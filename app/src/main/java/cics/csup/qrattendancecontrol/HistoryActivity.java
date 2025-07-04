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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

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
                    .setTitle(getString(R.string.clear_history_title))
                    .setMessage(getString(R.string.confirm_clear_history))
                    .setPositiveButton(getString(R.string.yes), (dialog, which) -> {
                        dbHelper.clearAllAttendance();
                        loadHistory();
                        Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(getString(R.string.no), null)
                    .show();
        });

        exportCSVButton.setText(getString(R.string.export_to_csv));
        exportCSVButton.setOnClickListener(v -> {
            if (currentRecords == null || currentRecords.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_data_export), Toast.LENGTH_SHORT).show();
            } else {
                createCSVFile();
            }
        });
    }

    private void loadHistory() {
        historyContainer.removeAllViews();
        currentRecords = dbHelper.getAttendanceRecords();

        if (currentRecords.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.no_attendance_found));
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(16);
            empty.setPadding(16, 32, 16, 32);
            historyContainer.addView(empty);
            return;
        }

        for (AttendanceRecord record : currentRecords) {
            TextView row = new TextView(this);
            row.setText(getString(R.string.record_format,
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
                        .setTitle(getString(R.string.delete_entry_title))
                        .setMessage(getString(R.string.confirm_delete_entry))
                        .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                            dbHelper.deleteAttendanceById(record.getId());
                            loadHistory();
                            Toast.makeText(this, getString(R.string.entry_deleted), Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show();
                return true;
            });

            historyContainer.addView(row);
        }
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
        if (currentRecords == null || currentRecords.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_data_export), Toast.LENGTH_SHORT).show();
            return;
        }

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
            Toast.makeText(this, getString(R.string.export_success, uri.getPath()), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_LONG).show();
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
