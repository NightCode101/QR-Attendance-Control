package cics.csup.qrattendancecontrol;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SyncManager {

    private static final String TAG = "SyncManager";
    private static final String ADMIN_PREFS = "AdminPrefs";
    private static final String ADMIN_KEY = "admin_password";

    private final Context context;
    private final AttendanceDBHelper db;
    private final FirebaseFirestore firestore;
    private final FirebaseRemoteConfig remoteConfig;

    public SyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = new AttendanceDBHelper(this.context);
        this.firestore = FirebaseFirestore.getInstance();
        this.remoteConfig = FirebaseRemoteConfig.getInstance();
    }

    public void fetchAndCacheAdminPassword() {
        // Source 1: Firestore
        firestore.collection("config").document("admin")
                .get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    if (doc.exists()) {
                        String pw = doc.getString("password");
                        if (pw != null && !pw.isEmpty()) {
                            saveAdminPassword(pw);
                            Log.d(TAG, "Admin password cached from Firestore.");
                        } else {
                            fetchAdminFromRemoteConfig();
                        }
                    } else {
                        fetchAdminFromRemoteConfig();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore admin fetch failed: " + e.getMessage());
                    fetchAdminFromRemoteConfig();
                });
    }

    private void fetchAdminFromRemoteConfig() {
        // Source 2: Remote Config (Fallback)
        remoteConfig.fetchAndActivate()
                .addOnCompleteListener(task -> {
                    String pw = remoteConfig.getString("admin_password");
                    if (pw != null && !pw.isEmpty() && !pw.equals("default_value")) {
                        saveAdminPassword(pw);
                        Log.d(TAG, "Admin password cached from Remote Config.");
                    } else {
                        Log.w(TAG, "Admin password not found in Remote Config.");
                    }
                });
    }

    private void saveAdminPassword(String password) {
        context.getSharedPreferences(ADMIN_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(ADMIN_KEY, password)
                .apply();
    }

    public void syncUnsyncedRecords() {
        if (!isOnline()) {
            Log.d(TAG, "Offline, skipping sync.");
            return;
        }

        List<AttendanceRecord> unsyncedRecords = db.getUnsyncedRecords();
        if (unsyncedRecords.isEmpty()) {
            Log.d(TAG, "No unsynced records found.");
            return;
        }

        Log.d(TAG, "Syncing " + unsyncedRecords.size() + " records...");

        for (AttendanceRecord record : unsyncedRecords) {
            String docId = record.getIdHash();
            DocumentReference docRef = firestore.collection("attendance_records").document(docId);

            firestore.runTransaction((Transaction.Function<Void>) transaction -> {
                DocumentSnapshot snapshot = transaction.get(docRef);
                Map<String, Object> existing = snapshot.getData();
                if (existing == null) existing = new HashMap<>();

                Map<String, Object> uploadData = new HashMap<>();
                if (shouldSyncField(existing, "time_in_am", record.getTimeInAM())) uploadData.put("time_in_am", record.getTimeInAM());
                if (shouldSyncField(existing, "time_out_am", record.getTimeOutAM())) uploadData.put("time_out_am", record.getTimeOutAM());
                if (shouldSyncField(existing, "time_in_pm", record.getTimeInPM())) uploadData.put("time_in_pm", record.getTimeInPM());
                if (shouldSyncField(existing, "time_out_pm", record.getTimeOutPM())) uploadData.put("time_out_pm", record.getTimeOutPM());

                if (!uploadData.isEmpty()) {
                    uploadData.put("name", record.getName());
                    uploadData.put("studentID", record.getStudentID());
                    uploadData.put("date", record.getDate());
                    uploadData.put("section", record.getSection());
                    uploadData.put("version", "7.1.0"); // Updated to 7.1.0
                    transaction.set(docRef, uploadData, SetOptions.merge());
                }
                return null;
            }).addOnSuccessListener(unused -> {
                db.markAsSynced(record.getId());
                Log.d(TAG, "Record synced: " + record.getStudentID());
            }).addOnFailureListener(e -> Log.e(TAG, "Failed to sync record " + record.getId(), e));
        }
    }

    private boolean shouldSyncField(Map<String, Object> existing, String key, String localValue) {
        if (localValue == null || localValue.equals("-")) return false;
        return !existing.containsKey(key) || existing.get(key) == null || Objects.equals(existing.get(key), "-");
    }

    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) return false;
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }

        @SuppressWarnings("deprecation")
        android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }
}
