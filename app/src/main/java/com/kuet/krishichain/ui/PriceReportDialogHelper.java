package com.kuet.krishichain.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.kuet.krishichain.R;
import com.kuet.krishichain.data.AppDatabase;
import com.kuet.krishichain.data.FirebaseRepository;
import com.kuet.krishichain.data.NetworkDao;
import com.kuet.krishichain.data.NodeEntity;
import com.kuet.krishichain.data.PriceEntity;
import com.kuet.krishichain.databinding.DialogReportPriceBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PriceReportDialogHelper {

    public interface OnReportSubmittedListener {
        void onReportSubmitted(String crop, double price, int nodeId);
    }

    public static void show(Context context, String defaultCropKey, Integer defaultNodeId, OnReportSubmittedListener listener) {
        DialogReportPriceBinding db = DialogReportPriceBinding.inflate(LayoutInflater.from(context));
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(db.getRoot())
                .setCancelable(true)
                .create();

        // 1. Setup Crop Spinner
        ArrayAdapter<CharSequence> cropAdapter = ArrayAdapter.createFromResource(context,
                R.array.crop_names, android.R.layout.simple_spinner_item);
        cropAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        db.dialogSpinnerCrop.setAdapter(cropAdapter);

        if (defaultCropKey != null) {
            String c = defaultCropKey.toLowerCase(Locale.ROOT);
            if (c.contains("tomato")) {
                db.dialogSpinnerCrop.setSelection(1);
            } else if (c.contains("onion")) {
                db.dialogSpinnerCrop.setSelection(2);
            } else {
                db.dialogSpinnerCrop.setSelection(0); // potato
            }
        } else {
            db.dialogSpinnerCrop.setSelection(0);
        }

        // 2. Setup Hubs Spinner asynchronously from Room
        AppDatabase.IO.execute(() -> {
            NetworkDao dao = AppDatabase.get(context.getApplicationContext()).networkDao();
            List<NodeEntity> hubs = dao.getAllNodes();
            List<String> hubNames = new ArrayList<>();
            int selectedHubPos = 0;
            for (int i = 0; i < hubs.size(); i++) {
                NodeEntity n = hubs.get(i);
                hubNames.add(n.name);
                if (defaultNodeId != null && n.id == defaultNodeId) {
                    selectedHubPos = i;
                }
            }
            final int initialHubPos = selectedHubPos;
            if (context instanceof Activity) {
                ((Activity) context).runOnUiThread(() -> {
                    ArrayAdapter<String> hubAdapter = new ArrayAdapter<>(context,
                            android.R.layout.simple_spinner_item, hubNames);
                    hubAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    db.dialogSpinnerHub.setAdapter(hubAdapter);
                    if (initialHubPos < hubNames.size()) {
                        db.dialogSpinnerHub.setSelection(initialHubPos);
                    }
                });
            }

            // 3. Setup Submit click listener
            db.dialogButtonSubmit.setOnClickListener(v -> {
                String priceStr = db.dialogEditPrice.getText().toString().trim();
                if (TextUtils.isEmpty(priceStr)) {
                    db.dialogEditPrice.setError("Enter price");
                    return;
                }
                double price;
                try {
                    price = Double.parseDouble(priceStr);
                    if (price <= 0) throw new NumberFormatException();
                } catch (Exception e) {
                    db.dialogEditPrice.setError("Invalid price");
                    return;
                }

                int hubIdx = db.dialogSpinnerHub.getSelectedItemPosition();
                if (hubIdx < 0 || hubIdx >= hubs.size()) return;
                int nodeId = hubs.get(hubIdx).id;

                String crop = db.dialogSpinnerCrop.getSelectedItem().toString().toLowerCase(Locale.ROOT);
                if (crop.contains("potato")) crop = "potato";
                else if (crop.contains("onion")) crop = "onion";
                else if (crop.contains("tomato")) crop = "tomato";

                final String cropFinal = crop;
                final double priceFinal = price;
                db.dialogButtonSubmit.setEnabled(false);

                // Save locally first to guarantee immediate offline-ready persistence
                AppDatabase.IO.execute(() -> {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    String today = sdf.format(new Date());
                    int updated = dao.updatePrice(nodeId, cropFinal, priceFinal, "User report", today);
                    if (updated == 0) {
                        PriceEntity pe = new PriceEntity();
                        pe.nodeId = nodeId;
                        pe.crop = cropFinal;
                        pe.price = priceFinal;
                        pe.source = "User report";
                        pe.date = today;
                        dao.insertPrices(Collections.singletonList(pe));
                    }
                });

                // Submit to Firebase
                FirebaseRepository.submitPriceReport(cropFinal, priceFinal, nodeId, new FirebaseRepository.CompleteCallback() {
                    @Override
                    public void onSuccess(String message) {
                        Toast.makeText(context, "Price report submitted to Firebase!", Toast.LENGTH_SHORT).show();
                        if (listener != null) {
                            listener.onReportSubmitted(cropFinal, priceFinal, nodeId);
                        }
                        dialog.dismiss();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(context, "Saved locally (" + error + ")", Toast.LENGTH_SHORT).show();
                        if (listener != null) {
                            listener.onReportSubmitted(cropFinal, priceFinal, nodeId);
                        }
                        dialog.dismiss();
                    }
                });
            });
        });

        db.dialogButtonCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
