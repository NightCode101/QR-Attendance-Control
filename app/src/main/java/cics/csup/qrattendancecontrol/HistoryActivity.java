package cics.csup.qrattendancecontrol;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.annotation.SuppressLint;

import androidx.core.content.FileProvider;
import androidx.core.view.WindowInsetsCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.android.material.snackbar.Snackbar;

import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class HistoryActivity extends AppCompatActivity {

    private AttendanceDBHelper dbHelper;
    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private Button clearHistoryButton, exportCSVButton, syncButton;
    private EditText searchNameEditText;
    private TextView totalTextView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ConnectivityManager.NetworkCallback networkCallback;
    private FirebaseFirestore firestore;

    private static final String ADMIN_PREFS = "AdminPrefs";
    private static final String ADMIN_KEY = "admin_password";

    private final ActivityResultLauncher<Intent> createFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) writeCSVToUri(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        applyWindowInsetPadding();

        dbHelper = new AttendanceDBHelper(this);
        firestore = FirebaseFirestore.getInstance();

        recyclerView = findViewById(R.id.recyclerViewHistory);
        clearHistoryButton = findViewById(R.id.clearHistoryButton);
        exportCSVButton = findViewById(R.id.exportCSVButton);
        syncButton = findViewById(R.id.syncButton);
        searchNameEditText = findViewById(R.id.searchNameEditText);
        totalTextView = findViewById(R.id.totalTextView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        attachSwipeHandler();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadHistory(searchNameEditText.getText().toString());
            swipeRefreshLayout.setRefreshing(false);
        });

        loadHistory(null);
        setupNetworkCallback();
        fetchAndCacheAdminPassword();

        syncButton.setOnClickListener(v -> {
            if (!checkInternetConnection()) {
                showSnackbar("No internet connection.");
                return;
            }
            syncOfflineDataToFirestore();
        });

        clearHistoryButton.setOnClickListener(v -> {
            if (adapter.getItemCount() == 0) {
                showSnackbar("No attendance to clear.");
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Delete Attendance")
                    .setMessage("Are you sure you want to delete all attendance record?\n\nThis action cannot be undone.")
                    .setPositiveButton("Delete", (d, w) -> {
                        dbHelper.hideAllVisibleRecords();
                        loadHistory(searchNameEditText.getText().toString());
                        showSnackbar("All attendance records are deleted");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        clearHistoryButton.setOnLongClickListener(v -> {
            promptAdminPasswordAndPerform("Admin: Delete All Records",
                    "Enter admin password to delete all local attendance records.\n\nThis action cannot be undone.",
                    true, ok -> {
                        if (ok) {
                            dbHelper.clearAllAttendance();
                            loadHistory(null);
                            showSnackbar("All local attendance records are deleted.");
                        }
                    });
            return true;
        });

        exportCSVButton.setOnClickListener(v -> {
            if (adapter.getItemCount() == 0) {
                showSnackbar("No attendance data to export.");
            } else {
                showExportOptions();
            }
        });

        searchNameEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadHistory(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory(searchNameEditText.getText().toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
    }

    private void loadHistory(String nameFilter) {
        List<AttendanceRecord> records = dbHelper.getVisibleAttendanceRecords(nameFilter);
        adapter.setList(records);
        totalTextView.setText("Total: " + records.size());
        updateButtonStates();
    }

    private void attachSwipeHandler() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) { return false; }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                AttendanceRecord rec = adapter.getItem(pos);

                new AlertDialog.Builder(HistoryActivity.this)
                        .setTitle("Delete Attendance")
                        .setMessage("Are you sure you want to delete this record?\n\n" + "Name: " + rec.getName() + "\nID Number: " + rec.getStudentID() + "\nSection: " + rec.getSection() + "\nDate: " + rec.getDate() +"\n\nThis action cannot be undone.")
                        .setPositiveButton("Yes", (d, w) -> {
                            dbHelper.setRecordHidden(rec.getId(), true);
                            adapter.removeItemUIOnly(pos);
                            totalTextView.setText("Total: " + adapter.getItemCount());
                            showSnackbar("Attendance Deleted");
                        })
                        .setNegativeButton("Cancel", (d, w) -> adapter.notifyItemChanged(pos))
                        .setCancelable(false)
                        .show();
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    private void fetchAndCacheAdminPassword() {
        FirebaseFirestore.getInstance().collection("config").document("admin")
                .get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    if (doc.exists()) {
                        String pw = doc.getString("password");
                        if (pw != null && !pw.isEmpty()) {
                            getSharedPreferences(ADMIN_PREFS, MODE_PRIVATE).edit().putString(ADMIN_KEY, pw).apply();
                            Log.d("ADMIN", "Admin password cached.");
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e("ADMIN", "Failed to fetch admin password: " + e.getMessage()));
    }

    private void promptAdminPasswordAndPerform(String title, String message, boolean isClear, Consumer<Boolean> callback) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 32, 48, 8);

        TextView tv = new TextView(this);
        tv.setText(message);
        container.addView(tv);

        EditText input = new EditText(this);
        input.setHint("Admin password");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        container.addView(input);

        TextView errorText = new TextView(this);
        errorText.setTextColor(getResources().getColor(R.color.md_theme_error));
        errorText.setVisibility(View.GONE);
        container.addView(errorText);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button ok = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                String entered = input.getText().toString();
                SharedPreferences prefs = getSharedPreferences(ADMIN_PREFS, MODE_PRIVATE);
                String cached = prefs.getString(ADMIN_KEY, null);
                if (cached == null) {
                    errorText.setText("Connect to internet to fetch password.");
                    errorText.setVisibility(View.VISIBLE);
                    fetchAndCacheAdminPassword();
                    return;
                }
                if (!entered.equals(cached)) {
                    errorText.setText("Incorrect password.");
                    errorText.setVisibility(View.VISIBLE);
                    return;
                }
                dialog.dismiss();
                callback.accept(true);
            });
        });
        dialog.show();
    }

    private void syncOfflineDataToFirestore() {
        List<AttendanceRecord> unsynced = dbHelper.getUnsyncedRecords();
        if (unsynced.isEmpty()) {
            showSnackbar("All records are already uploaded.");
            return;
        }

        final int total = unsynced.size();
        final int[] done = {0}, uploaded = {0};

        for (AttendanceRecord record : unsynced) {
            String docId = record.getIdHash();
            Map<String, Object> data = record.toMap();

            firestore.collection("attendance_records").document(docId)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(a -> {
                        dbHelper.markAsSynced(record.getId());
                        uploaded[0]++; done[0]++;
                        if (done[0] == total) {
                            loadHistory(searchNameEditText.getText().toString());
                            showSnackbar(uploaded[0] + " records uploaded.");
                        }
                    })
                    .addOnFailureListener(e -> {
                        done[0]++;
                        if (done[0] == total) {
                            loadHistory(searchNameEditText.getText().toString());
                            showSnackbar(uploaded[0] + " records uploaded, some failed.");
                        }
                        Log.e("HistorySync", "Failed to sync record: " + docId, e);
                    });
        }
    }

    private boolean checkInternetConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            Network net = cm.getActiveNetwork();
            if (net != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
        }
        return false;
    }

    private void setupNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                runOnUiThread(() -> {
                    updateButtonStates();
                    syncOfflineDataToFirestore();
                    fetchAndCacheAdminPassword();
                });
            }
            @Override public void onLost(Network network) { runOnUiThread(() -> updateButtonStates()); }
        };
        cm.registerDefaultNetworkCallback(networkCallback);
        updateButtonStates();
    }

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
        String section = getSharedPreferences("AttendancePrefs", MODE_PRIVATE).getString("last_section", "Section");
        String safeSection = section.replaceAll("[^a-zA-Z0-9]", "_");
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String fileName = "BSIT_" + safeSection + "_" + currentDate + ".csv";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        createFileLauncher.launch(intent);
    }

    private void writeCSVToUri(Uri uri) {
        try (OutputStreamWriter writer = new OutputStreamWriter(getContentResolver().openOutputStream(uri))) {
            writer.write("Name,Date,Section,Time In AM,Time Out AM,Time In PM,Time Out PM\n");
            for (AttendanceRecord record : adapter.getCurrentList()) {
                writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        record.getName(), record.getDate(), record.getSection(),
                        record.getFieldValue("time_in_am"),
                        record.getFieldValue("time_out_am"),
                        record.getFieldValue("time_in_pm"),
                        record.getFieldValue("time_out_pm")));
            }
            writer.flush();
            Toast.makeText(this, "Exported successfully.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareCSVDirectly() {
        try {
            String section = getSharedPreferences("AttendancePrefs", MODE_PRIVATE).getString("last_section", "Section");
            String safeSection = section.replaceAll("[^a-zA-Z0-9]", "_");
            String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String fileName = "BSIT_" + safeSection + "_" + currentDate + ".csv";

            File cacheFile = new File(getCacheDir(), fileName);
            try (FileOutputStream fos = new FileOutputStream(cacheFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                writer.write("Name,Date,Section,Time In AM,Time Out AM,Time In PM,Time Out PM\n");
                for (AttendanceRecord r : adapter.getCurrentList()) {
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            r.getName(), r.getDate(), r.getSection(),
                            r.getFieldValue("time_in_am"),
                            r.getFieldValue("time_out_am"),
                            r.getFieldValue("time_in_pm"),
                            r.getFieldValue("time_out_pm")));
                }
            }

            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", cacheFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share CSV"));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show();
        else Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void applyWindowInsetPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
    }

    private void updateButtonStates() {
        boolean hasInternet = checkInternetConnection();
        syncButton.setEnabled(hasInternet);
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
        private final List<AttendanceRecord> list;
        HistoryAdapter(List<AttendanceRecord> initial) { this.list = new ArrayList<>(initial); }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(16, 24, 16, 24);
            row.setGravity(Gravity.CENTER_VERTICAL);

            View dot = new View(parent.getContext());
            int size = 24;
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(size, size);
            dotParams.setMargins(0, 0, 24, 0);
            dot.setLayoutParams(dotParams);

            TextView text = new TextView(parent.getContext());
            text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            text.setTextSize(15);
            text.setTypeface(Typeface.MONOSPACE);
            row.addView(dot);
            row.addView(text);
            return new Holder(row, dot, text);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int pos) {
            AttendanceRecord r = list.get(pos);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(r.isSynced() ? getResources().getColor(R.color.green_dark) : getResources().getColor(R.color.md_theme_error));
            h.dot.setBackground(circle);

            h.text.setText(String.format(Locale.getDefault(),
                    "%s\nDate: %s\nSection: %s\nTime In AM: %s\nTime Out AM: %s\nTime In PM: %s\nTime Out PM: %s",
                    r.getName(), r.getDate(), r.getSection(),
                    r.getFieldValue("time_in_am"),
                    r.getFieldValue("time_out_am"),
                    r.getFieldValue("time_in_pm"),
                    r.getFieldValue("time_out_pm")));

            h.itemView.setOnLongClickListener(v -> {
                promptAdminPasswordAndPerform("Delete Attendance (Admin)",
                        "Enter admin password to permanently delete this record.",
                        false, ok -> {
                            if (ok) {
                                dbHelper.deleteAttendanceById(r.getId());
                                list.remove(pos);
                                notifyItemRemoved(pos);
                                totalTextView.setText("Total: " + list.size());
                                showSnackbar("Record permanently deleted.");
                            } else {
                                notifyItemChanged(pos);
                            }
                        });
                return true;
            });
        }

        @Override public int getItemCount() { return list.size(); }
        AttendanceRecord getItem(int pos) { return list.get(pos); }

        @SuppressLint("NotifyDataSetChanged")
        void setList(List<AttendanceRecord> recs) {
            list.clear();
            if (recs != null) list.addAll(recs);
            notifyDataSetChanged();
            updateButtonStates();
        }

        List<AttendanceRecord> getCurrentList() { return new ArrayList<>(list); }

        void removeItemUIOnly(int pos) {
            if (pos >= 0 && pos < list.size()) {
                list.remove(pos);
                notifyItemRemoved(pos);
            }
        }

        class Holder extends RecyclerView.ViewHolder {
            View dot; TextView text;
            Holder(@NonNull View item, View dot, TextView text) { super(item); this.dot = dot; this.text = text; }
        }
    }
}