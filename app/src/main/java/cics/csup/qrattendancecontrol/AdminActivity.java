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
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton; // 1. ADDED: Import for MaterialButton
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration; // 2. ADDED: Import for the listener
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
    // This list holds ALL records from Firestore
    private List<AttendanceRecord> attendanceList;
    private FirebaseFirestore firestore;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Spinner sectionSpinner, daySpinner, monthSpinner, yearSpinner;
    private TextView totalCountText;
    private AdminCacheDBHelper cacheDB;

    // 3. ADDED: To manage the real-time listener
    private ListenerRegistration firestoreListener;
    private EditText searchNameEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        // 4. CHANGED: Initialize adapter and list
        adapter = new AttendanceAdapter(); // Initialize with no list
        recyclerView.setAdapter(adapter);
        attendanceList = new ArrayList<>(); // This is our full, unfiltered list

        firestore = FirebaseFirestore.getInstance();
        cacheDB = new AdminCacheDBHelper(this);

        setupSectionSpinner();
        setupDateFilters();

        // --- ADD THIS ENTIRE BLOCK ---
        searchNameEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterRecords(); // Re-run the filter every time text changes
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // 🔹 Logout button with confirmation dialog
        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            // Create the dialog
            AlertDialog dialog = new AlertDialog.Builder(AdminActivity.this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to log out?")
                    .setPositiveButton("Yes", (d, which) -> {
                        FirebaseAuth.getInstance().signOut();
                        getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                                .edit()
                                .clear()
                                .apply();
                        Toast.makeText(AdminActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(AdminActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Cancel", (dialogInterface, which) -> dialogInterface.dismiss())
                    .create(); // Use .create() instead of .show()

            // Show the dialog
            dialog.show();

            // --- THIS IS THE FIX ---
            // Set the button colors after showing
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.md_theme_error));
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.md_theme_onSurfaceVariant));
        });

        // 5. ADDED: Programmatic click listener for export
        MaterialButton exportButton = findViewById(R.id.button_export_csv);
        exportButton.setOnClickListener(v -> {
            exportToCSV(v); // Call your existing export method
        });

        // 🔹 Load cached or Firestore data
        if (checkInternetConnection()) {
            loadFromFirestoreAndCache();
        } else {
            loadFromCache();
            Toast.makeText(this, "Offline mode: showing cached data.", Toast.LENGTH_SHORT).show();
        }

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (checkInternetConnection()) {
                // The listener is already active, just tell the user
                Toast.makeText(this, "Data is already real-time", Toast.LENGTH_SHORT).show();
            } else {
                loadFromCache();
                Toast.makeText(this, "Still offline: showing cached data.", Toast.LENGTH_SHORT).show();
            }
            swipeRefreshLayout.setRefreshing(false);
        });

        // 6. CHANGED: Use adapter.getCurrentList() to get the item
        adapter.setOnItemLongClickListener((position, view) -> {
            if (position == RecyclerView.NO_POSITION) return;
            // Get the record from the adapter's *current* (filtered) list
            AttendanceRecord record = adapter.getCurrentList().get(position);
            confirmDelete(record);
        });
    }

    // --- Section Spinner Setup (unchanged) ---
    private void setupSectionSpinner() {
        String[] sections = {"ALL SECTIONS", "1A", "1B", "1C", "1D",
                "2A", "2B", "2C", "3A", "3B", "3C",
                "4A", "4B", "4C", "COLSC", "TESTING PURPOSES"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sections);
        sectionSpinner.setAdapter(adapter);
        sectionSpinner.setSelection(0);
        sectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) { filterRecords(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // --- Date Dropdown Setup (unchanged) ---
    private void setupDateFilters() {
        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, generateRange(1, 31, "Day"));
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, generateRange(1, 12, "Month"));
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, generateRange(2023, 2027, "Year"));
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
            firestoreListener.remove(); // Remove old listener if it exists
        }

        firestoreListener = firestore.collection("attendance_records")
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    swipeRefreshLayout.setRefreshing(false); // Stop refresh UI
                    if (e != null) {
                        Toast.makeText(this, "Failed to load data.", Toast.LENGTH_SHORT).show();
                        loadFromCache(); // Fallback to cache
                        return;
                    }

                    if (snapshots != null) {
                        attendanceList.clear(); // Clear the main list
                        cacheDB.clearCache();   // Clear the old cache

                        for (DocumentSnapshot doc : snapshots.getDocuments()) {

                            // --- THIS IS THE FIX ---
                            // Get both fields. New records will have both.
                            String studentID = doc.getString("studentID");
                            String name = doc.getString("name");

                            // Handle old records
                            if (studentID == null && name != null) {
                                // This is an old record where the 'name' field
                                // might be the ID or the Name. Use it for both.
                                studentID = name;
                            } else if (name == null && studentID != null) {
                                // This is an old record where only the ID was saved.
                                // Use the ID as the name for display.
                                name = studentID;
                            }

                            // Get the rest of the data
                            String date = doc.getString("date");
                            String section = doc.getString("section");
                            String inAm = doc.getString("time_in_am");
                            String outAm = doc.getString("time_out_am");
                            String inPm = doc.getString("time_in_pm");
                            String outPm = doc.getString("time_out_pm");

                            // Use the new 9-argument constructor
                            AttendanceRecord record = new AttendanceRecord(
                                    0, // Local ID
                                    name != null ? name : "-",      // Student Name
                                    studentID != null ? studentID : "-", // Student ID
                                    date != null ? date : "-",      // Date
                                    inAm != null ? inAm : "-",      // inAm
                                    outAm != null ? outAm : "-",    // outAm
                                    inPm != null ? inPm : "-",      // inPm
                                    outPm != null ? outPm : "-",    // outPm
                                    section != null ? section : "-" // section
                            );
                            // --- END OF FIX ---

                            record.setSynced(true);
                            attendanceList.add(record); // Add to the main list
                            cacheDB.insertOrUpdate(record); // Update the cache
                        }

                        filterRecords();
                    }
                });
    }

    // 8. CHANGED: Replaced notifyDataSetChanged with filterRecords
    private void loadFromCache() {
        attendanceList.clear();
        attendanceList.addAll(cacheDB.getAllRecords());
        // Filter the list (even if it's from cache) and submit
        filterRecords();
    }

    // 9. CHANGED: This now submits the filtered list to the ListAdapter
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

            // --- ADD THIS BLOCK ---
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

        // This is the correct way to update the ListAdapter
        adapter.submitList(filtered);
        updateTotalCount(filtered);
    }

    // 10. CHANGED: Updated delete logic
    private void confirmDelete(AttendanceRecord record) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Record")
                .setMessage("Delete this record from Firestore and cache?\n\n" + record.getName())
                .setPositiveButton("Delete", (dialog, which) -> {
                    String docId = record.getName().replaceAll("[^a-zA-Z0-9]", "_")
                            + "_" + record.getDate() + "_" + record.getSection();

                    firestore.collection("attendance_records")
                            .document(docId)
                            .delete()
                            .addOnSuccessListener(unused -> {
                                // Delete from cache
                                cacheDB.deleteByNameDateSection(record.getName(), record.getDate(), record.getSection());

                                // The snapshot listener will automatically remove it
                                // from attendanceList, so we just need to re-filter
                                // To be safe, we can remove it manually first
                                attendanceList.remove(record);
                                filterRecords();

                                Toast.makeText(this, "Deleted successfully.", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateTotalCount(List<AttendanceRecord> list) {
        totalCountText.setText("Total Records: " + list.size());
    }

    // --- checkInternetConnection (unchanged) ---
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

    // 11. CHANGED: Export the *filtered* list
    public void exportToCSV(View view) {
        try {
            List<AttendanceRecord> exportList = adapter.getCurrentList();
            if (exportList == null || exportList.isEmpty()) {
                Toast.makeText(this, "No records to export.", Toast.LENGTH_SHORT).show();
                return;
            }

            // --- 2. ADDED: New Dynamic Filename Logic ---
            // Get the currently selected section from the spinner
            String section = sectionSpinner.getSelectedItem().toString();
            if (section.equals("ALL SECTIONS")) {
                section = "All"; // Use "All" if nothing is selected
            }
            String safeSection = section.replaceAll("[^a-zA-Z0-9]", "_");
            String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            // Create the new, smart filename
            String fileName = "Admin_Export_" + safeSection + "_" + currentDate + ".csv";
            // --- End of New Logic ---


            // 3. CHANGED: Use the new dynamic 'fileName'
            File csvFile = new File(getExternalFilesDir(null), fileName);
            FileWriter writer = new FileWriter(csvFile);
            writer.append("Name,Date,Time In AM,Time Out AM,Time In PM,Time Out PM,Section\n");

            for (AttendanceRecord r : exportList) {
                writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        r.getName(), r.getDate(),
                        r.getFieldValue("time_in_am"), // Use safe getter
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

    // 12. ADDED: Clean up the listener when the activity is destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
    }
}