package com.kuet.krishichain.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRouteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final int pad = (int) (16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });

        binding.recyclerSteps.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerSteps.setAdapter(adapter);

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

        binding.radioMode.setOnCheckedChangeListener((group, checkedId) -> {
            RouteResult r = checkedId == R.id.radioFastest ? fastest : cheapest;
            if (r != null) {
                showRoute(r);
            }
        });
        binding.buttonShare.setOnClickListener(v -> share());
        binding.buttonMap.setOnClickListener(v -> openMap());
        binding.buttonCall.setOnClickListener(v -> openDialer());

        calculate(cropKey, originId, destId, price);
    }

    private void calculate(final String cropKey, final int originId, final int destId,
                           final double price) {
        final Context app = getApplicationContext();
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
                final RouteResult cheap = RouteCalculator.compute(
                        snap, originId, destId, price, qtyKg, rate, false);
                final RouteResult fast = RouteCalculator.compute(
                        snap, originId, destId, price, qtyKg, rate, true);
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
                    binding.textRouteSummary.setText(getString(R.string.route_summary,
                            cropName, qtyKg, originName, dest.name));
                    showRoute(binding.radioFastest.isChecked() ? fastest : cheapest);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    binding.progress.setVisibility(View.GONE);
                    binding.textCompare.setText(getString(R.string.route_error, e.getMessage()));
                });
            }
        });
    }

    private void showRoute(RouteResult r) {
        current = r;
        adapter.setItems(r.legs);
        binding.textProfit.setText(getString(R.string.route_profit, r.option.netProfit));
        binding.textTotals.setText(getString(R.string.route_totals, r.km,
                Fmt.duration(this, r.minutes), r.option.transportCost, r.option.toll,
                r.option.spoilageFraction * 100.0));

        if (r.legs.isEmpty()) {
            binding.textCompare.setText(R.string.route_here);
        } else if (cheapest.placeNames.equals(fastest.placeNames)) {
            binding.textCompare.setText(R.string.route_same);
        } else {
            binding.textCompare.setText(getString(R.string.route_compare,
                    cheapest.option.netProfit, Fmt.duration(this, cheapest.minutes),
                    fastest.option.netProfit, Fmt.duration(this, fastest.minutes)));
        }
    }

    // ---------------- Implicit Intents ----------------
    // An implicit Intent names an ACTION, not a class. Android finds an app that can handle it.

    private void share() {
        if (current == null) {
            return;
        }
        String text = getString(R.string.share_text, qtyKg, cropName, destNode.name, originName,
                TextUtils.join(" → ", current.placeNames), current.km,
                Fmt.duration(this, current.minutes), current.option.netProfit);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        start(Intent.createChooser(send, getString(R.string.share_chooser)));
    }

    private void openMap() {
        if (destNode == null) {
            return;
        }
        String label = Uri.encode(destNode.name);
        Uri uri = Uri.parse("geo:" + destNode.lat + "," + destNode.lon
                + "?q=" + destNode.lat + "," + destNode.lon + "(" + label + ")");
        start(new Intent(Intent.ACTION_VIEW, uri));
    }

    /** Opens the dialer with no number: the app has no phone numbers yet. */
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