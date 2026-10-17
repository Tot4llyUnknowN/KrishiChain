package com.kuet.krishichain.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.kuet.krishichain.algo.Step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Custom view. Draws the road network from lat/lon and REPLAYS a list of algorithm Steps.
 * It knows nothing about Room or about how the steps were produced.
 */
public class GraphView extends View {

    public interface Listener {
        void onNodeTapped(int index);

        void onProgress(int applied, int total);

        void onPlayStateChanged(boolean playing);
    }

    private static final int E_NORMAL = 0;
    private static final int E_EXAMINED = 1;
    private static final int E_TREE = 2;
    private static final int E_ADD = 3;
    private static final int E_SKIP = 4;
    private static final int E_CYCLE = 5;
    private static final int E_FLOW = 6;

    private static final int N_UNSEEN = 0;
    private static final int N_FRONTIER = 1;
    private static final int N_DONE = 2;

    // ----- network (set once) -----
    private int n = 0;
    private String[] names = new String[0];
    private String[] types = new String[0];
    private double[] gx = new double[0];
    private double[] gy = new double[0];
    private double minX, maxX, minY, maxY;
    private float[] px = new float[0];
    private float[] py = new float[0];
    private int[][] pairs = new int[0][];
    private final Map<Long, Integer> pairIndex = new HashMap<>();

    // ----- animation state -----
    private int[] edgeState = new int[0];
    private int[] nodeState = new int[0];
    private double[] dist = new double[0];
    private int[] parent = new int[0];
    private int examined = -1;
    private int lastNode = -1;
    private boolean showCycle = false;
    private final List<Integer> pathNodes = new ArrayList<>();
    private List<Integer> cycle = new ArrayList<>();

    private final List<int[]> cutPairs = new ArrayList<>();

    /** Roads of the minimum cut, drawn thick red when the replay has finished. */
    public void setCut(List<int[]> pairs) {
        cutPairs.clear();
        if (pairs != null) {
            cutPairs.addAll(pairs);
        }
        invalidate();
    }

    // ----- playback -----
    private List<Step> steps = new ArrayList<>();
    private int stepIndex = 0;
    private boolean showDist = false;
    private int startNode = -1;
    private boolean playing = false;
    private int delayMs = 180;
    private int source = -1;
    private int dest = -1;
    private Listener listener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!playing) {
                return;
            }
            stepOnce();
            if (stepIndex >= steps.size()) {
                playing = false;
                notifyPlayState();
            } else {
                handler.postDelayed(this, delayMs);
            }
        }
    };

    // ----- paints -----
    private float dp;
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint distPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GraphView(Context context) {
        super(context);
        init();
    }

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        dp = getResources().getDisplayMetrics().density;
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        namePaint.setTextAlign(Paint.Align.CENTER);
        namePaint.setTextSize(9 * dp);
        namePaint.setColor(0xFF424242);
        distPaint.setTextAlign(Paint.Align.CENTER);
        distPaint.setTextSize(10 * dp);
        distPaint.setFakeBoldText(true);
        distPaint.setColor(0xFF0D47A1);
    }

    // =====================================================================
    // Public API
    // =====================================================================

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** pairs[k] = {u, v}: one undirected road between node indexes u and v. */
    public void setNetwork(String[] names, String[] types, double[] lat, double[] lon,
                           int[][] pairs) {
        pause();
        this.n = names.length;
        this.names = names;
        this.types = types;
        this.pairs = pairs;
        gx = new double[n];
        gy = new double[n];
        px = new float[n];
        py = new float[n];
        double midLat = 0;
        for (double v : lat) {
            midLat += v;
        }
        midLat /= Math.max(1, n);
        double cos = Math.cos(Math.toRadians(midLat)); // keeps Bangladesh from looking stretched
        minX = Double.MAX_VALUE;
        minY = Double.MAX_VALUE;
        maxX = -Double.MAX_VALUE;
        maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            gx[i] = lon[i] * cos;
            gy[i] = -lat[i]; // north is up
            minX = Math.min(minX, gx[i]);
            maxX = Math.max(maxX, gx[i]);
            minY = Math.min(minY, gy[i]);
            maxY = Math.max(maxY, gy[i]);
        }
        pairIndex.clear();
        for (int k = 0; k < pairs.length; k++) {
            pairIndex.put(pairKey(pairs[k][0], pairs[k][1]), k);
        }
        steps = new ArrayList<>();
        resetState();
        invalidate();
    }

    public void setMarkers(int source, int dest) {
        this.source = source;
        this.dest = dest;
        invalidate();
    }

    /**
     * Loads a trace. showDist: write distance labels on nodes (shortest-path algorithms).
     * startNode: marked as done at the beginning (Prim's start, Dijkstra's source), or -1.
     * cycle: nodes of a negative cycle to highlight when the NEGATIVE_CYCLE step arrives.
     */
    public void loadSteps(List<Step> steps, boolean showDist, int startNode, List<Integer> cycle) {
        cutPairs.clear();
        pause();
        this.steps = steps;
        this.showDist = showDist;
        this.startNode = startNode;
        this.cycle = cycle == null ? new ArrayList<Integer>() : cycle;
        resetState();
        invalidate();
        notifyProgress();
    }

    public void setDelayMs(int ms) {
        this.delayMs = Math.max(5, ms);
    }

    public boolean isPlaying() {
        return playing;
    }

    public void togglePlay() {
        if (playing) {
            pause();
        } else {
            play();
        }
    }

    public void play() {
        if (steps.isEmpty()) {
            return;
        }
        if (stepIndex >= steps.size()) {
            resetState();
            invalidate();
        }
        playing = true;
        notifyPlayState();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, delayMs);
    }

    public void pause() {
        handler.removeCallbacks(tick);
        if (playing) {
            playing = false;
            notifyPlayState();
        }
    }

    /** Applies exactly one step. Returns false when the trace is finished. */
    public boolean stepOnce() {
        if (stepIndex >= steps.size()) {
            return false;
        }
        apply(steps.get(stepIndex++));
        invalidate();
        notifyProgress();
        return true;
    }

    /** Skips the animation and shows the final picture. */
    public void jumpToEnd() {
        pause();
        resetState();
        while (stepIndex < steps.size()) {
            apply(steps.get(stepIndex++));
        }
        invalidate();
        notifyProgress();
    }

    // =====================================================================
    // Replaying one Step
    // =====================================================================

    private void apply(Step s) {
        switch (s.type) {
            case VISIT:
                nodeState[s.from] = N_DONE;
                dist[s.from] = s.value;
                lastNode = s.from;
                break;
            case RELAX:
                clearExamined();
                int k = pairOf(s.from, s.to);
                if (k >= 0 && edgeState[k] == E_NORMAL) {
                    edgeState[k] = E_EXAMINED;
                    examined = k;
                }
                break;
            case UPDATE:
                dist[s.to] = s.value;
                if (nodeState[s.to] == N_UNSEEN) {
                    nodeState[s.to] = N_FRONTIER;
                }
                setParent(s.from, s.to);
                lastNode = s.to;
                break;
            case NEGATIVE_CYCLE:
                clearExamined();
                showCycle = true;
                for (int i = 0; i < cycle.size(); i++) {
                    int a = cycle.get(i);
                    int b = cycle.get((i + 1) % cycle.size());
                    int e = pairOf(a, b);
                    if (e >= 0) {
                        edgeState[e] = E_CYCLE;
                    }
                }
                break;
            case AUGMENT: {
                if (s.from < n) {
                    nodeState[s.from] = N_DONE;
                }
                if (s.to < n) {
                    nodeState[s.to] = N_DONE;
                }
                int e = pairOf(s.from, s.to);
                if (e >= 0) {
                    edgeState[e] = E_FLOW;
                }
                break;
            }
            case MST_ADD: {
                nodeState[s.from] = N_DONE;
                nodeState[s.to] = N_DONE;
                int e = pairOf(s.from, s.to);
                if (e >= 0) {
                    edgeState[e] = E_ADD;
                }
                break;
            }
            case MST_SKIP: {
                int e = pairOf(s.from, s.to);
                if (e >= 0 && edgeState[e] != E_ADD) {
                    edgeState[e] = E_SKIP;
                }
                break;
            }
            case FINISH:
                clearExamined();
                lastNode = -1;
                computePath();
                break;
            default:
                break;
        }
    }

    private void setParent(int u, int v) {
        int old = parent[v];
        if (old >= 0 && old != u) {
            int ko = pairOf(old, v);
            if (ko >= 0 && edgeState[ko] == E_TREE) {
                edgeState[ko] = E_NORMAL;
            }
        }
        parent[v] = u;
        int k = pairOf(u, v);
        if (k >= 0 && (edgeState[k] == E_NORMAL || edgeState[k] == E_EXAMINED)) {
            edgeState[k] = E_TREE;
            if (examined == k) {
                examined = -1;
            }
        }
    }

    private void clearExamined() {
        if (examined >= 0 && edgeState[examined] == E_EXAMINED) {
            edgeState[examined] = E_NORMAL;
        }
        examined = -1;
    }

    private void computePath() {
        pathNodes.clear();
        if (!showDist || showCycle || dest < 0 || dest >= n
                || dist[dest] == Double.POSITIVE_INFINITY) {
            return;
        }
        int v = dest;
        for (int guard = 0; v >= 0 && guard <= n; guard++) {
            pathNodes.add(v);
            v = parent[v];
        }
    }

    private void resetState() {
        edgeState = new int[pairs.length];
        nodeState = new int[n];
        dist = new double[n];
        parent = new int[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(parent, -1);
        examined = -1;
        lastNode = -1;
        showCycle = false;
        pathNodes.clear();
        stepIndex = 0;
        if (startNode >= 0 && startNode < n) {
            if (showDist) {
                dist[startNode] = 0;
                nodeState[startNode] = N_FRONTIER;
            } else {
                nodeState[startNode] = N_DONE;
            }
        }
    }

    private int pairOf(int u, int v) {
        Integer k = pairIndex.get(pairKey(u, v));
        return k == null ? -1 : k;
    }

    private static long pairKey(int a, int b) {
        return Math.min(a, b) * 1000L + Math.max(a, b);
    }

    private void notifyProgress() {
        if (listener != null) {
            listener.onProgress(stepIndex, steps.size());
        }
    }

    private void notifyPlayState() {
        if (listener != null) {
            listener.onPlayStateChanged(playing);
        }
    }

    // =====================================================================
    // Drawing
    // =====================================================================

    private void layoutNodes() {
        float pad = 28 * dp;
        float w = getWidth() - 2 * pad;
        float h = getHeight() - 2 * pad;
        double spanX = Math.max(1e-9, maxX - minX);
        double spanY = Math.max(1e-9, maxY - minY);
        double s = Math.min(w / spanX, h / spanY);
        double offX = pad + (w - spanX * s) / 2;
        double offY = pad + (h - spanY * s) / 2;
        for (int i = 0; i < n; i++) {
            px[i] = (float) (offX + (gx[i] - minX) * s);
            py[i] = (float) (offY + (gy[i] - minY) * s);
        }
    }

    private void styleEdge(int state) {
        switch (state) {
            case E_EXAMINED:
                linePaint.setColor(0xFFFF6F00);
                linePaint.setStrokeWidth(3 * dp);
                break;
            case E_TREE:
                linePaint.setColor(0xFF1976D2);
                linePaint.setStrokeWidth(2.5f * dp);
                break;
            case E_ADD:
                linePaint.setColor(0xFF2E7D32);
                linePaint.setStrokeWidth(4 * dp);
                break;
            case E_SKIP:
                linePaint.setColor(0xFFEF9A9A);
                linePaint.setStrokeWidth(1.5f * dp);
                break;
            case E_CYCLE:
                linePaint.setColor(0xFFD32F2F);
                linePaint.setStrokeWidth(4 * dp);
                break;
            case E_FLOW:
                linePaint.setColor(0xFF00ACC1);
                linePaint.setStrokeWidth(4 * dp);
                break;
            default:
                linePaint.setColor(0xFFBDBDBD);
                linePaint.setStrokeWidth(1 * dp);
                break;
        }
    }

    private static int typeColor(String type) {
        if ("village".equals(type)) {
            return 0xFF7CB342;
        }
        if ("hub".equals(type)) {
            return 0xFFFFA000;
        }
        if ("market".equals(type)) {
            return 0xFF8E24AA;
        }
        return 0xFF757575;
    }

    private void ring(Canvas c, int i, float radius, int color, float width) {
        ringPaint.setColor(color);
        ringPaint.setStrokeWidth(width);
        c.drawCircle(px[i], py[i], radius, ringPaint);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(0xFFFAFAFA);
        if (n == 0) {
            return;
        }
        layoutNodes();

        // Plain roads first, highlighted roads on top.
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < pairs.length; k++) {
                int st = edgeState[k];
                if ((pass == 0) != (st == E_NORMAL)) {
                    continue;
                }
                styleEdge(st);
                int a = pairs[k][0];
                int b = pairs[k][1];
                c.drawLine(px[a], py[a], px[b], py[b], linePaint);
            }
        }

        // Final shortest path.
        if (pathNodes.size() > 1) {
            linePaint.setColor(0xFF00C853);
            linePaint.setStrokeWidth(6 * dp);
            for (int i = 0; i + 1 < pathNodes.size(); i++) {
                int a = pathNodes.get(i);
                int b = pathNodes.get(i + 1);
                c.drawLine(px[a], py[a], px[b], py[b], linePaint);
            }
        }
        // Minimum cut (only after the replay finished).
        if (!cutPairs.isEmpty() && !steps.isEmpty() && stepIndex >= steps.size()) {
            linePaint.setColor(0xFFD50000);
            linePaint.setStrokeWidth(7 * dp);
            for (int[] p : cutPairs) {
                c.drawLine(px[p[0]], py[p[0]], px[p[1]], py[p[1]], linePaint);
            }
        }
        // Nodes.
        for (int i = 0; i < n; i++) {
            float r = (i == lastNode ? 9 : 6) * dp;
            fillPaint.setColor(typeColor(types[i]));
            c.drawCircle(px[i], py[i], r, fillPaint);
            if (nodeState[i] == N_DONE) {
                ring(c, i, r, 0xFF1565C0, 2.5f * dp);
            } else if (nodeState[i] == N_FRONTIER) {
                ring(c, i, r, 0xFFFF6F00, 2.5f * dp);
            }
            if (i == source) {
                ring(c, i, r + 4 * dp, 0xFF000000, 2 * dp);
            }
            if (i == dest) {
                ring(c, i, r + 4 * dp, 0xFF00C853, 2 * dp);
            }
            c.drawText(names[i], px[i], py[i] + r + 11 * dp, namePaint);
            if (showDist && dist[i] != Double.POSITIVE_INFINITY) {
                c.drawText(String.format(Locale.US, "%.0f", dist[i]),
                        px[i], py[i] - r - 4 * dp, distPaint);
            }
        }
    }

    // =====================================================================
    // Touch: tap a node to choose it
    // =====================================================================

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int best = -1;
            float bestDist = 36 * dp;
            for (int i = 0; i < n; i++) {
                float d = (float) Math.hypot(px[i] - e.getX(), py[i] - e.getY());
                if (d < bestDist) {
                    bestDist = d;
                    best = i;
                }
            }
            if (best >= 0 && listener != null) {
                listener.onNodeTapped(best);
            }
            performClick();
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick); // never leave a timer running after the screen is gone
        super.onDetachedFromWindow();
    }
}