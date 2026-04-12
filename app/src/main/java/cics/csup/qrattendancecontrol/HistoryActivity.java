package cics.csup.qrattendancecontrol;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.annotation.SuppressLint;

import androidx.core.content.ContextCompat;
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
import java.util.Calendar;
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
    private TextView dateFilterButton;
    private Button clearDateFilterButton;
    private String selectedDateFilter;

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
        dateFilterButton = findViewById(R.id.dateFilterButton);
        clearDateFilterButton = findViewById(R.id.clearDateFilterButton);
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
            syncOfflineDataToFirestore(true);
        });

        dateFilterButton.setOnClickListener(v -> {
            final Calendar calendar = Calendar.getInstance();
            DatePickerDialog picker = new DatePickerDialog(
                    HistoryActivity.this,
                    (view, year, month, dayOfMonth) -> {
                        Calendar picked = Calendar.getInstance();
                        picked.set(year, month, dayOfMonth);
                        selectedDateFilter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(picked.getTime());
                        updateDateFilterButtons();
                        loadHistory(searchNameEditText.getText().toString());
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            picker.show();
        });

        clearDateFilterButton.setOnClickListener(v -> {
            selectedDateFilter = null;
            updateDateFilterButtons();
            loadHistory(searchNameEditText.getText().toString());
        });

        updateDateFilterButtons();

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
        if (selectedDateFilter != null && !selectedDateFilter.trim().isEmpty()) {
            List<AttendanceRecord> filteredByDate = new ArrayList<>();
            for (AttendanceRecord record : records) {
                if (selectedDateFilter.equals(record.getDate())) {
                    filteredByDate.add(record);
                }
            }
            records = filteredByDate;
        }
        adapter.setList(records);
        totalTextView.setText("Total: " + records.size());
        updateButtonStates();
    }

    private void updateDateFilterButtons() {
        if (selectedDateFilter == null || selectedDateFilter.trim().isEmpty()) {
            dateFilterButton.setText(getString(R.string.history_filter_date));
            clearDateFilterButton.setEnabled(false);
            clearDateFilterButton.setAlpha(0.6f);
            return;
        }

        dateFilterButton.setText(selectedDateFilter);
        clearDateFilterButton.setEnabled(true);
        clearDateFilterButton.setAlpha(1f);
    }

    private void attachSwipeHandler() {
        // CHANGED: Removed ItemTouchHelper.LEFT, now it only accepts ItemTouchHelper.RIGHT
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

            // Define colors and icon here so they aren't recreated on every draw frame
            private final ColorDrawable deleteBackground = new ColorDrawable(Color.parseColor("#B00020")); // Dark Red
            private final Drawable deleteIcon = ContextCompat.getDrawable(HistoryActivity.this, R.drawable.ic_delete);

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                return false; // We don't want drag-and-drop moving
            }

            // This method handles drawing the colored background and icon underneath the swiped item
            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View itemView = vh.itemView;
                int iconMargin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;

                // dX > 0 means swiping right. Since we only enabled RIGHT in the constructor,
                // we only need to handle this case.
                if (dX > 0) {
                    // 1. Draw the Red Background
                    // Bounds: From the left edge of the item to wherever the swipe is currently (dX)
                    deleteBackground.setBounds(itemView.getLeft(), itemView.getTop(), (int) dX, itemView.getBottom());
                    deleteBackground.draw(c);

                    // 2. Draw the Icon
                    // Bounds: Left aligned with some margin, centered vertically
                    int iconLeft = itemView.getLeft() + iconMargin;
                    int iconRight = itemView.getLeft() + iconMargin + deleteIcon.getIntrinsicWidth();
                    int iconTop = itemView.getTop() + iconMargin;
                    int iconBottom = itemView.getBottom() - iconMargin;
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);

                    // Clip the canvas to ensure the icon doesn't draw outside the swiped area if swiped slowly
                    c.save();
                    c.clipRect(itemView.getLeft(), itemView.getTop(), dX, itemView.getBottom());
                    deleteIcon.draw(c);
                    c.restore();
                }

                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
            }

            // This method is triggered when the swipe gesture is fully completed
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                // direction will always be ItemTouchHelper.RIGHT here
                int pos = vh.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                AttendanceRecord rec = adapter.getItem(pos);

                // Show confirmation dialog before actual deletion
                new AlertDialog.Builder(HistoryActivity.this)
                        .setTitle("Delete Attendance")
                        .setMessage("Are you sure you want to delete this record?\n\n" + "Name: " + rec.getName() + "\nID Number: " + rec.getStudentID() + "\nSection: " + rec.getSection() + "\nDate: " + rec.getDate() + "\n\nThis action cannot be undone.")
                        .setPositiveButton("Yes", (d, w) -> {
                            // Mark hidden in DB
                            dbHelper.setRecordHidden(rec.getId(), true);
                            // Remove from UI adapter visually
                            adapter.removeItemUIOnly(pos);
                            totalTextView.setText("Total: " + adapter.getItemCount());
                            showSnackbar("Attendance Deleted");
                        })
                        // If cancelled, snap the item back to its original position
                        .setNegativeButton("Cancel", (d, w) -> adapter.notifyItemChanged(pos))
                        .setCancelable(false)
                        .show();
            }
        };

        // Attach the helper to the RecyclerView
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
        errorText.setTextColor(ContextCompat.getColor(this, R.color.md_theme_error));
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

    private void syncOfflineDataToFirestore(boolean showFeedback) {
        List<AttendanceRecord> unsynced = dbHelper.getUnsyncedRecords();
        if (unsynced.isEmpty()) {
            if (showFeedback) showSnackbar("All records are already uploaded.");
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
                            if (showFeedback) showSnackbar(uploaded[0] + " records uploaded.");
                        }
                    })
                    .addOnFailureListener(e -> {
                        done[0]++;
                        if (done[0] == total) {
                            loadHistory(searchNameEditText.getText().toString());
                            if (showFeedback) showSnackbar(uploaded[0] + " records uploaded, some failed.");
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
                    syncOfflineDataToFirestore(false);
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
        if (rootView == null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }

        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getColor(R.color.md_theme_secondary))
                .setTextColor(getColor(R.color.white))
                .show();
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
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance, parent, false);
            return new Holder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int pos) {
            AttendanceRecord r = list.get(pos);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(getColor(r.isSynced() ? R.color.status_dot_synced : R.color.status_dot_pending));
            h.syncDot.setBackground(dot);
            h.textSyncStatus.setText(r.isSynced() ? R.string.history_sync_synced : R.string.history_sync_pending);
            h.textSyncStatus.setTextColor(getColor(r.isSynced() ? R.color.green_dark : R.color.md_theme_onSurfaceVariant));

            h.textName.setText(r.getName());
            h.textDate.setText("Date: " + r.getDate());
            h.textSection.setText(r.getSection());
            h.textSection.setTextColor(getColor(R.color.md_theme_onSurface));
            h.textTimeInAM.setText("IN AM: " + r.getFieldValue("time_in_am"));
            h.textTimeOutAM.setText("OUT AM: " + r.getFieldValue("time_out_am"));
            h.textTimeInPM.setText("IN PM: " + r.getFieldValue("time_in_pm"));
            h.textTimeOutPM.setText("OUT PM: " + r.getFieldValue("time_out_pm"));

            h.itemView.setOnLongClickListener(v -> {
                int currentPos = h.getAdapterPosition();
                if (currentPos == RecyclerView.NO_POSITION) return false;

                promptAdminPasswordAndPerform("Delete Attendance (Admin)",
                        "Enter admin password to permanently delete this record.",
                        false, ok -> {
                            if (ok) {
                                dbHelper.deleteAttendanceById(r.getId());
                                list.remove(currentPos);
                                notifyItemRemoved(currentPos);
                                totalTextView.setText("Total: " + list.size());
                                showSnackbar("Record permanently deleted.");
                            } else {
                                notifyItemChanged(currentPos);
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
            View syncDot;
            TextView textSyncStatus;
            TextView textName;
            TextView textSection;
            TextView textDate;
            TextView textTimeInAM;
            TextView textTimeOutAM;
            TextView textTimeInPM;
            TextView textTimeOutPM;

            Holder(@NonNull View item) {
                super(item);
                syncDot = item.findViewById(R.id.syncDot);
                textSyncStatus = item.findViewById(R.id.textSyncStatus);
                textName = item.findViewById(R.id.textName);
                textSection = item.findViewById(R.id.textSection);
                textDate = item.findViewById(R.id.textDate);
                textTimeInAM = item.findViewById(R.id.textTimeInAM);
                textTimeOutAM = item.findViewById(R.id.textTimeOutAM);
                textTimeInPM = item.findViewById(R.id.textTimeInPM);
                textTimeOutPM = item.findViewById(R.id.textTimeOutPM);
            }
        }
    }
}