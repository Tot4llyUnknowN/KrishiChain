package com.kuet.krishichain.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebSettings;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.kuet.krishichain.R;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.CropInfo;
import com.kuet.krishichain.data.CropXmlParser;
import com.kuet.krishichain.data.NetworkDao;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.data.NodeEntity;
import com.kuet.krishichain.databinding.ActivityRouteBinding;
import com.kuet.krishichain.domain.NetworkSnapshot;
import com.kuet.krishichain.domain.RouteCalculator;
import com.kuet.krishichain.domain.RouteResult;

import java.util.Locale;

public class RouteActivity extends AppCompatActivity {
    public static final String EXTRA_DEST_ID = "dest_id";
    public static final String EXTRA_PRICE = "price_per_kg";

    private ActivityRouteBinding binding;
    private final StepAdapter adapter = new StepAdapter();

    private RouteResult cheapest;
    private RouteResult fastest;
    private RouteResult current;
    private NodeEntity destNode;

    private String cropName;
    private double qtyKg;
    private String originName;
    private boolean isFastestMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRouteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final int pad = (int) (16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        binding.buttonBack.setOnClickListener(v -> finish());
        TopBarHelper.setup(this, binding.getRoot());
        binding.recyclerSteps.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerSteps.setAdapter(adapter);

        // Configure OpenStreetMap WebView
        WebSettings webSettings = binding.mapWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 KrishiChain/1.0");

        final String cropKey = getIntent().getStringExtra(RankingActivity.EXTRA_CROP_KEY);
        cropName = getIntent().getStringExtra(RankingActivity.EXTRA_CROP_NAME);
        qtyKg = getIntent().getDoubleExtra(RankingActivity.EXTRA_QTY_KG, 0);
        final int originId = getIntent().getIntExtra(RankingActivity.EXTRA_ORIGIN_ID, -1);
        originName = getIntent().getStringExtra(RankingActivity.EXTRA_ORIGIN_NAME);
        final int destId = getIntent().getIntExtra(EXTRA_DEST_ID, -1);
        final double price = getIntent().getDoubleExtra(EXTRA_PRICE, 0);

        if (cropKey == null || qtyKg <= 0 || originId < 0 || destId < 0) {
            finish();
            return;
        }

        binding.tabCheapest.setOnClickListener(v -> setRouteMode(false));
        binding.tabFastest.setOnClickListener(v -> setRouteMode(true));

        binding.buttonStartNavigation.setOnClickListener(v -> startNavigation());
        binding.buttonShare.setOnClickListener(v -> share());
        binding.buttonMap.setOnClickListener(v -> openMap());
        binding.buttonCall.setOnClickListener(v -> openDialer());

        calculate(cropKey, originId, destId, price);
    }

    private void setRouteMode(boolean fast) {
        isFastestMode = fast;
        if (fast) {
            binding.tabFastest.setBackgroundResource(R.drawable.bg_tab_on);
            binding.tabFastest.setTextColor(Color.WHITE);
            binding.tabCheapest.setBackgroundResource(R.drawable.bg_tab_off);
            binding.tabCheapest.setTextColor(Color.parseColor("#143E23"));
        } else {
            binding.tabCheapest.setBackgroundResource(R.drawable.bg_tab_on);
            binding.tabCheapest.setTextColor(Color.WHITE);
            binding.tabFastest.setBackgroundResource(R.drawable.bg_tab_off);
            binding.tabFastest.setTextColor(Color.parseColor("#143E23"));
        }
        RouteResult r = fast ? fastest : cheapest;
        if (r != null) {
            showRoute(r);
        }
    }

    private void calculate(final String cropKey, final int originId, final int destId,
                           final double price) {
        final Context app = getApplicationContext();
        binding.progress.setVisibility(View.VISIBLE);
        AppDatabase.IO.execute(() -> {
            try {
                NetworkLoader.loadIfNeeded(app);
                NetworkDao dao = AppDatabase.get(app).networkDao();
                NetworkSnapshot snap = NetworkSnapshot.load(dao);
                double rate = 0;
                for (CropInfo info : CropXmlParser.parse(app)) {
                    if (info.key.equals(cropKey)) {
                        rate = info.spoilagePctPerHour;
                    }
                }
                double prodCost = com.kuet.krishichain.data.UserPreferences.getProductionCost(app, 20.0);
                final RouteResult cheap = RouteCalculator.compute(
                        snap, originId, destId, price, qtyKg, prodCost, rate, false);
                final RouteResult fast = RouteCalculator.compute(
                        snap, originId, destId, price, qtyKg, prodCost, rate, true);
                final NodeEntity dest = snap.nodeById(destId);
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    cheapest = cheap;
                    fastest = fast;
                    destNode = dest;
                    binding.progress.setVisibility(View.GONE);
                    binding.recyclerSteps.setVisibility(View.VISIBLE);
                    binding.buttonShare.setEnabled(true);
                    binding.buttonMap.setEnabled(true);
                    showRoute(isFastestMode ? fastest : cheapest);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    binding.progress.setVisibility(View.GONE);
                    binding.textRouteMeta.setText(getString(R.string.route_error, e.getMessage()));
                });
            }
        });
    }

    private void showRoute(RouteResult r) {
        current = r;
        adapter.setItems(r.legs);
        int districtCount = r.placeNames != null ? r.placeNames.size() : (r.legs.size() + 1);
        binding.textWaypointsSummary.setText(String.format(Locale.US, "%d Districts Visited", districtCount));
        binding.linearRouteView.setRoute(r.placeNames, r.legs);

        String destName = destNode != null ? destNode.name : "Market";
        double price = getIntent().getDoubleExtra(EXTRA_PRICE, 0);

        binding.textRouteTitle.setText(originName + " → " + destName);
        double demandTons = destNode != null ? com.kuet.krishichain.domain.DistrictDemand.getDemandTons(destNode.id) : 50.0;
        binding.textRouteMeta.setText(String.format(Locale.US, "Distance %.0f km · Time %s · Demand %.0ft",
                r.km, Fmt.duration(this, r.minutes), demandTons));
        binding.textProfit.setText(String.format(Locale.US, "Net profit ৳ %,.0f", r.option.netProfit));

        // Render OpenStreetMap with exact GPS path nodes
        String mapHtml = OsmMapHelper.buildHtml(r.nodes, originName, destName, price);
        binding.mapWebView.loadDataWithBaseURL("https://openstreetmap.org", mapHtml, "text/html", "UTF-8", null);
    }

    private void startNavigation() {
        if (destNode == null) return;
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + destNode.lat + "," + destNode.lon + "&mode=d");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(mapIntent);
        } catch (Exception e) {
            openMap();
        }
    }

    private void share() {
        if (current == null || destNode == null) return;
        String text = getString(R.string.share_text, qtyKg, cropName, destNode.name, originName,
                TextUtils.join(" → ", current.placeNames), current.km,
                Fmt.duration(this, current.minutes), current.option.netProfit);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        start(Intent.createChooser(send, getString(R.string.share_chooser)));
    }

    private void openMap() {
        if (destNode == null) return;
        String label = Uri.encode(destNode.name);
        Uri uri = Uri.parse("geo:" + destNode.lat + "," + destNode.lon
                + "?q=" + destNode.lat + "," + destNode.lon + "(" + label + ")");
        start(new Intent(Intent.ACTION_VIEW, uri));
    }

    private void openDialer() {
        start(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")));
    }

    private void start(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_no_app, Toast.LENGTH_SHORT).show();
        }
    }
}
