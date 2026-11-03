package com.kuet.krishichain.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.kuet.krishichain.domain.RouteLeg;

import java.util.ArrayList;
import java.util.List;

/**
 * Route visualizer matching the exact HTML mockup SVG design (4 · Route detail):
 * - Soft pastel map background (#d8e9cf)
 * - Gentle river wave (#a8cfe8)
 * - Thick solid green route (#1b7a2f)
 * - Dashed amber alternative path (#f59e0b)
 * - Color-coded nodes (village #7cb342, hub #ffa000, market #8e24aa)
 */
public class LinearRouteView extends View {

    private final List<String> places = new ArrayList<>();
    private final List<RouteLeg> legs = new ArrayList<>();

    private float dp;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint riverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mainRoutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint altRoutePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    public LinearRouteView(Context context) {
        super(context);
        init();
    }

    public LinearRouteView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LinearRouteView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        dp = getResources().getDisplayMetrics().density;

        bgPaint.setColor(0xFFD8E9CF); // #d8e9cf
        bgPaint.setStyle(Paint.Style.FILL);

        riverPaint.setColor(0xB3A8CFE8); // #a8cfe8
        riverPaint.setStyle(Paint.Style.STROKE);
        riverPaint.setStrokeWidth(9 * dp);
        riverPaint.setStrokeCap(Paint.Cap.ROUND);

        mainRoutePaint.setColor(0xFF1B7A2F); // #1b7a2f
        mainRoutePaint.setStyle(Paint.Style.STROKE);
        mainRoutePaint.setStrokeWidth(4.5f * dp);
        mainRoutePaint.setStrokeCap(Paint.Cap.ROUND);

        altRoutePaint.setColor(0xFFF59E0B); // #f59e0b
        altRoutePaint.setStyle(Paint.Style.STROKE);
        altRoutePaint.setStrokeWidth(2.5f * dp);
        altRoutePaint.setPathEffect(new DashPathEffect(new float[]{5 * dp, 4 * dp}, 0));

        nodePaint.setStyle(Paint.Style.FILL);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(9.5f * dp);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(0xFF17261A);
    }

    public void setRoute(List<String> placeNames, List<RouteLeg> routeLegs) {
        places.clear();
        if (placeNames != null) {
            places.addAll(placeNames);
        }
        legs.clear();
        if (routeLegs != null) {
            legs.addAll(routeLegs);
        }
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize((int) (270 * dp), widthMeasureSpec);
        int height = (int) (130 * dp);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        // Background with 10dp rounded corners
        bounds.set(0, 0, w, h);
        canvas.drawRoundRect(bounds, 10 * dp, 10 * dp, bgPaint);

        // River curve in the background
        Path river = new Path();
        river.moveTo(0, h * 0.73f);
        river.quadTo(w * 0.22f, h * 0.60f, w * 0.45f, h * 0.80f);
        river.quadTo(w * 0.75f, h * 0.95f, w, h * 0.67f);
        canvas.drawPath(river, riverPaint);

        if (places.isEmpty()) {
            return;
        }

        int n = places.size();
        float padX = 36 * dp;
        float startY = h * 0.80f;
        float endY = h * 0.22f;

        float[] px = new float[n];
        float[] py = new float[n];

        float stepX = (n > 1) ? (w - 2 * padX) / (n - 1) : 0;
        float stepY = (n > 1) ? (endY - startY) / (n - 1) : 0;

        for (int i = 0; i < n; i++) {
            px[i] = (n > 1) ? padX + i * stepX : w / 2;
            py[i] = (n > 1) ? startY + i * stepY : h / 2;
        }

        // Alternate dashed path if there are multiple intermediate hops
        if (n >= 3) {
            Path altPath = new Path();
            altPath.moveTo(px[0], py[0]);
            float midX = (px[0] + px[n - 1]) / 2;
            float midY = Math.min(py[0], py[n - 1]) + 20 * dp;
            altPath.quadTo(midX, midY, px[n - 1], py[n - 1]);
            canvas.drawPath(altPath, altRoutePaint);
        }

        // Main Route polyline
        Path mainPath = new Path();
        mainPath.moveTo(px[0], py[0]);
        for (int i = 1; i < n; i++) {
            mainPath.lineTo(px[i], py[i]);
        }
        canvas.drawPath(mainPath, mainRoutePaint);

        // Nodes & Labels
        for (int i = 0; i < n; i++) {
            float x = px[i];
            float y = py[i];

            boolean isOrigin = (i == 0);
            boolean isMarket = (i == n - 1);

            float r;
            if (isOrigin) {
                nodePaint.setColor(0xFF7CB342); // green village #7cb342
                r = 6.5f * dp;
            } else if (isMarket) {
                nodePaint.setColor(0xFF8E24AA); // purple market #8e24aa
                r = 7.5f * dp;
            } else {
                nodePaint.setColor(0xFFFFA000); // orange hub #ffa000
                r = 5.5f * dp;
            }

            canvas.drawCircle(x, y, r, nodePaint);

            // City labels
            String name = places.get(i);
            float labelY = isOrigin ? (y + 15 * dp) : (y - 9 * dp);
            canvas.drawText(name, x, labelY, textPaint);
        }
    }
}
