package com.kuet.krishichain.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;

import com.kuet.krishichain.R;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.CropInfo;
import com.kuet.krishichain.data.CropXmlParser;
import com.kuet.krishichain.data.EdgeEntity;
import com.kuet.krishichain.data.NetworkDao;
import com.kuet.krishichain.data.NetworkLoader;
import com.kuet.krishichain.data.NodeEntity;
import com.kuet.krishichain.data.PriceEntity;
import com.kuet.krishichain.data.UserPreferences;
import com.kuet.krishichain.databinding.ActivityMarketMapBinding;
import com.kuet.krishichain.domain.NetworkSnapshot;
import com.kuet.krishichain.domain.RouteCalculator;
import com.kuet.krishichain.domain.RouteResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MarketMapActivity extends AppCompatActivity {
    private static final String[] CROP_KEYS = {"potato", "tomato", "onion"};

    private ActivityMarketMapBinding binding;
    private List<NodeEntity> allNodes = new ArrayList<>();
    private List<EdgeEntity> allEdges = new ArrayList<>();
    private NetworkSnapshot snapshot;
    private int selectedOriginId = 1; // Default Dhaka (id 1)
    private int selectedDestId = -1;  // Unselected by default
    private String currentCropKey = "potato";
    private String currentCropName = "Potato";
    private double currentPrice = 38.0;
    private RouteResult currentRouteResult;
    private boolean isMapLoaded = false;
    private boolean isSyncingSpinners = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMarketMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonBack.setOnClickListener(v -> finish());

        // Setup bottom navigation bar (Tab.MAP)
        BottomNavHelper.setup(this, BottomNavHelper.Tab.MAP, binding.getRoot());

        // Load shared preferences for origin and crop
        selectedOriginId = UserPreferences.getOriginId(this, 1);
        currentCropKey = UserPreferences.getCropKey(this, "potato");

        // Configure WebView
        WebSettings webSettings = binding.mapWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 KrishiChain/1.0");

        binding.mapWebView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onHubSelected(int id, String name) {
                runOnUiThread(() -> selectDestinationHub(id, name));
            }
        }, "Android");

        // Crop Spinner
        ArrayAdapter<CharSequence> cropAdapter = ArrayAdapter.createFromResource(
                this, R.array.crop_names, R.layout.item_spinner_selected);
        cropAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.spinnerCrop.setAdapter(cropAdapter);

        // Set initial crop selection to match preferences
        for (int i = 0; i < CROP_KEYS.length; i++) {
            if (CROP_KEYS[i].equalsIgnoreCase(currentCropKey)) {
                binding.spinnerCrop.setSelection(i);
                break;
            }
        }

        binding.spinnerCrop.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isSyncingSpinners) return;
                currentCropKey = (position >= 0 && position < CROP_KEYS.length) ? CROP_KEYS[position] : "potato";
                currentCropName = binding.spinnerCrop.getSelectedItem() != null
                        ? binding.spinnerCrop.getSelectedItem().toString() : "Potato";
                UserPreferences.setCropKey(MarketMapActivity.this, currentCropKey);

                if (selectedDestId > 0 && selectedDestId != selectedOriginId) {
                    calculateAndRenderRoute(selectedOriginId, selectedDestId);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        binding.buttonViewRouteDetails.setOnClickListener(v -> {
            if (currentRouteResult == null || selectedDestId <= 0) return;
            Intent intent = new Intent(this, RouteActivity.class);
            intent.putExtra(RankingActivity.EXTRA_CROP_KEY, currentCropKey);
            intent.putExtra(RankingActivity.EXTRA_CROP_NAME, currentCropName);
            intent.putExtra(RankingActivity.EXTRA_QTY_KG, UserPreferences.getQuantity(this, 1000.0));
            intent.putExtra(RankingActivity.EXTRA_ORIGIN_ID, selectedOriginId);
            intent.putExtra(RankingActivity.EXTRA_ORIGIN_NAME, getOriginName());
            intent.putExtra(RouteActivity.EXTRA_DEST_ID, selectedDestId);
            intent.putExtra(RouteActivity.EXTRA_PRICE, currentPrice);
            startActivity(intent);
        });

        resetCardToDefaultPrompt();
        loadNetwork();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavHelper.setup(this, BottomNavHelper.Tab.MAP, binding.getRoot());
        syncWithSharedPreferences();
    }

    private void syncWithSharedPreferences() {
        int prefOriginId = UserPreferences.getOriginId(this, selectedOriginId);
        String prefCropKey = UserPreferences.getCropKey(this, currentCropKey);

        boolean originChanged = (prefOriginId != selectedOriginId);
        boolean cropChanged = (!prefCropKey.equalsIgnoreCase(currentCropKey));

        if (originChanged || cropChanged) {
            isSyncingSpinners = true;
            if (originChanged) {
                selectedOriginId = prefOriginId;
                if (binding.spinnerOrigin.getAdapter() != null) {
                    for (int i = 0; i < allNodes.size(); i++) {
                        if (allNodes.get(i).id == selectedOriginId) {
                            binding.spinnerOrigin.setSelection(i);
                            break;
                        }
                    }
                }
            }
            if (cropChanged) {
                currentCropKey = prefCropKey;
                for (int i = 0; i < CROP_KEYS.length; i++) {
                    if (CROP_KEYS[i].equalsIgnoreCase(currentCropKey)) {
                        binding.spinnerCrop.setSelection(i);
                        currentCropName = binding.spinnerCrop.getItemAtPosition(i).toString();
                        break;
                    }
                }
            }
            isSyncingSpinners = false;

            if (selectedDestId > 0 && selectedDestId != selectedOriginId) {
                calculateAndRenderRoute(selectedOriginId, selectedDestId);
            } else {
                clearRouteOnMap();
            }
        }
    }

    private String getOriginName() {
        for (NodeEntity n : allNodes) {
            if (n.id == selectedOriginId) return n.name;
        }
        return "Dhaka";
    }

    private void resetCardToDefaultPrompt() {
        binding.textSelectedHubTitle.setText("Select a Market Hub");
        binding.textSelectedHubPrice.setText("Tap any pin");
        binding.textOptimalRouteNodes.setText("Tap any district on the map or select from the dropdown to calculate the optimal highway route & net profit.");
        binding.layoutMetaRow.setVisibility(View.GONE);
        binding.buttonViewRouteDetails.setVisibility(View.GONE);
    }

    private void loadNetwork() {
        final Context app = getApplicationContext();
        binding.mapProgress.setVisibility(View.VISIBLE);
        AppDatabase.IO.execute(() -> {
            try {
                NetworkLoader.loadIfNeeded(app);
            } catch (Exception ignored) {}
            NetworkDao dao = AppDatabase.get(app).networkDao();
            allNodes = dao.getAllNodes();
            allEdges = dao.getAllEdges();
            snapshot = NetworkSnapshot.load(dao);

            runOnUiThread(() -> {
                if (isDestroyed()) return;
                binding.mapProgress.setVisibility(View.GONE);

                isSyncingSpinners = true;

                // 1. Setup Origin Spinner with all 64 districts
                ArrayAdapter<NodeEntity> originAdapter = new ArrayAdapter<>(
                        this, R.layout.item_spinner_selected, allNodes);
                originAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
                binding.spinnerOrigin.setAdapter(originAdapter);

                for (int i = 0; i < allNodes.size(); i++) {
                    if (allNodes.get(i).id == selectedOriginId) {
                        binding.spinnerOrigin.setSelection(i);
                        break;
                    }
                }

                // 2. Setup Destination Spinner with prompt at position 0
                List<String> destOptions = new ArrayList<>();
                destOptions.add("-- Select Destination City --");
                for (NodeEntity n : allNodes) {
                    destOptions.add(n.name);
                }
                ArrayAdapter<String> destAdapter = new ArrayAdapter<>(
                        this, R.layout.item_spinner_selected, destOptions);
                destAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
                binding.spinnerDest.setAdapter(destAdapter);
                binding.spinnerDest.setSelection(0); // Unselected by default!

                isSyncingSpinners = false;

                binding.spinnerOrigin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (isSyncingSpinners) return;
                        NodeEntity selected = (NodeEntity) binding.spinnerOrigin.getSelectedItem();
                        if (selected != null && selected.id != selectedOriginId) {
                            selectedOriginId = selected.id;
                            UserPreferences.setOriginId(MarketMapActivity.this, selectedOriginId);
                            if (selectedDestId > 0 && selectedDestId != selectedOriginId) {
                                calculateAndRenderRoute(selectedOriginId, selectedDestId);
                            } else {
                                clearRouteOnMap();
                            }
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

                binding.spinnerDest.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (isSyncingSpinners) return;
                        if (position == 0) {
                            // Unselected
                            selectedDestId = -1;
                            currentRouteResult = null;
                            resetCardToDefaultPrompt();
                            clearRouteOnMap();
                        } else {
                            NodeEntity selected = allNodes.get(position - 1);
                            selectDestinationHub(selected.id, selected.name);
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

                // Render map initialized with only origin marked (no auto destination selected)
                renderBaseMap();
            });
        });
    }

    private void renderBaseMap() {
        String html = MarketMapHtmlHelper.buildMapHtml(
                allNodes, allEdges, selectedOriginId, -1, null);
        isMapLoaded = true;
        binding.mapWebView.loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null);
    }

    private void clearRouteOnMap() {
        if (isMapLoaded) {
            binding.mapWebView.evaluateJavascript("if(window.clearRoute){window.clearRoute(" + selectedOriginId + ");}", null);
        }
    }

    private void selectDestinationHub(int destId, String destName) {
        if (destId == selectedOriginId) {
            binding.textSelectedHubTitle.setText(destName + " (Your Location)");
            binding.textSelectedHubPrice.setText("Origin");
            binding.textOptimalRouteNodes.setText("This is your origin. Select a different district hub to view route & profits.");
            binding.layoutMetaRow.setVisibility(View.GONE);
            binding.buttonViewRouteDetails.setVisibility(View.GONE);
            selectedDestId = -1;
            clearRouteOnMap();
            return;
        }

        selectedDestId = destId;

        // Synchronize spinner selection without re-triggering listener
        isSyncingSpinners = true;
        for (int i = 0; i < allNodes.size(); i++) {
            if (allNodes.get(i).id == destId) {
                binding.spinnerDest.setSelection(i + 1);
                break;
            }
        }
        isSyncingSpinners = false;

        calculateAndRenderRoute(selectedOriginId, destId);
    }

    private void calculateAndRenderRoute(int originId, int destId) {
        final Context app = getApplicationContext();
        binding.mapProgress.setVisibility(View.VISIBLE);

        AppDatabase.IO.execute(() -> {
            try {
                NetworkDao dao = AppDatabase.get(app).networkDao();
                if (snapshot == null) {
                    snapshot = NetworkSnapshot.load(dao);
                }
                double rate = 0;
                for (CropInfo info : CropXmlParser.parse(app)) {
                    if (info.key.equals(currentCropKey)) {
                        rate = info.spoilagePctPerHour;
                    }
                }
                PriceEntity pe = dao.getPrice(destId, currentCropKey);
                final double price = pe != null ? pe.price : 38.0;
                currentPrice = price;

                double qty = UserPreferences.getQuantity(app, 1000.0);
                final RouteResult result = RouteCalculator.compute(
                        snapshot, originId, destId, price, qty, rate, false);

                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    binding.mapProgress.setVisibility(View.GONE);
                    currentRouteResult = result;

                    // Update UI Card
                    String destName = result.option.marketName;
                    binding.textSelectedHubTitle.setText(destName + " Market Hub");
                    binding.textSelectedHubPrice.setText(String.format(Locale.US, "৳ %.1f / kg", price));
                    binding.textOptimalRouteNodes.setText("Optimal Route: " + TextUtils.join(" → ", result.placeNames));

                    binding.layoutMetaRow.setVisibility(View.VISIBLE);
                    binding.textMetaDist.setText(String.format(Locale.US, "📍 %.0f km", result.km));
                    binding.textMetaTime.setText(String.format(Locale.US, "⏱ %s", Fmt.duration(this, result.minutes)));
                    binding.textMetaProfit.setText(String.format(Locale.US, "Est. Profit ৳ %,d", Math.round(result.option.netProfit)));

                    binding.buttonViewRouteDetails.setVisibility(View.VISIBLE);

                    // Update route on map in-place (keeps zoom & position fixed)
                    String coordsJson = MarketMapHtmlHelper.buildCoordsJson(result.nodes);
                    if (isMapLoaded) {
                        binding.mapWebView.evaluateJavascript(
                                "if(window.updateRoute){window.updateRoute(" + originId + "," + destId + "," + coordsJson + ");}", null);
                    } else {
                        String html = MarketMapHtmlHelper.buildMapHtml(
                                allNodes, allEdges, selectedOriginId, selectedDestId, result.nodes);
                        isMapLoaded = true;
                        binding.mapWebView.loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    binding.mapProgress.setVisibility(View.GONE);
                    binding.textOptimalRouteNodes.setText("Route calculation error: " + e.getMessage());
                });
            }
        });
    }
}
