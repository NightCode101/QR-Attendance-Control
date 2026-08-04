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
        // We no longer exit early if offline. 
        // Firestore .set() with persistence enabled will cache the update and push when online.
        
        List<AttendanceRecord> unsyncedRecords = db.getUnsyncedRecords();
        if (unsyncedRecords.isEmpty()) {
            Log.d(TAG, "No unsynced records found.");
            return;
        }

        Log.d(TAG, "Syncing " + unsyncedRecords.size() + " records (Robust Mode)...");

        for (AttendanceRecord record : unsyncedRecords) {
            String docId = record.getIdHash();
            DocumentReference docRef = firestore.collection("attendance_records").document(docId);

            Map<String, Object> uploadData = new HashMap<>();
            
            // Only upload fields that have actual data to prevent overwriting cloud data with "-"
            if (isFilled(record.getTimeInAM())) uploadData.put("time_in_am", record.getTimeInAM());
            if (isFilled(record.getTimeOutAM())) uploadData.put("time_out_am", record.getTimeOutAM());
            if (isFilled(record.getTimeInPM())) uploadData.put("time_in_pm", record.getTimeInPM());
            if (isFilled(record.getTimeOutPM())) uploadData.put("time_out_pm", record.getTimeOutPM());

            if (!uploadData.isEmpty()) {
                uploadData.put("name", record.getName());
                uploadData.put("studentID", record.getStudentID());
                uploadData.put("date", record.getDate());
                uploadData.put("section", record.getSection());
                uploadData.put("version", "8.2.0"); // Updated to 8.2.0
                
                docRef.set(uploadData, SetOptions.merge())
                    .addOnSuccessListener(unused -> {
                        db.markAsSynced(record.getId());
                        Log.d(TAG, "Record successfully pushed/merged: " + record.getStudentID());
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to initiate sync for " + record.getId(), e));
            } else {
                // If for some reason it's "unsynced" but has no data, mark as synced to stop retrying
                db.markAsSynced(record.getId());
            }
        }
    }

    private boolean isFilled(String value) {
        return value != null && !value.equals("-") && !value.trim().isEmpty();
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
