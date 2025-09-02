package cics.csup.qrattendancecontrol;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Collections;

public class AdminActivity extends AppCompatActivity {

    private final Set<String> sectionSet = new HashSet<>();
    private final List<AttendanceRecord> allRecords = new ArrayList<>();
    private FirebaseFirestore firestore;
    private LinearLayout sectionButtonContainer;
    private LinearLayout adminAttendanceContainer;
    private EditText searchNameEditText;
    private Button exportCsvButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private String currentSection = null;
    private Spinner daySpinner, monthSpinner, yearSpinner;
    private Calendar selectedDate = null;
    private TextView dateFilterTextView;
    private TextView sectionTotalTextView;

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
        getWindow().setNavigationBarColor(Color.parseColor("#121212"));
        getWindow().setStatusBarColor(Color.parseColor("#121212"));

        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(0);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Find views once
        daySpinner = findViewById(R.id.daySpinner);
        monthSpinner = findViewById(R.id.monthSpinner);
        yearSpinner = findViewById(R.id.yearSpinner);
        dateFilterTextView = findViewById(R.id.dateFilterTextView);
        sectionButtonContainer = findViewById(R.id.sectionButtonContainer);
        adminAttendanceContainer = findViewById(R.id.adminDataContainer);
        searchNameEditText = findViewById(R.id.searchNameEditText);
        exportCsvButton = findViewById(R.id.exportCsvButton);
        swipeRefreshLayout = findViewById(R.id.adminSwipeRefreshLayout);
        firestore = FirebaseFirestore.getInstance();

        // Populate spinners
        setupDateSpinners();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String ADMIN_UID = "KCKVGF5sJ7TfGWKAl0fRJziE4Ja2";

        if (user == null || !user.getUid().equals(ADMIN_UID)) {
            Toast.makeText(this, "Access denied. Not an admin.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Listeners
        searchNameEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (currentSection != null) showRecordsForSection(currentSection);
            }
        });

        swipeRefreshLayout.setOnRefreshListener(this::loadAllRecords);
        exportCsvButton.setOnClickListener(v -> createCSVFile());

        AdapterView.OnItemSelectedListener dateChangeListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateDateFilterLabel();
                if (currentSection != null) showRecordsForSection(currentSection);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        sectionTotalTextView = findViewById(R.id.sectionTotalTextView);

        daySpinner.setOnItemSelectedListener(dateChangeListener);
        monthSpinner.setOnItemSelectedListener(dateChangeListener);
        yearSpinner.setOnItemSelectedListener(dateChangeListener);

        updateDateFilterLabel();
        loadAllRecords();
    }

    private void setupDateSpinners() {
        List<String> days = new ArrayList<>();
        days.add("Day");
        for (int i = 1; i <= 31; i++) days.add(String.valueOf(i));
        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, days);
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        daySpinner.setAdapter(dayAdapter);

        List<String> months = new ArrayList<>();
        months.add("Month");
        Collections.addAll(months, "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December");
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, months);
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        monthSpinner.setAdapter(monthAdapter);

        List<String> years = new ArrayList<>();
        years.add("Year");
        for (int i = 2024; i <= 2050; i++) years.add(String.valueOf(i));
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, years);
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        yearSpinner.setAdapter(yearAdapter);
    }

    private void updateDateFilterLabel() {
        if (daySpinner == null || monthSpinner == null || yearSpinner == null || dateFilterTextView == null) return;

        String day = daySpinner.getSelectedItem() != null ? daySpinner.getSelectedItem().toString() : "";
        String month = monthSpinner.getSelectedItem() != null ? monthSpinner.getSelectedItem().toString() : "";
        String year = yearSpinner.getSelectedItem() != null ? yearSpinner.getSelectedItem().toString() : "";

        if (!"Day".equals(day) && !"Month".equals(month) && !"Year".equals(year)) {
            dateFilterTextView.setText(String.format("Showing results for: %s %s, %s", month, day, year));
            selectedDate = Calendar.getInstance();
            selectedDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(day));
            // Calendar months are zero-based
            int monthIndex = monthSpinner.getSelectedItemPosition() - 1; // subtract 1 because "Month" placeholder is first
            selectedDate.set(Calendar.MONTH, monthIndex);
            selectedDate.set(Calendar.YEAR, Integer.parseInt(year));
        } else {
            dateFilterTextView.setText("No date selected");
            selectedDate = null;
        }
    }

    private void loadAllRecords() {
        firestore.collection("attendance_records").get()
                .addOnSuccessListener(querySnapshots -> {
                    sectionSet.clear();
                    allRecords.clear();

                    for (QueryDocumentSnapshot doc : querySnapshots) {
                        String name = doc.getString("name");
                        String section = doc.getString("section");
                        String rawDate = doc.getString("date");
                        String formattedDate = rawDate;

                        try {
                            Date parsed = new SimpleDateFormat("MMMM d, yyyy", Locale.US).parse(rawDate);
                            formattedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed);
                        } catch (Exception e) {
                            Log.e("DateParse", "Error parsing date: " + rawDate, e);
                        }

                        String timeInAM = doc.getString("time_in_am");
                        String timeOutAM = doc.getString("time_out_am");
                        String timeInPM = doc.getString("time_in_pm");
                        String timeOutPM = doc.getString("time_out_pm");

                        if (name != null && section != null && formattedDate != null) {
                            section = section.trim().toUpperCase();

                            AttendanceRecord record = new AttendanceRecord(
                                    0,
                                    name,
                                    formattedDate, // <-- use the formatted date here
                                    timeInAM != null ? timeInAM : "-",
                                    timeOutAM != null ? timeOutAM : "-",
                                    timeInPM != null ? timeInPM : "-",
                                    timeOutPM != null ? timeOutPM : "-",
                                    section
                            );

                            allRecords.add(record);
                            sectionSet.add(section);
                        }
                    }

                    swipeRefreshLayout.setRefreshing(false);
                    renderSectionButtons();
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(this, "Failed to load attendance records.", Toast.LENGTH_SHORT).show();
                });
    }

    private void renderSectionButtons() {
        sectionButtonContainer.removeAllViews();
        TextView sectionTotalTextView = findViewById(R.id.sectionTotalTextView); // make sure this TextView exists in layout

        List<String> predefinedSections = Arrays.asList(
                "1A", "1B", "1C", "1D",
                "2A", "2B", "2C",
                "3A", "3B", "3C",
                "4A", "4B", "4C",
                "COLSC", "TESTING PURPOSES"
        );

        for (String section : predefinedSections) {
            Button btn = new Button(this);
            btn.setText(section);
            btn.setAllCaps(false);
            btn.setTextColor(getColor(R.color.white));
            btn.setPadding(32, 20, 32, 20);
            btn.setBackgroundTintList(ColorStateList.valueOf(
                    section.equals(currentSection)
                            ? getColor(R.color.secondary_accent)
                            : getColor(R.color.primary_accent)));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 24, 0);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> {
                currentSection = section;
                renderSectionButtons(); // re-style buttons
                showRecordsForSection(section); // refresh list

                // ✅ Count matching records
                int count = 0;
                for (AttendanceRecord record : allRecords) {
                    if (record.getSection().equalsIgnoreCase(section)) {
                        count++;
                    }
                }

                sectionTotalTextView.setText("Total: " + count);
            });

            sectionButtonContainer.addView(btn);
        }

        if (currentSection == null) {
            currentSection = predefinedSections.get(0);
            renderSectionButtons();
            showRecordsForSection(currentSection);

            // ✅ Show total for default section
            int count = 0;
            for (AttendanceRecord record : allRecords) {
                if (record.getSection().equalsIgnoreCase(currentSection)) {
                    count++;
                }
            }
            sectionTotalTextView.setText("Total: " + count);
        }
    }

    private void showRecordsForSection(String section) {
        adminAttendanceContainer.removeAllViews();
        String query = searchNameEditText.getText().toString().toLowerCase(Locale.getDefault());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String selectedDateString = selectedDate != null ? sdf.format(selectedDate.getTime()) : null;

        List<AttendanceRecord> filtered = new ArrayList<>();
        for (AttendanceRecord record : allRecords) {
            boolean nameMatches = query.isEmpty() || record.getName().toLowerCase().contains(query);
            boolean sectionMatches = record.getSection().equalsIgnoreCase(section);
            boolean dateMatches = selectedDateString == null || selectedDateString.equals(record.getDate());

            if (sectionMatches && nameMatches && dateMatches) {
                filtered.add(record);
            }
        }

        if (filtered.isEmpty()) {
            showEmptyMessage(section);
            return;
        }
        sectionTotalTextView.setText("Total in this section: " + filtered.size());

        for (AttendanceRecord record : filtered) {
            addRecordToUI(record);
        }
    }

    private void showEmptyMessage(String section) {
        TextView empty = new TextView(this);
        empty.setText("No records found for " + section);
        empty.setGravity(Gravity.CENTER);
        empty.setTextColor(getColor(R.color.text_secondary));
        empty.setPadding(16, 24, 16, 24);
        adminAttendanceContainer.addView(empty);
    }

    private void addRecordToUI(AttendanceRecord record) {
        TextView row = new TextView(this);

        String displayText = record.getName() + "\n"
                + "Date: " + record.getDate() + "\n"
                + "Time In (AM): " + record.getTimeInAM() + "\n"
                + "Time Out (AM): " + record.getTimeOutAM() + "\n"
                + "Time In (PM): " + record.getTimeInPM() + "\n"
                + "Time Out (PM): " + record.getTimeOutPM();

        row.setText(displayText);
        row.setTextSize(16);
        row.setTypeface(Typeface.MONOSPACE);
        row.setTextColor(getColor(R.color.text_primary));
        row.setBackgroundColor(getColor(R.color.card_background));
        row.setPadding(24, 24, 24, 24);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, 16);
        row.setLayoutParams(rowParams);

        row.setOnLongClickListener(v -> {
            showDeleteDialog(record);
            return true;
        });

        adminAttendanceContainer.addView(row);
    }

    private void showDeleteDialog(AttendanceRecord record) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Entry")
                .setMessage("Are you sure you want to delete this record?\n\n" + record.getName())
                .setPositiveButton("Delete", (dialog, which) -> confirmDeleteWithUndo(record))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteWithUndo(AttendanceRecord record) {
        allRecords.remove(record);
        showRecordsForSection(currentSection);

        Snackbar snackbar = Snackbar.make(
                findViewById(android.R.id.content),
                "Record deleted.",
                Snackbar.LENGTH_LONG
        );

        snackbar.setAction("Undo", v -> {
            allRecords.add(record);
            showRecordsForSection(currentSection);
        });

        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                if (event != DISMISS_EVENT_ACTION) {
                    firestore.collection("attendance_records").document(record.getIdHash())
                            .delete()
                            .addOnFailureListener(e ->
                                    Toast.makeText(AdminActivity.this, "Failed to delete from Firestore.", Toast.LENGTH_SHORT).show()
                            );
                }
            }
        });

        snackbar.show();
    }

    private void createCSVFile() {
        if (currentSection == null) {
            Toast.makeText(this, "No section selected", Toast.LENGTH_SHORT).show();
            return;
        }

        // Make section safe for filenames
        String safeSection = currentSection.replaceAll("[^a-zA-Z0-9]", "_");

        // Use selected date if available, otherwise today's date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String datePart = (selectedDate != null) ? sdf.format(selectedDate.getTime()) : sdf.format(new Date());

        // Final filename format
        String fileName = "BSIT_" + safeSection + "_" + datePart + ".csv";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        createFileLauncher.launch(intent);
    }

    private void writeCSVToUri(Uri uri) {
        if (currentSection == null) {
            Toast.makeText(this, "No section selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder data = new StringBuilder();
        data.append("Name,Date,Time In AM,Time Out AM,Time In PM,Time Out PM,Section\n");

        // Build export list based on section + date filter
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String selectedDateString = selectedDate != null ? sdf.format(selectedDate.getTime()) : null;

        List<AttendanceRecord> exportList = new ArrayList<>();
        for (AttendanceRecord record : allRecords) {
            boolean sectionMatches = record.getSection().equalsIgnoreCase(currentSection);
            boolean dateMatches = (selectedDateString == null) || selectedDateString.equals(record.getDate());

            if (sectionMatches && dateMatches) {
                exportList.add(record);
            }
        }

        if (exportList.isEmpty()) {
            Toast.makeText(this, "No records found for export.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add records to CSV
        for (AttendanceRecord record : exportList) {
            data.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    record.getName(),
                    record.getDate(),
                    record.getTimeInAM(),
                    record.getTimeOutAM(),
                    record.getTimeInPM(),
                    record.getTimeOutPM(),
                    record.getSection()));
        }

        String fileName;
        if (selectedDateString != null) {
            fileName = "BSIT_" + currentSection + "_" + selectedDateString + ".csv";
        } else {
            String today = sdf.format(new Date());
            fileName = "BSIT_" + currentSection + "_" + today + ".csv";
        }

        try (OutputStream outputStream = getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {

            writer.write(data.toString());
            writer.flush();
            Toast.makeText(this, "Export successful: " + fileName, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}
