package cics.csup.qrattendancecontrol;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private AttendanceDBHelper dbHelper;
    private LinearLayout historyContainer;
    private Button clearHistoryButton;
    private Button exportCSVButton;
    private List<AttendanceRecord> currentRecords;

    private final ActivityResultLauncher<Intent> createFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        writeCSVToUri(uri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        applyWindowInsetPadding();

        dbHelper = new AttendanceDBHelper(this);
        historyContainer = findViewById(R.id.historyContainer);
        clearHistoryButton = findViewById(R.id.clearHistoryButton);
        exportCSVButton = findViewById(R.id.exportCSVButton);

        loadHistory();

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
            } else {
                showExportOptions();
            }
        });
    }

    private void loadHistory() {
        historyContainer.removeAllViews();
        currentRecords = dbHelper.getAttendanceRecords();

        if (currentRecords.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No attendance records found.");
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(16);
            empty.setPadding(16, 32, 16, 32);
            historyContainer.addView(empty);
            return;
        }

        for (AttendanceRecord record : currentRecords) {
            TextView row = new TextView(this);
            row.setText(String.format(Locale.getDefault(),
                    "%s\nDate: %s\nTime In: %s\nTime Out: %s",
                    record.getName(),
                    record.getDate(),
                    record.getTimeIn(),
                    record.getTimeOut() != null ? record.getTimeOut() : "-"));

            row.setTextSize(16);
            row.setTypeface(Typeface.MONOSPACE);
            row.setTextColor(Color.DKGRAY);
            row.setPadding(16, 24, 16, 24);

            row.setOnLongClickListener(v -> {
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

            historyContainer.addView(row);
        }
    }

    private void showExportOptions() {
        String[] options = {"Save to Files", "Share via Other Apps"};
        new AlertDialog.Builder(this)
                .setTitle("Export Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        createCSVFile();
                    } else {
                        shareCSVDirectly();
                    }
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
        data.append("Name,Date,Time In,Time Out\n");

        for (AttendanceRecord record : currentRecords) {
            data.append("\"").append(record.getName()).append("\",")
                    .append("\"").append(record.getDate()).append("\",")
                    .append("\"").append(record.getTimeIn()).append("\",")
                    .append("\"").append(record.getTimeOut() != null ? record.getTimeOut() : "-").append("\"\n");
        }

        try (OutputStream outputStream = getContentResolver().openOutputStream(uri);
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
            // Generate matching filename
            String section = getSharedPreferences("AttendancePrefs", MODE_PRIVATE)
                    .getString("last_section", "Section");
            String safeSection = section.replaceAll("[^a-zA-Z0-9]", "_");
            String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String fileName = "BSIT_" + safeSection + "_" + currentDate + ".csv";

            // Create file in cache directory with that name
            File cacheFile = new File(getCacheDir(), fileName);
            try (FileOutputStream fos = new FileOutputStream(cacheFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos)) {

                writer.write("Name,Date,Time In,Time Out\n");
                for (AttendanceRecord record : currentRecords) {
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            record.getName(),
                            record.getDate(),
                            record.getTimeIn(),
                            record.getTimeOut() != null ? record.getTimeOut() : "-"));
                }
                writer.flush();
            }

            // Share the CSV file via other apps
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
