package com.kuet.krishichain.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.krishichain.MainActivity;
import com.kuet.krishichain.R;
import com.kuet.krishichain.algo.PriceReport;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.FirebaseRepository;
import com.kuet.krishichain.data.NetworkDao;
import com.kuet.krishichain.data.NodeEntity;
import com.kuet.krishichain.data.PriceEntity;
import com.kuet.krishichain.databinding.ItemPriceReportBinding;
import com.kuet.krishichain.databinding.ItemProductGroupHeaderBinding;
import com.kuet.krishichain.databinding.TabPricesContentBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PricesTabHelper {
    private final MainActivity activity;
    private final TabPricesContentBinding binding;
    private final Map<Integer, String> nodeNames = new HashMap<>();
    private final List<PriceEntity> allPrices = new ArrayList<>();
    private AllPricesAdapter adapter;

    private boolean isSortByPrice = true;
    private String selectedFilterCrop = null;
    private ListenerRegistration firestoreListener;

    private static final List<String> FIXED_CROP_ORDER = Arrays.asList("potato", "tomato", "onion");

    public PricesTabHelper(MainActivity activity, TabPricesContentBinding binding) {
        this.activity = activity;
        this.binding = binding;
    }

    public void setup() {
        binding.buttonBack.setOnClickListener(v -> activity.selectTab(BottomNavHelper.Tab.HOME));

        adapter = new AllPricesAdapter();
        binding.recyclerAllPrices.setLayoutManager(new LinearLayoutManager(activity));
        binding.recyclerAllPrices.setAdapter(adapter);

        binding.buttonSortPrice.setOnClickListener(v -> {
            isSortByPrice = true;
            updateSortButtonsUI();
            rebuildAndDisplay();
            Toast.makeText(activity, "Sorted by Price (Highest first) in all groups", Toast.LENGTH_SHORT).show();
        });

        binding.buttonSortDistrict.setOnClickListener(v -> {
            isSortByPrice = false;
            updateSortButtonsUI();
            rebuildAndDisplay();
            Toast.makeText(activity, "Sorted by District (A to Z) in all groups", Toast.LENGTH_SHORT).show();
        });

        binding.buttonSubmitPrice.setOnClickListener(v -> openReportDialog());
        binding.buttonHeaderReportPrice.setOnClickListener(v -> openReportDialog());

        binding.tabAllCrops.setOnClickListener(v -> selectFilter(null));
        binding.tabPotato.setOnClickListener(v -> selectFilter("potato"));
        binding.tabTomato.setOnClickListener(v -> selectFilter("tomato"));
        binding.tabOnion.setOnClickListener(v -> selectFilter("onion"));

        selectFilter(selectedFilterCrop);
        updateSortButtonsUI();
        loadData();
        subscribeToReports();
    }

    public void onTabSelected() {
        loadData();
    }

    public void filterByCrop(String cropKey) {
        selectFilter(cropKey);
    }

    public void onDestroy() {
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }
    }

    private void openReportDialog() {
        PriceReportDialogHelper.show(activity, selectedFilterCrop, null, (crop, price, nodeId) -> {
            loadData();
        });
    }

    private void subscribeToReports() {
        firestoreListener = FirebaseRepository.listenToReports(new FirebaseRepository.ReportsCallback() {
            @Override
            public void onSuccess(List<PriceReport> reports) {
                if (reports != null && !reports.isEmpty()) {
                    AppDatabase.IO.execute(() -> {
                        FirebaseRepository.syncCrowdReports(activity.getApplicationContext(), reports);
                        loadData();
                    });
                }
            }

            @Override
            public void onError(Exception e) {}
        });
    }

    private void selectFilter(String cropKey) {
        selectedFilterCrop = cropKey;
        setTabSelected(binding.tabAllCrops, cropKey == null);
        setTabSelected(binding.tabPotato, "potato".equalsIgnoreCase(cropKey));
        setTabSelected(binding.tabTomato, "tomato".equalsIgnoreCase(cropKey));
        setTabSelected(binding.tabOnion, "onion".equalsIgnoreCase(cropKey));
        rebuildAndDisplay();
    }

    private void setTabSelected(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_tab_on : R.drawable.bg_tab_off);
        tab.setTextColor(selected ? Color.WHITE : Color.parseColor("#142C1B"));
    }

    private void updateSortButtonsUI() {
        int activeBg = Color.parseColor("#1B7A2F");
        int inactiveBg = Color.parseColor("#F1F5F2");
        int activeText = Color.WHITE;
        int inactiveText = Color.parseColor("#3B5242");

        if (isSortByPrice) {
            binding.buttonSortPrice.setBackgroundTintList(ColorStateList.valueOf(activeBg));
            binding.buttonSortPrice.setTextColor(activeText);
            binding.buttonSortDistrict.setBackgroundTintList(ColorStateList.valueOf(inactiveBg));
            binding.buttonSortDistrict.setTextColor(inactiveText);
        } else {
            binding.buttonSortPrice.setBackgroundTintList(ColorStateList.valueOf(inactiveBg));
            binding.buttonSortPrice.setTextColor(inactiveText);
            binding.buttonSortDistrict.setBackgroundTintList(ColorStateList.valueOf(activeBg));
            binding.buttonSortDistrict.setTextColor(activeText);
        }
    }

    public void loadData() {
        AppDatabase.IO.execute(() -> {
            NetworkDao dao = AppDatabase.get(activity.getApplicationContext()).networkDao();
            List<NodeEntity> nodes = dao.getAllNodes();
            for (NodeEntity n : nodes) {
                nodeNames.put(n.id, n.name);
            }
            List<PriceEntity> prices = dao.getAllPrices();
            allPrices.clear();
            allPrices.addAll(prices);

            activity.runOnUiThread(() -> {
                binding.textTotalCount.setText(prices.size() + " Hubs");
                rebuildAndDisplay();
            });
        });
    }

    private void rebuildAndDisplay() {
        if (allPrices.isEmpty()) {
            adapter.setItems(Collections.emptyList());
            return;
        }

        Map<String, List<PriceEntity>> grouped = new LinkedHashMap<>();
        for (String key : FIXED_CROP_ORDER) {
            grouped.put(key, new ArrayList<>());
        }
        for (PriceEntity p : allPrices) {
            String crop = p.crop != null ? p.crop.toLowerCase(Locale.US) : "other";
            if (!grouped.containsKey(crop)) {
                grouped.put(crop, new ArrayList<>());
            }
            grouped.get(crop).add(p);
        }

        for (Map.Entry<String, List<PriceEntity>> entry : grouped.entrySet()) {
            List<PriceEntity> items = entry.getValue();
            if (isSortByPrice) {
                items.sort((a, b) -> Double.compare(b.price, a.price));
            } else {
                items.sort((a, b) -> {
                    String nameA = nodeNames.getOrDefault(a.nodeId, "District " + a.nodeId);
                    String nameB = nodeNames.getOrDefault(b.nodeId, "District " + b.nodeId);
                    return nameA.compareToIgnoreCase(nameB);
                });
            }
        }

        List<Object> displayList = new ArrayList<>();
        for (Map.Entry<String, List<PriceEntity>> entry : grouped.entrySet()) {
            String cropKey = entry.getKey();
            List<PriceEntity> items = entry.getValue();
            if (items.isEmpty()) continue;

            if (selectedFilterCrop != null && !selectedFilterCrop.equalsIgnoreCase(cropKey)) {
                continue;
            }

            displayList.add(new CropGroupHeader(cropKey, items.size()));
            for (PriceEntity p : items) {
                displayList.add(p);
            }
        }

        adapter.setItems(displayList);
    }

    public static class CropGroupHeader {
        public final String cropKey;
        public final int count;

        public CropGroupHeader(String cropKey, int count) {
            this.cropKey = cropKey;
            this.count = count;
        }

        public String getDisplayName() {
            if ("potato".equalsIgnoreCase(cropKey)) return "🥔 Potato Wholesale Rates";
            if ("tomato".equalsIgnoreCase(cropKey)) return "🍅 Tomato Wholesale Rates";
            if ("onion".equalsIgnoreCase(cropKey)) return "🧅 Onion Wholesale Rates";
            return cropKey.substring(0, 1).toUpperCase(Locale.US) + cropKey.substring(1) + " Rates";
        }
    }

    private class AllPricesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        private final List<Object> items = new ArrayList<>();

        public void setItems(List<Object> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof CropGroupHeader ? TYPE_HEADER : TYPE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                ItemProductGroupHeaderBinding b = ItemProductGroupHeaderBinding.inflate(inflater, parent, false);
                return new HeaderViewHolder(b);
            } else {
                ItemPriceReportBinding b = ItemPriceReportBinding.inflate(inflater, parent, false);
                return new ItemViewHolder(b);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object obj = items.get(position);
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind((CropGroupHeader) obj);
            } else if (holder instanceof ItemViewHolder) {
                ((ItemViewHolder) holder).bind((PriceEntity) obj);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            private final ItemProductGroupHeaderBinding binding;

            HeaderViewHolder(ItemProductGroupHeaderBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(CropGroupHeader header) {
                binding.textGroupTitle.setText(header.getDisplayName());
                binding.textGroupCount.setText(header.count + " District Hubs");
            }
        }

        class ItemViewHolder extends RecyclerView.ViewHolder {
            private final ItemPriceReportBinding binding;

            ItemViewHolder(ItemPriceReportBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(PriceEntity p) {
                String district = nodeNames.getOrDefault(p.nodeId, "District #" + p.nodeId);
                binding.textDistrictName.setText(district + " Market Hub");

                String cropEmoji = "🥔";
                if ("tomato".equalsIgnoreCase(p.crop)) cropEmoji = "🍅";
                else if ("onion".equalsIgnoreCase(p.crop)) cropEmoji = "🧅";

                String cropName = p.crop != null
                        ? p.crop.substring(0, 1).toUpperCase(Locale.US) + p.crop.substring(1)
                        : "Produce";
                binding.textMarketCrop.setText(cropEmoji + " " + cropName);

                String date = p.date != null ? p.date : "Today";
                binding.textReportMeta.setText("Wholesale • " + date);
                binding.textPrice.setText(String.format(Locale.US, "৳ %.1f / kg", p.price));
                binding.textStatusBadge.setText("VERIFIED HUB");

                binding.getRoot().setOnClickListener(v -> {
                    Toast.makeText(activity,
                            district + ": " + cropName + " ৳ " + String.format(Locale.US, "%.1f", p.price) + "/kg",
                            Toast.LENGTH_SHORT).show();
                });
            }
        }
    }
}
