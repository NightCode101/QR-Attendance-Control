package cics.csup.qrattendancecontrol;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryActivity extends AppCompatActivity {

    private AttendanceDBHelper dbHelper;
    private LinearLayout historyContainer;
    private Button clearHistoryButton, exportCSVButton, syncButton;
    private List<AttendanceRecord> currentRecords;
    private EditText searchNameEditText;

    private Snackbar connectivitySnackbar;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final ActivityResultLauncher<Intent> createFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) writeCSVToUri(uri);
                }
            });

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setNavigationBarColor(Color.parseColor("#121212"));
        getWindow().setStatusBarColor(Color.parseColor("#121212"));
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(0);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        applyWindowInsetPadding();

        dbHelper = new AttendanceDBHelper(this);
        historyContainer = findViewById(R.id.historyContainer);
        clearHistoryButton = findViewById(R.id.clearHistoryButton);
        exportCSVButton = findViewById(R.id.exportCSVButton);
        syncButton = findViewById(R.id.syncButton);
        searchNameEditText = findViewById(R.id.searchNameEditText);

        // SwipeRefreshLayout
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadHistory();
            swipeRefreshLayout.setRefreshing(false);
        });

        loadHistory();
        setupNetworkCallback();

        syncButton.setOnClickListener(v -> {
            if (currentRecords == null || currentRecords.isEmpty()) {
                Toast.makeText(this, "No attendance data to sync.", Toast.LENGTH_SHORT).show();
            } else {
                syncOfflineDataToFirestore();
            }
        });

        clearHistoryButton.setOnClickListener(v -> {
            if (currentRecords == null || currentRecords.isEmpty()) {
                Toast.makeText(this, "No attendance history to clear.", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Clear History")
                    .setMessage("Are you sure you want to clear all records?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        dbHelper.clearAllAttendance();
                        loadHistory();
                        Toast.makeText(this, "Attendance history cleared.", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        exportCSVButton.setOnClickListener(v -> {
            if (currentRecords == null || currentRecords.isEmpty()) {
                Toast.makeText(this, "No attendance data to export.", Toast.LENGTH_SHORT).show();
            } else showExportOptions();
        });

        searchNameEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterHistory(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }

    private void loadHistory() {
        historyContainer.removeAllViews();
        TextView totalTextView = findViewById(R.id.totalTextView);

        List<AttendanceRecord> records = dbHelper.getAttendanceRecords();
        currentRecords = records;
        updateButtonStates();

        if (records.isEmpty()) {
            totalTextView.setText("Total: 0");
            TextView empty = new TextView(this);
            empty.setText("No attendance records found.");
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(16);
            empty.setPadding(16, 32, 16, 32);
            historyContainer.addView(empty);
            return;
        }

        totalTextView.setText("Total: " + records.size());

        for (AttendanceRecord record : records) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setPadding(16, 24, 16, 24);
            rowLayout.setGravity(Gravity.CENTER_VERTICAL);

            View statusDot = new View(this);
            int dotSize = 24;
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotParams.setMargins(0, 0, 24, 0);
            statusDot.setLayoutParams(dotParams);

            GradientDrawable circleDrawable = new GradientDrawable();
            circleDrawable.setShape(GradientDrawable.OVAL);
            circleDrawable.setColor(record.isSynced()
                    ? Color.parseColor("#4CAF50")
                    : Color.parseColor("#F44336"));
            statusDot.setBackground(circleDrawable);

            TextView rowText = new TextView(this);
            rowText.setText(String.format(Locale.getDefault(),
                    "%s\nDate: %s\nTime In AM: %s\nTime Out AM: %s\nTime In PM: %s\nTime Out PM: %s",
                    record.getName(), record.getDate(), record.getTimeInAM(),
                    record.getTimeOutAM(), record.getTimeInPM(), record.getTimeOutPM()));
            rowText.setTextSize(16);
            rowText.setTypeface(Typeface.MONOSPACE);
            rowText.setTextColor(Color.DKGRAY);

            rowLayout.addView(statusDot);
            rowLayout.addView(rowText);

            rowLayout.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Delete Entry")
                        .setMessage("Are you sure you want to delete this record?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            dbHelper.deleteAttendanceById(record.getId());
                            loadHistory();
                            Toast.makeText(this, "Entry deleted.", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });

            historyContainer.addView(rowLayout);
        }
    }

    private void updateButtonStates() {
        boolean hasData = currentRecords != null && !currentRecords.isEmpty();
        clearHistoryButton.setEnabled(hasData);
        exportCSVButton.setEnabled(hasData);
        syncButton.setEnabled(hasData && checkInternetConnection());

        int enabledColor = Color.parseColor("#FD7F09");
        int disabledColor = Color.parseColor("#BDBDBD");

        clearHistoryButton.setBackgroundTintList(hasData ? android.content.res.ColorStateList.valueOf(enabledColor) :
                android.content.res.ColorStateList.valueOf(disabledColor));
        exportCSVButton.setBackgroundTintList(hasData ? android.content.res.ColorStateList.valueOf(enabledColor) :
                android.content.res.ColorStateList.valueOf(disabledColor));
        syncButton.setBackgroundTintList(hasData && checkInternetConnection() ?
                android.content.res.ColorStateList.valueOf(enabledColor) :
                android.content.res.ColorStateList.valueOf(disabledColor));
    }

    private void filterHistory(String query) {
        historyContainer.removeAllViews();
        TextView totalTextView = findViewById(R.id.totalTextView);

        if (currentRecords == null || currentRecords.isEmpty()) {
            totalTextView.setText("Total: 0");
            updateButtonStates();
            return;
        }

        List<AttendanceRecord> filtered = new ArrayList<>();
        for (AttendanceRecord record : currentRecords) {
            if (record.getName().toLowerCase().contains(query.toLowerCase())) filtered.add(record);
        }

        totalTextView.setText("Total: " + filtered.size());

        if (filtered.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No matching records found.");
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(16);
            empty.setPadding(16, 32, 16, 32);
            historyContainer.addView(empty);
        } else {
            for (AttendanceRecord record : filtered) {
                TextView row = new TextView(this);
                row.setText(String.format(Locale.getDefault(),
                        "%s\nDate: %s\nTime In AM: %s\nTime Out AM: %s\nTime In PM: %s\nTime Out PM: %s",
                        record.getName(), record.getDate(), record.getTimeInAM(), record.getTimeOutAM(),
                        record.getTimeInPM(), record.getTimeOutPM()));
                row.setTextSize(16);
                row.setTypeface(Typeface.MONOSPACE);
                row.setTextColor(Color.DKGRAY);
                row.setPadding(16, 24, 16, 24);
                historyContainer.addView(row);
            }
        }

        updateButtonStates();
    }

    private void setupNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> {
                    updateButtonStates();
                    syncOfflineDataToFirestore(); // Auto-sync
                });
            }

            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> updateButtonStates());
            }
        };

        cm.registerDefaultNetworkCallback(networkCallback);
        updateButtonStates();
    }

    private boolean checkInternetConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            Network network = cm.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
        }
        return false;
    }

    // --- Firestore Sync with Merge ---
    private void syncOfflineDataToFirestore() {
        if (!checkInternetConnection()) {
            Toast.makeText(this, "No internet connection. Cannot sync.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<AttendanceRecord> unsynced = dbHelper.getUnsyncedRecords();
        if (unsynced.isEmpty()) {
            Toast.makeText(this, "All records are already synced.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        final int total = unsynced.size();
        final int[] successCount = {0};
        final int[] failCount = {0};

        for (AttendanceRecord record : unsynced) {
            // Use getIdHash() to ensure consistent document IDs across devices
            String docId = record.getIdHash();

            firestore.collection("attendance_records")
                    .document(docId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        Map<String, Object> updatedData = record.toMap();

                        if (doc.exists()) {
                            // Merge existing fields to prevent overwriting valid remote data
                            Map<String, Object> existing = doc.getData();
                            if (existing != null) {
                                for (String key : new String[]{"time_in_am", "time_out_am", "time_in_pm", "time_out_pm"}) {
                                    String localVal = record.getField(key);
                                    String remoteVal = (String) existing.get(key);
                                    if ((remoteVal != null && !remoteVal.equals("-")) && (localVal == null || localVal.equals("-"))) {
                                        updatedData.put(key, remoteVal); // keep remote if local is empty
                                    }
                                }
                            }
                        }

                        firestore.collection("attendance_records")
                                .document(docId)
                                .set(updatedData, SetOptions.merge()) // merge to update partial fields
                                .addOnSuccessListener(unused -> markRecordSynced(record, successCount, failCount, total))
                                .addOnFailureListener(e -> markRecordFailed(successCount, failCount, total));
                    })
                    .addOnFailureListener(e -> markRecordFailed(successCount, failCount, total));
        }
    }


    private void markRecordSynced(AttendanceRecord record, int[] successCount, int[] failCount, int total) {
        dbHelper.markAsSynced(record.getId());
        record.setSynced(true);
        successCount[0]++;
        if (successCount[0] + failCount[0] == total) loadHistory();
    }

    private void markRecordFailed(int[] successCount, int[] failCount, int total) {
        failCount[0]++;
        if (successCount[0] + failCount[0] == total) loadHistory();
    }

    // --- CSV export / share methods (same as before) ---
    private void showExportOptions() {
        String[] options = {"Save to Files", "Share via Other Apps"};
        new AlertDialog.Builder(this)
                .setTitle("Export Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) createCSVFile();
                    else shareCSVDirectly();
                })
                .show();
    }
    private void createCSVFile() {
        String section = getSharedPreferences("AttendancePrefs", MODE_PRIVATE)
                .getString("last_section", "Section");
        String safeSection = section.replaceAll("[^a-zA-Z0-9]", "_");
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String fileName = "BSIT_" + safeSection + "_" + currentDate + ".csv";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        createFileLauncher.launch(intent);
    }
    private void writeCSVToUri(Uri uri) {
        StringBuilder data = new StringBuilder();
        data.append("Name,Date,Time In AM,Time Out AM,Time In PM,Time Out PM,Section\n");
        for (AttendanceRecord record : currentRecords) {
            data.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    record.getName(), record.getDate(), record.getTimeInAM(), record.getTimeOutAM(),
                    record.getTimeInPM(), record.getTimeOutPM(), record.getSection()));
        }
        try (java.io.OutputStream outputStream = getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
            writer.write(data.toString());
            writer.flush();
            Toast.makeText(this, "Exported successfully.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    private void shareCSVDirectly() {
        try {
            String section = getSharedPreferences("AttendancePrefs", MODE_PRIVATE)
                    .getString("last_section", "Section");
            String safeSection = section.replaceAll("[^a-zA-Z0-9]", "_");
            String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String fileName = "BSIT_" + safeSection + "_" + currentDate + ".csv";

            File cacheFile = new File(getCacheDir(), fileName);
            try (FileOutputStream fos = new FileOutputStream(cacheFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                writer.write("Name,Date,Time In AM,Time Out AM,Time In PM,Time Out PM,Section\n");
                for (AttendanceRecord record : currentRecords) {
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            record.getName(), record.getDate(), record.getTimeInAM(), record.getTimeOutAM(),
                            record.getTimeInPM(), record.getTimeOutPM(), record.getSection()));
                }
                writer.flush();
            }

            Uri uri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".fileprovider", cacheFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_TITLE, fileName);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share CSV via"));

        } catch (Exception e) {
            Toast.makeText(this, "Failed to share: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    private void applyWindowInsetPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (view, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            view.setPadding(0, topInset, 0, bottomInset);
            return insets;
        });
    }
}
