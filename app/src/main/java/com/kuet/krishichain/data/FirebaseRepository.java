package com.kuet.krishichain.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.kuet.krishichain.algo.OutlierFilter;
import com.kuet.krishichain.algo.PriceReport;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles Firebase Authentication and Cloud Firestore crowd-sourced price reports.
 * Includes offline persistence and an outlier rejection filter.
 */
public final class FirebaseRepository {
    private static final String TAG = "KrishiChain_Firebase";
    public static final String COLLECTION_REPORTS = "price_reports";

    public interface AuthCallback {
        void onSuccess(String uid);
        void onError(Exception e);
    }

    public interface ReportsCallback {
        void onSuccess(List<PriceReport> reports);
        void onError(Exception e);
    }

    public interface CompleteCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    private FirebaseRepository() {}

    /** Authenticates the user anonymously or returns the existing UID. */
    public static void ensureAuth(@NonNull AuthCallback callback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            callback.onSuccess(current.getUid());
            return;
        }
        auth.signInAnonymously()
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user != null) {
                        callback.onSuccess(user.getUid());
                    } else {
                        callback.onError(new IllegalStateException("User null after anonymous sign-in"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /** Submits a new crop price report to Firestore. */
    public static void submitPriceReport(String crop, double price, int nodeId,
                                         CompleteCallback callback) {
        ensureAuth(new AuthCallback() {
            @Override
            public void onSuccess(String uid) {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                Map<String, Object> data = new HashMap<>();
                data.put("crop", crop.toLowerCase(Locale.ROOT).trim());
                data.put("price", price);
                data.put("nodeId", nodeId);
                data.put("dateMillis", System.currentTimeMillis());
                data.put("userId", uid);

                db.collection(COLLECTION_REPORTS)
                        .add(data)
                        .addOnSuccessListener(ref -> callback.onSuccess("Price reported successfully!"))
                        .addOnFailureListener(e -> callback.onError("Failed to submit: " + e.getMessage()));
            }

            @Override
            public void onError(Exception e) {
                callback.onError("Authentication failed: " + e.getMessage());
            }
        });
    }

    /** Listens for real-time price reports submitted across the network. */
    public static ListenerRegistration listenToReports(ReportsCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        return db.collection(COLLECTION_REPORTS)
                .orderBy("dateMillis", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    if (snapshots != null) {
                        List<PriceReport> reports = parseSnapshots(snapshots);
                        callback.onSuccess(reports);
                    }
                });
    }

    /** Parses Firestore document snapshots into PriceReport domain models. */
    private static List<PriceReport> parseSnapshots(QuerySnapshot snapshots) {
        List<PriceReport> list = new ArrayList<>();
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            String crop = doc.getString("crop");
            Double price = doc.getDouble("price");
            Long nodeId = doc.getLong("nodeId");
            Long dateMillis = doc.getLong("dateMillis");
            String userId = doc.getString("userId");
            if (crop != null && price != null && nodeId != null && dateMillis != null) {
                list.add(new PriceReport(crop, price, dateMillis, nodeId.intValue(), userId));
            }
        }
        return list;
    }

    /**
     * Synchronously fetches Firestore reports, runs outlier rejection,
     * and updates Room database prices. Safe to call on background threads (e.g. SyncService).
     */
    public static int syncCrowdPrices(Context context) throws Exception {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        QuerySnapshot snapshots = Tasks.await(
                db.collection(COLLECTION_REPORTS)
                        .orderBy("dateMillis", Query.Direction.DESCENDING)
                        .limit(100)
                        .get()
        );

        List<PriceReport> reports = parseSnapshots(snapshots);
        if (reports.isEmpty()) {
            return 0;
        }

        // Apply Outlier Filter (Item #9)
        OutlierFilter filter = new OutlierFilter(0.40);
        OutlierFilter.FilterResult result = filter.filter(reports);

        // Group valid reports by (crop, nodeId)
        Map<String, List<Double>> grouped = new HashMap<>();
        for (PriceReport r : result.validReports) {
            String key = r.crop.toLowerCase(Locale.ROOT) + "@" + r.nodeId;
            List<Double> list = grouped.get(key);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(key, list);
            }
            list.add(r.price);
        }

        AppDatabase localDb = AppDatabase.get(context);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String today = sdf.format(new Date());

        int count = 0;
        for (Map.Entry<String, List<Double>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("@");
            String crop = parts[0];
            int nodeId = Integer.parseInt(parts[1]);

            double sum = 0;
            for (double p : entry.getValue()) sum += p;
            double avgPrice = Math.round((sum / entry.getValue().size()) * 10.0) / 10.0;

            int rows = localDb.networkDao().updatePrice(nodeId, crop, avgPrice, "Crowd report", today);
            if (rows == 0) {
                PriceEntity pe = new PriceEntity();
                pe.nodeId = nodeId;
                pe.crop = crop;
                pe.price = avgPrice;
                pe.source = "Crowd report";
                pe.date = today;
                localDb.networkDao().insertPrices(Collections.singletonList(pe));
            }
            count++;
        }
        Log.i(TAG, "Synced " + count + " crowd prices to Room. (Rejected "
                + result.rejectedReports.size() + " outliers)");
        return count;
    }
}
