package cics.csup.qrattendancecontrol;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate; // ADDED: For Theme
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar; // ADDED: For Snackbar
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AdminActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AttendanceAdapter adapter;
    private List<AttendanceRecord> attendanceList;
    private FirebaseFirestore firestore;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Spinner sectionSpinner, daySpinner, monthSpinner, yearSpinner;
    private TextView totalCountText;
    private AdminCacheDBHelper cacheDB;

    private ListenerRegistration firestoreListener;
    private EditText searchNameEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // --- ADDED: Force light mode to fix dark dialogs/spinners ---
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        // ---

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        recyclerView = findViewById(R.id.recyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        sectionSpinner = findViewById(R.id.sectionSpinner);
        daySpinner = findViewById(R.id.daySpinner);
        monthSpinner = findViewById(R.id.monthSpinner);
        yearSpinner = findViewById(R.id.yearSpinner);
        totalCountText = findViewById(R.id.totalCountText);
        searchNameEditText = findViewById(R.id.searchNameEditText);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AttendanceAdapter();
        recyclerView.setAdapter(adapter);
        attendanceList = new ArrayList<>();

        firestore = FirebaseFirestore.getInstance();
        cacheDB = new AdminCacheDBHelper(this);

        setupSectionSpinner();
        setupDateFilters();

        searchNameEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterRecords();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // 🔹 Logout button with confirmation dialog
        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(AdminActivity.this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to log out?")
                    .setPositiveButton("Yes", (d, which) -> {
                        FirebaseAuth.getInstance().signOut();
                        getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                                .edit()
                                .clear()
                                .apply();
                        // CHANGED: Use Snackbar
                        showSnackbar("Logged out successfully");
                        Intent intent = new Intent(AdminActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Cancel", (dialogInterface, which) -> dialogInterface.dismiss())
                    .create();

            dialog.show();

            // Set the button colors after showing
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.md_theme_error));
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.md_theme_onSurfaceVariant));
        });

        MaterialButton exportButton = findViewById(R.id.button_export_csv);
        exportButton.setOnClickListener(v -> {
            exportToCSV(v);
        });

        // 🔹 Load cached or Firestore data
        if (checkInternetConnection()) {
            loadFromFirestoreAndCache();
        } else {
            // CHANGED: Use Snackbar
            showSnackbar("Offline mode: showing cached data.");
        }

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (checkInternetConnection()) {
                // CHANGED: Use Snackbar
                showSnackbar("Data is already real-time");
            } else {
                loadFromCache();
                // CHANGED: Use Snackbar
                showSnackbar("Still offline: showing cached data.");
            }
            swipeRefreshLayout.setRefreshing(false);
        });

        adapter.setOnItemLongClickListener((position, view) -> {
            if (position == RecyclerView.NO_POSITION) return;
            AttendanceRecord record = adapter.getCurrentList().get(position);
            confirmDelete(record);
        });
    }

    // --- Section Spinner Setup ---
    private void setupSectionSpinner() {
        String[] sections = {"ALL SECTIONS", "1A", "1B", "1C", "1D",
                "2A", "2B", "2C", "3A", "3B", "3C",
                "4A", "4B", "4C", "COLSC", "TESTING PURPOSES"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, sections);
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        sectionSpinner.setAdapter(adapter);
        sectionSpinner.setSelection(0);
        sectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) { filterRecords(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // --- Date Dropdown Setup ---
    private void setupDateFilters() {
        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, generateRange(1, 31, "Day"));
        dayAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, generateRange(1, 12, "Month"));
        monthAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, generateRange(2023, 2033, "Year"));
        yearAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        daySpinner.setAdapter(dayAdapter);
        monthSpinner.setAdapter(monthAdapter);
        yearSpinner.setAdapter(yearAdapter);
        AdapterView.OnItemSelectedListener dateListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) { filterRecords(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        daySpinner.setOnItemSelectedListener(dateListener);
        monthSpinner.setOnItemSelectedListener(dateListener);
        yearSpinner.setOnItemSelectedListener(dateListener);
    }

    private List<String> generateRange(int start, int end, String label) {
        List<String> list = new ArrayList<>();
        list.add(label);
        for (int i = start; i <= end; i++) list.add(String.valueOf(i));
        return list;
    }

    // 7. CHANGED: Now uses .addSnapshotListener AND the new 9-argument constructor
    private void loadFromFirestoreAndCache() {
        swipeRefreshLayout.setRefreshing(true);

        if (firestoreListener != null) {
            firestoreListener.remove();
        }

        firestoreListener = firestore.collection("attendance_records")
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    swipeRefreshLayout.setRefreshing(false);
                    if (e != null) {
                        // CHANGED: Use Snackbar
                        showSnackbar("Failed to load data. Showing cache.");
                        loadFromCache(); // Fallback to cache
                        return;
                    }

                    if (snapshots != null) {
                        attendanceList.clear();
                        cacheDB.clearCache();

                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            String studentID = doc.getString("studentID");
                            String name = doc.getString("name");

                            if (studentID == null && name != null) {
                                studentID = name;
                            } else if (name == null && studentID != null) {
                                name = studentID;
                            }

                            String date = doc.getString("date");
                            String section = doc.getString("section");
                            String inAm = doc.getString("time_in_am");
                            String outAm = doc.getString("time_out_am");
                            String inPm = doc.getString("time_in_pm");
                            String outPm = doc.getString("time_out_pm");

                            AttendanceRecord record = new AttendanceRecord(
                                    0,
                                    name != null ? name : "-",
                                    studentID != null ? studentID : "-",
                                    date != null ? date : "-",
                                    inAm != null ? inAm : "-",
                                    outAm != null ? outAm : "-",
                                    inPm != null ? inPm : "-",
                                    outPm != null ? outPm : "-",
                                    section != null ? section : "-"
                            );

                            record.setSynced(true);
                            attendanceList.add(record);
                            cacheDB.insertOrUpdate(record);
                        }
                        filterRecords();
                    }
                });
    }

    private void loadFromCache() {
        attendanceList.clear();
        attendanceList.addAll(cacheDB.getAllRecords());
        filterRecords();
    }

    private void filterRecords() {
        if (attendanceList == null) return;

        String selectedSection = sectionSpinner.getSelectedItem().toString();
        String selectedDay = daySpinner.getSelectedItem().toString();
        String selectedMonth = monthSpinner.getSelectedItem().toString();
        String selectedYear = yearSpinner.getSelectedItem().toString();
        String searchQuery = searchNameEditText.getText().toString().toLowerCase().trim();

        List<AttendanceRecord> filtered = new ArrayList<>();
        for (AttendanceRecord record : attendanceList) {
            boolean matches = true;
            String section = record.getSection() != null ? record.getSection() : "";
            String date = record.getDate() != null ? record.getDate() : "";
            String name = record.getName() != null ? record.getName().toLowerCase() : "";
            String studentID = record.getStudentID() != null ? record.getStudentID().toLowerCase() : "";

            if (!selectedSection.equals("ALL SECTIONS") && !section.trim().equalsIgnoreCase(selectedSection)) matches = false;

            if (matches && !searchQuery.isEmpty()) {
                if (!name.contains(searchQuery) && !studentID.contains(searchQuery)) {
                    matches = false;
                }
            }

            if (matches && !selectedYear.equals("Year")) {
                if (!date.startsWith(selectedYear)) matches = false;
            }

            if (matches && !selectedMonth.equals("Month")) {
                String monthNum = String.format(Locale.getDefault(), "-%02d-", Integer.parseInt(selectedMonth));
                if (!date.contains(monthNum)) matches = false;
            }

            if (matches && !selectedDay.equals("Day")) {
                String dayNum = String.format(Locale.getDefault(), "-%02d", Integer.parseInt(selectedDay));
                if (!date.endsWith(dayNum)) matches = false;
            }

            if (matches) filtered.add(record);
        }

        adapter.submitList(filtered);
        updateTotalCount(filtered);
    }

    private void confirmDelete(AttendanceRecord record) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete Record")
                .setMessage("Delete this record from Firestore and cache?\n\n" + record.getName())
                .setPositiveButton("Delete", (d, which) -> {
                    String docId = record.getIdHash();

                    firestore.collection("attendance_records")
                            .document(docId)
                            .delete()
                            .addOnSuccessListener(unused -> {
                                cacheDB.deleteByNameDateSection(record.getStudentID(), record.getDate(), record.getSection());
                                attendanceList.remove(record);
                                filterRecords();
                                // CHANGED: Use Snackbar
                                showSnackbar("Deleted successfully.");
                            })
                            .addOnFailureListener(e -> {
                                // CHANGED: Use Snackbar
                                showSnackbar("Delete failed: " + e.getMessage());
                            });
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        // Set button colors
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.md_theme_error));
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.md_theme_onSurfaceVariant));
    }

    private void updateTotalCount(List<AttendanceRecord> list) {
        totalCountText.setText("Total Records: " + list.size());
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

    public void exportToCSV(View view) {
        try {
            List<AttendanceRecord> exportList = adapter.getCurrentList();
            if (exportList == null || exportList.isEmpty()) {
                // CHANGED: Use Snackbar
                showSnackbar("No records to export.");
                return;
            }

            String section = sectionSpinner.getSelectedItem().toString();
            if (section.equals("ALL SECTIONS")) {
                section = "All";
            }
            String safeSection = section.replaceAll("[^a-zA-Z0-9]", "_");
            String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            String fileName = "Admin_Export_" + safeSection + "_" + currentDate + ".csv";

            File csvFile = new File(getExternalFilesDir(null), fileName);
            FileWriter writer = new FileWriter(csvFile);
            writer.append("Name,Date,Time In AM,Time Out AM,Time In PM,Time Out PM,Section\n");

            for (AttendanceRecord r : exportList) {
                writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        r.getName(), r.getDate(),
                        r.getFieldValue("time_in_am"),
                        r.getFieldValue("time_out_am"),
                        r.getFieldValue("time_in_pm"),
                        r.getFieldValue("time_out_pm"),
                        r.getSection()));
            }

            writer.flush();
            writer.close();

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM,
                    FileProvider.getUriForFile(this, getPackageName() + ".provider", csvFile));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share CSV via"));

        } catch (IOException e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
    }

    // ------------------- UI HELPER -------------------

    /**
     * Displays a non-stacking Snackbar message.
     */
    private void showSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show();
        } else {
            // Fallback
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }
}