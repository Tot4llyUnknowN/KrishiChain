package com.kuet.krishichain.ui;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import com.kuet.krishichain.MainActivity;
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
import com.kuet.krishichain.databinding.TabMapContentBinding;
import com.kuet.krishichain.domain.DistrictDemand;
import com.kuet.krishichain.domain.NetworkSnapshot;
import com.kuet.krishichain.domain.RouteCalculator;
import com.kuet.krishichain.domain.RouteResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapTabHelper {
    private static final String[] CROP_KEYS = {"potato", "tomato", "onion"};

    private final MainActivity activity;
    private final TabMapContentBinding binding;
    private List<NodeEntity> allNodes = new ArrayList<>();
    private List<EdgeEntity> allEdges = new ArrayList<>();
    private NetworkSnapshot snapshot;
    private int selectedOriginId = 1;
    private int selectedDestId = -1;
    private String currentCropKey = "potato";
    private String currentCropName = "Potato";
    private double currentPrice = 38.0;
    private RouteResult currentRouteResult;
    private boolean isMapLoaded = false;
    private boolean isSyncingSpinners = false;

    public MapTabHelper(MainActivity activity, TabMapContentBinding binding) {
        this.activity = activity;
        this.binding = binding;
    }

    public void setup() {
        binding.buttonBack.setOnClickListener(v -> activity.selectTab(BottomNavHelper.Tab.HOME));

        selectedOriginId = UserPreferences.getOriginId(activity, 1);
        currentCropKey = UserPreferences.getCropKey(activity, "potato");

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
                activity.runOnUiThread(() -> selectDestinationHub(id, name));
            }
        }, "Android");

        ArrayAdapter<CharSequence> cropAdapter = ArrayAdapter.createFromResource(
                activity, R.array.crop_names, R.layout.item_spinner_selected);
        cropAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.spinnerCrop.setAdapter(cropAdapter);

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
                UserPreferences.setCropKey(activity, currentCropKey);

                if (selectedDestId > 0 && selectedDestId != selectedOriginId) {
                    calculateAndRenderRoute(selectedOriginId, selectedDestId);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        binding.buttonResetView.setOnClickListener(v -> {
            selectedDestId = -1;
            binding.autoCompleteDest.setText("", false);
            resetCardToDefaultPrompt();
            clearRouteOnMap();
        });

        binding.buttonViewRouteDetails.setOnClickListener(v -> {
            if (currentRouteResult == null || selectedDestId <= 0) return;
            Intent intent = new Intent(activity, RouteActivity.class);
            intent.putExtra(RankingActivity.EXTRA_CROP_KEY, currentCropKey);
            intent.putExtra(RankingActivity.EXTRA_CROP_NAME, currentCropName);
            intent.putExtra(RankingActivity.EXTRA_QTY_KG, UserPreferences.getQuantity(activity, 1000.0));
            intent.putExtra(RankingActivity.EXTRA_ORIGIN_ID, selectedOriginId);
            intent.putExtra(RankingActivity.EXTRA_ORIGIN_NAME, getOriginName());
            intent.putExtra(RouteActivity.EXTRA_DEST_ID, selectedDestId);
            intent.putExtra(RouteActivity.EXTRA_PRICE, currentPrice);
            activity.startActivity(intent);
        });

        resetCardToDefaultPrompt();
        loadNetwork();
    }

    public void onTabSelected() {
        syncWithSharedPreferences();
    }

    private void syncWithSharedPreferences() {
        int prefOriginId = UserPreferences.getOriginId(activity, selectedOriginId);
        String prefCropKey = UserPreferences.getCropKey(activity, currentCropKey);

        boolean originChanged = (prefOriginId != selectedOriginId);
        boolean cropChanged = (!prefCropKey.equalsIgnoreCase(currentCropKey));

        if (originChanged || cropChanged) {
            isSyncingSpinners = true;
            if (originChanged) {
                selectedOriginId = prefOriginId;
                if (binding.autoCompleteOrigin.getAdapter() != null) {
                    for (int i = 0; i < allNodes.size(); i++) {
                        if (allNodes.get(i).id == selectedOriginId) {
                            binding.autoCompleteOrigin.setText(allNodes.get(i).name, false);
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
        binding.textSelectedHubRate.setText("Tap any pin");
        binding.textOptimalRouteNodes.setText("Tap any district on the map or select from the dropdown to calculate the optimal highway route & net profit.");
        binding.layoutMetaRow.setVisibility(View.GONE);
        binding.buttonViewRouteDetails.setVisibility(View.GONE);
    }

    private void loadNetwork() {
        final Context app = activity.getApplicationContext();
        AppDatabase.IO.execute(() -> {
            try {
                NetworkLoader.loadIfNeeded(app);
            } catch (Exception ignored) {}
            NetworkDao dao = AppDatabase.get(app).networkDao();
            allNodes = dao.getAllNodes();
            allEdges = dao.getAllEdges();
            snapshot = NetworkSnapshot.load(dao);

            activity.runOnUiThread(() -> {
                if (activity.isDestroyed()) return;

                isSyncingSpinners = true;

                List<String> originNames = new ArrayList<>();
                for (NodeEntity n : allNodes) {
                    originNames.add(n.name);
                }
                SearchableDistrictAdapter originAdapter = new SearchableDistrictAdapter(
                        activity, android.R.layout.simple_dropdown_item_1line, originNames);
                binding.autoCompleteOrigin.setAdapter(originAdapter);

                for (int i = 0; i < allNodes.size(); i++) {
                    if (allNodes.get(i).id == selectedOriginId) {
                        binding.autoCompleteOrigin.setText(allNodes.get(i).name, false);
                        break;
                    }
                }

                binding.autoCompleteOrigin.setOnClickListener(v -> binding.autoCompleteOrigin.showDropDown());
                binding.autoCompleteOrigin.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) binding.autoCompleteOrigin.showDropDown();
                });
                binding.autoCompleteOrigin.setOnItemClickListener((parent, view, position, id) -> {
                    String selName = (String) parent.getItemAtPosition(position);
                    for (NodeEntity n : allNodes) {
                        if (n.name.equalsIgnoreCase(selName)) {
                            selectedOriginId = n.id;
                            UserPreferences.setOriginId(activity, selectedOriginId);
                            if (selectedDestId > 0 && selectedDestId != selectedOriginId) {
                                calculateAndRenderRoute(selectedOriginId, selectedDestId);
                            } else {
                                clearRouteOnMap();
                            }
                            break;
                        }
                    }
                });

                SearchableDistrictAdapter destAdapter = new SearchableDistrictAdapter(
                        activity, android.R.layout.simple_dropdown_item_1line, originNames);
                binding.autoCompleteDest.setAdapter(destAdapter);
                binding.autoCompleteDest.setOnClickListener(v -> binding.autoCompleteDest.showDropDown());
                binding.autoCompleteDest.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) binding.autoCompleteDest.showDropDown();
                });
                binding.autoCompleteDest.setOnItemClickListener((parent, view, position, id) -> {
                    String selName = (String) parent.getItemAtPosition(position);
                    for (NodeEntity n : allNodes) {
                        if (n.name.equalsIgnoreCase(selName)) {
                            selectDestinationHub(n.id, n.name);
                            break;
                        }
                    }
                });

                isSyncingSpinners = false;
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
            binding.textSelectedHubRate.setText("Origin");
            binding.textOptimalRouteNodes.setText("This is your origin. Select a different district hub to view route & profits.");
            binding.layoutMetaRow.setVisibility(View.GONE);
            binding.buttonViewRouteDetails.setVisibility(View.GONE);
            selectedDestId = -1;
            clearRouteOnMap();
            return;
        }

        selectedDestId = destId;
        isSyncingSpinners = true;
        binding.autoCompleteDest.setText(destName, false);
        isSyncingSpinners = false;

        calculateAndRenderRoute(selectedOriginId, destId);
    }

    private void calculateAndRenderRoute(int originId, int destId) {
        final Context app = activity.getApplicationContext();

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
                double prodCost = UserPreferences.getProductionCost(app, 20.0);
                final RouteResult result = RouteCalculator.compute(
                        snapshot, originId, destId, price, qty, prodCost, rate, false);

                activity.runOnUiThread(() -> {
                    if (activity.isDestroyed()) return;
                    currentRouteResult = result;

                    String destName = result.option.marketName;
                    binding.textSelectedHubTitle.setText(destName + " Market Hub");
                    binding.textSelectedHubRate.setText(String.format(Locale.US, "৳ %.1f / kg", price));
                    binding.textOptimalRouteNodes.setText("Optimal Route: " + TextUtils.join(" → ", result.placeNames));

                    binding.layoutMetaRow.setVisibility(View.VISIBLE);
                    double demandTons = DistrictDemand.getDemandTons(destId);
                    binding.textMetaDist.setText(String.format(Locale.US, "📍 %.0f km · %.0ft demand", result.km, demandTons));
                    binding.textMetaTime.setText(String.format(Locale.US, "⏱ %s", Fmt.duration(activity, result.minutes)));
                    binding.textMetaProfit.setText(String.format(Locale.US, "Est. Profit ৳ %,d", Math.round(result.option.netProfit)));

                    binding.buttonViewRouteDetails.setVisibility(View.VISIBLE);

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
                activity.runOnUiThread(() -> {
                    if (activity.isDestroyed()) return;
                    binding.textOptimalRouteNodes.setText("Route calculation error: " + e.getMessage());
                });
            }
        });
    }
}
