package com.kuet.krishichain.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

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
import com.kuet.krishichain.data.PriceEntity;
import com.kuet.krishichain.databinding.ActivityRankingBinding;
import com.kuet.krishichain.domain.MarketRanker;
import com.kuet.krishichain.domain.NetworkSnapshot;
import com.kuet.krishichain.domain.RankedMarket;
import com.kuet.krishichain.domain.RankingResult;

import java.util.List;

public class RankingActivity extends AppCompatActivity {
    public static final String EXTRA_CROP_KEY = "crop_key";
    public static final String EXTRA_CROP_NAME = "crop_name";
    public static final String EXTRA_QTY_KG = "qty_kg";
    public static final String EXTRA_ORIGIN_ID = "origin_id";
    public static final String EXTRA_ORIGIN_NAME = "origin_name";

    private ActivityRankingBinding binding;
    private final MarketAdapter adapter = new MarketAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRankingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final int pad = (int) (16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });

        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        // Data arrives from Home through the explicit Intent.
        final String cropKey = getIntent().getStringExtra(EXTRA_CROP_KEY);
        final String cropName = getIntent().getStringExtra(EXTRA_CROP_NAME);
        final double qtyKg = getIntent().getDoubleExtra(EXTRA_QTY_KG, 0);
        final int originId = getIntent().getIntExtra(EXTRA_ORIGIN_ID, -1);
        final String originName = getIntent().getStringExtra(EXTRA_ORIGIN_NAME);
        if (cropKey == null || qtyKg <= 0 || originId < 0) {
            finish();
            return;
        }

        // Tapping a market opens the Route screen (explicit Intent with extras).
        adapter.setListener(market -> {
            Intent intent = new Intent(this, RouteActivity.class);
            intent.putExtra(EXTRA_CROP_KEY, cropKey);
            intent.putExtra(EXTRA_CROP_NAME, cropName);
            intent.putExtra(EXTRA_QTY_KG, qtyKg);
            intent.putExtra(EXTRA_ORIGIN_ID, originId);
            intent.putExtra(EXTRA_ORIGIN_NAME, originName);
            intent.putExtra(RouteActivity.EXTRA_DEST_ID, market.option.nodeId);
            intent.putExtra(RouteActivity.EXTRA_PRICE, market.option.pricePerKg);
            startActivity(intent);
        });

        binding.textSummary.setText(
                getString(R.string.ranking_summary, cropName, qtyKg, originName));

        calculate(cropKey, qtyKg, originId);
    }

    private void calculate(final String cropKey, final double qtyKg, final int originId) {
        final Context app = getApplicationContext();
        AppDatabase.IO.execute(() -> {
            try {
                NetworkLoader.loadIfNeeded(app); // cheap when nothing changed
                NetworkDao dao = AppDatabase.get(app).networkDao();
                NetworkSnapshot snapshot = NetworkSnapshot.load(dao);
                List<PriceEntity> prices = dao.getPricesForCrop(cropKey);
                double rate = 0;
                for (CropInfo info : CropXmlParser.parse(app)) {
                    if (info.key.equals(cropKey)) {
                        rate = info.spoilagePctPerHour;
                    }
                }
                final RankingResult result =
                        MarketRanker.rank(snapshot, prices, originId, qtyKg, rate);
                final int dummy = dao.dummyPriceCount();
                runOnUiThread(() -> show(result, dummy));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    binding.progress.setVisibility(View.GONE);
                    binding.textInsight.setVisibility(View.VISIBLE);
                    binding.textInsight.setText(getString(R.string.ranking_error, e.getMessage()));
                });
            }
        });
    }

    private void show(RankingResult result, int dummyCount) {
        if (isDestroyed()) {
            return;
        }
        binding.progress.setVisibility(View.GONE);
        binding.textDemoWarning.setVisibility(dummyCount > 0 ? View.VISIBLE : View.GONE);

        RankedMarket best = result.best();
        if (best == null) {
            binding.textInsight.setVisibility(View.VISIBLE);
            binding.textInsight.setText(R.string.ranking_empty);
            return;
        }
        RankedMarket nearest = result.nearest;
        if (best.option.nodeId == nearest.option.nodeId) {
            binding.textInsight.setText(getString(R.string.ranking_insight_same,
                    best.option.marketName));
        } else {
            binding.textInsight.setText(getString(R.string.ranking_insight_better,
                    best.option.marketName,
                    best.option.netProfit - nearest.option.netProfit,
                    nearest.option.marketName));
        }
        binding.textInsight.setVisibility(View.VISIBLE);

        adapter.setItems(result.ranked, nearest.option.nodeId);
        binding.recycler.setVisibility(View.VISIBLE);
    }
}