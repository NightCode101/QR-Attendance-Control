package cics.csup.qrattendancecontrol;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AbsentCheckerActivity extends AppCompatActivity {

    private static final String TAG = "AbsentChecker";
    private AdminCacheDBHelper db;
    private FirebaseFirestore firestore;
    private ConfigHelper configHelper;
    private AnalyticsManager analyticsManager;

    private RecyclerView recyclerView;
    private AbsentStudentAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Spinner sectionSpinner;
    private TextView dateFilterButton, absentCountText, masterlistStatusBanner;
    private EditText searchEditText;
    private RadioGroup sessionRadioGroup;
    private MaterialButton clearDateFilterButton, exportButton;

    private String selectedDate;
    private List<AdminCacheDBHelper.StudentML> filteredMasterlist = new ArrayList<>();
    private List<AttendanceRecord> attendanceRecords = new ArrayList<>();

    private final ActivityResultLauncher<Intent> pickCsvLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) importMasterlistFromUri(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_absent_checker);

        db = new AdminCacheDBHelper(this);
        firestore = FirebaseFirestore.getInstance();
        configHelper = new ConfigHelper();
        analyticsManager = new AnalyticsManager(this);
        analyticsManager.logAbsentCheckerOpen();

        initViews();
        setupToolbar();
        setupFilters();
        setupButtons();

        selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        updateDateFilterUI();

        // Initial data load: Prefer local if possible
        loadInitialData();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        sectionSpinner = findViewById(R.id.sectionSpinner);
        dateFilterButton = findViewById(R.id.dateFilterButton);
        clearDateFilterButton = findViewById(R.id.clearDateFilterButton);
        searchEditText = findViewById(R.id.absentSearchEditText);
        sessionRadioGroup = findViewById(R.id.sessionRadioGroup);
        absentCountText = findViewById(R.id.absentCountText);
        masterlistStatusBanner = findViewById(R.id.masterlistStatusBanner);
        exportButton = findViewById(R.id.exportButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AbsentStudentAdapter();
        recyclerView.setAdapter(adapter);

        adapter.setOnItemLongClickListener(this::showStudentManagementDialog);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            analyticsManager.logOnForeground(); // Reuse for sync intent
            syncFromFirestore();
        });
    }

    private void loadInitialData() {
        // Step 1: Get local attendance first for the current day (Quota Saver)
        attendanceRecords = db.getAttendanceByDate(selectedDate);
        
        if (attendanceRecords.isEmpty()) {
            // Only if local is empty, we force a cloud sync
            syncFromFirestore();
        } else {
            calculateAbsents();
        }
    }

    private void setupToolbar() {
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        if (topAppBar != null) {
            setSupportActionBar(topAppBar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
            topAppBar.setTitle(getString(R.string.absent_checker_title));
            topAppBar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void setupFilters() {
        configHelper.fetchAndActivate(this, () -> {
            List<String> sections = new ArrayList<>();
            sections.add("ALL SECTIONS");
            sections.addAll(configHelper.getSections());
            sections.remove("Select a Section");

            ArrayAdapter<String> sectionAdapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, sections);
            sectionAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
            sectionSpinner.setAdapter(sectionAdapter);
        });

        sectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { calculateAbsents(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        dateFilterButton.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, day) -> {
                Calendar picked = Calendar.getInstance();
                picked.set(year, month, day);
                selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(picked.getTime());
                updateDateFilterUI();
                
                // When date changes, check local first. If empty, sync cloud for that specific date.
                attendanceRecords = db.getAttendanceByDate(selectedDate);
                if (attendanceRecords.isEmpty()) {
                    fetchAttendanceForDate(selectedDate);
                } else {
                    calculateAbsents();
                }
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        clearDateFilterButton.setOnClickListener(v -> {
            selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            updateDateFilterUI();
            attendanceRecords = db.getAttendanceByDate(selectedDate);
            calculateAbsents();
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { calculateAbsents(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        sessionRadioGroup.setOnCheckedChangeListener((group, checkedId) -> calculateAbsents());
    }

    private void setupButtons() {
        exportButton.setOnClickListener(v -> exportAbsents());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_absent_checker, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_import_ml) {
            handleImportClick();
            return true;
        } else if (id == R.id.action_clear_ml) {
            handleClearClick();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleImportClick() {
        if (!db.getMasterlist("ALL SECTIONS").isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Overwrite Masterlist?")
                    .setMessage("A masterlist already exists. Importing a new one will delete the current list and replace it. Do you want to continue?")
                    .setPositiveButton("Continue", (d, w) -> openFilePicker())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            openFilePicker();
        }
    }

    private void handleClearClick() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.absent_clear_confirm_title)
                .setMessage(R.string.absent_clear_confirm_message)
                .setPositiveButton("Clear All", (d, w) -> clearMasterlist())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        pickCsvLauncher.launch(intent);
    }

    private void syncFromFirestore() {
        swipeRefreshLayout.setRefreshing(true);
        
        // Sync Masterlist
        firestore.collection("student_masterlist").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    db.clearMasterlist();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String id = doc.getString("studentID");
                        String name = doc.getString("name");
                        String sec = doc.getString("section");
                        if (id != null && name != null) {
                            db.upsertStudent(id, name, sec);
                        }
                    }
                    // Then sync attendance for selected date
                    fetchAttendanceForDate(selectedDate);
                })
                .addOnFailureListener(e -> {
                    showSnackbar("Offline: Using cached data.");
                    fetchAttendanceForDate(selectedDate);
                });
    }

    private void fetchAttendanceForDate(String date) {
        swipeRefreshLayout.setRefreshing(true);
        firestore.collection("attendance_records")
                .whereEqualTo("date", date)
                .get()
                .addOnSuccessListener(snapshots -> {
                    attendanceRecords.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        AttendanceRecord r = new AttendanceRecord(
                                0, doc.getString("name"), doc.getString("studentID"),
                                doc.getString("date"), doc.getString("time_in_am"),
                                doc.getString("time_out_am"), doc.getString("time_in_pm"),
                                doc.getString("time_out_pm"), doc.getString("section")
                        );
                        attendanceRecords.add(r);
                        db.insertOrUpdate(r); // Cache it locally
                    }
                    calculateAbsents();
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    attendanceRecords = db.getAttendanceByDate(date);
                    calculateAbsents();
                    swipeRefreshLayout.setRefreshing(false);
                });
    }

    private void calculateAbsents() {
        String sectionFilter = sectionSpinner.getSelectedItem() != null ? sectionSpinner.getSelectedItem().toString() : "ALL SECTIONS";
        String searchQuery = searchEditText.getText().toString().trim().toLowerCase();
        
        List<AdminCacheDBHelper.StudentML> masterlist = db.getMasterlist(sectionFilter);
        masterlistStatusBanner.setVisibility(db.getMasterlist("ALL SECTIONS").isEmpty() ? View.VISIBLE : View.GONE);

        if (masterlist.isEmpty()) {
            adapter.submitList(new ArrayList<>());
            absentCountText.setText(getString(R.string.absent_label_total, 0));
            return;
        }

        // Presence Logic
        int selectedSessionId = sessionRadioGroup.getCheckedRadioButtonId();
        Map<String, AttendanceRecord> attendeeMap = new HashMap<>();
        for (AttendanceRecord r : attendanceRecords) {
            if (!r.getDate().equals(selectedDate)) continue;
            
            String sec = r.getSection() != null ? r.getSection().trim().toUpperCase(Locale.ROOT) : "";
            if (sec.equals("COLSC") || sec.equals("TESTING PURPOSES")) continue;

            boolean isPresent = false;
            if (selectedSessionId == R.id.radioAM) {
                isPresent = isFilled(r.getTimeInAM()) || isFilled(r.getTimeOutAM());
            } else if (selectedSessionId == R.id.radioPM) {
                isPresent = isFilled(r.getTimeInPM()) || isFilled(r.getTimeOutPM());
            } else { // Both
                isPresent = isFilled(r.getTimeInAM()) || isFilled(r.getTimeOutAM()) ||
                            isFilled(r.getTimeInPM()) || isFilled(r.getTimeOutPM());
            }

            if (isPresent) {
                attendeeMap.put(normalizeID(r.getStudentID()), r);
            }
        }

        List<AdminCacheDBHelper.StudentML> absentList = new ArrayList<>();
        for (AdminCacheDBHelper.StudentML student : masterlist) {
            // Apply Search Filter locally
            boolean matchesSearch = searchQuery.isEmpty() || 
                                    student.name.toLowerCase().contains(searchQuery) || 
                                    student.studentID.toLowerCase().contains(searchQuery);
            
            if (matchesSearch && !attendeeMap.containsKey(normalizeID(student.studentID))) {
                absentList.add(student);
            }
        }

        adapter.submitList(absentList);
        absentCountText.setText(getString(R.string.absent_label_total, absentList.size()));
    }

    private void showStudentManagementDialog(AdminCacheDBHelper.StudentML student, int pos) {
        String[] options = {"Update Info", "Delete from Masterlist"};
        new AlertDialog.Builder(this)
                .setTitle(student.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showUpdateStudentDialog(student);
                    else confirmDeleteStudent(student);
                })
                .show();
    }

    private void showUpdateStudentDialog(AdminCacheDBHelper.StudentML student) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 0);

        EditText nameInput = new EditText(this);
        nameInput.setText(student.name);
        nameInput.setHint("Name");
        layout.addView(nameInput);

        EditText idInput = new EditText(this);
        idInput.setText(student.studentID);
        idInput.setHint("ID Number (YY-NNNNN)");
        layout.addView(idInput);

        EditText sectionInput = new EditText(this);
        sectionInput.setText(student.section);
        sectionInput.setHint("Section");
        layout.addView(sectionInput);

        new AlertDialog.Builder(this)
                .setTitle("Update Student Info")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = nameInput.getText().toString().trim();
                    String newId = idInput.getText().toString().trim();
                    String newSec = sectionInput.getText().toString().trim();
                    
                    if (newName.isEmpty() || newId.isEmpty() || newSec.isEmpty()) {
                        showSnackbar("Fields cannot be empty.");
                        return;
                    }

                    updateStudentInFirestore(student.studentID, newId, newName, newSec);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateStudentInFirestore(String oldId, String newId, String name, String section) {
        swipeRefreshLayout.setRefreshing(true);
        Map<String, Object> data = new HashMap<>();
        data.put("studentID", newId);
        data.put("name", name);
        data.put("section", section);

        WriteBatch batch = firestore.batch();
        if (!oldId.equals(newId)) {
            batch.delete(firestore.collection("student_masterlist").document(normalizeID(oldId)));
        }
        batch.set(firestore.collection("student_masterlist").document(normalizeID(newId)), data);

        batch.commit().addOnSuccessListener(unused -> {
            db.updateStudentML(oldId, newId, name, section);
            showSnackbar("Student updated.");
            calculateAbsents();
            swipeRefreshLayout.setRefreshing(false);
        }).addOnFailureListener(e -> {
            showSnackbar("Update failed.");
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void confirmDeleteStudent(AdminCacheDBHelper.StudentML student) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Student")
                .setMessage("Are you sure you want to remove " + student.name + " from the masterlist?")
                .setPositiveButton("Delete", (d, w) -> deleteStudentFromFirestore(student.studentID))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteStudentFromFirestore(String studentId) {
        swipeRefreshLayout.setRefreshing(true);
        firestore.collection("student_masterlist").document(normalizeID(studentId)).delete()
                .addOnSuccessListener(unused -> {
                    db.deleteStudentML(studentId);
                    showSnackbar("Student removed from masterlist.");
                    calculateAbsents();
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    showSnackbar("Deletion failed.");
                    swipeRefreshLayout.setRefreshing(false);
                });
    }

    private void importMasterlistFromUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            List<Map<String, Object>> students = new ArrayList<>();
            String line;
            boolean firstLine = true;
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                if (cols.length < 3) continue;

                String id = cols[0].replace("\"", "").trim();
                String name = cols[1].replace("\"", "").trim();
                String sec = cols[2].replace("\"", "").trim();

                if (firstLine && (id.toLowerCase().contains("id") || name.toLowerCase().contains("name"))) {
                    firstLine = false;
                    continue;
                }

                if (!id.isEmpty() && !name.isEmpty()) {
                    Map<String, Object> s = new HashMap<>();
                    s.put("studentID", id);
                    s.put("name", name);
                    s.put("section", sec);
                    students.add(s);
                }
            }

            if (students.isEmpty()) {
                showSnackbar(getString(R.string.absent_import_error));
                return;
            }

            uploadMasterlist(students);

        } catch (Exception e) {
            Log.e(TAG, "CSV Import failed", e);
            showSnackbar(getString(R.string.absent_import_error));
        }
    }

    private void uploadMasterlist(List<Map<String, Object>> students) {
        swipeRefreshLayout.setRefreshing(true);
        WriteBatch batch = firestore.batch();
        for (Map<String, Object> student : students) {
            String id = (String) student.get("studentID");
            batch.set(firestore.collection("student_masterlist").document(normalizeID(id)), student);
        }

        batch.commit().addOnSuccessListener(unused -> {
            showSnackbar(getString(R.string.absent_import_success, students.size()));
            analyticsManager.logMasterlistImport(students.size());
            syncFromFirestore();
        }).addOnFailureListener(e -> {
            showSnackbar("Upload failed: " + e.getMessage());
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void clearMasterlist() {
        swipeRefreshLayout.setRefreshing(true);
        firestore.collection("student_masterlist").get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.isEmpty()) {
                        db.clearMasterlist();
                        showSnackbar("Masterlist is already empty.");
                        calculateAbsents();
                        swipeRefreshLayout.setRefreshing(false);
                        return;
                    }

                    WriteBatch batch = firestore.batch();
                    for (DocumentSnapshot doc : snapshots) batch.delete(doc.getReference());

                    batch.commit().addOnSuccessListener(unused -> {
                        db.clearMasterlist();
                        showSnackbar("Masterlist cleared.");
                        calculateAbsents();
                        swipeRefreshLayout.setRefreshing(false);
                    }).addOnFailureListener(e -> {
                        showSnackbar("Cloud clear failed.");
                        swipeRefreshLayout.setRefreshing(false);
                    });
                });
    }

    private void exportAbsents() {
        List<AdminCacheDBHelper.StudentML> list = adapter.getCurrentList();
        if (list.isEmpty()) {
            showSnackbar("No absent records to export.");
            return;
        }

        String sessionText = "Both";
        int checked = sessionRadioGroup.getCheckedRadioButtonId();
        if (checked == R.id.radioAM) sessionText = "AM";
        else if (checked == R.id.radioPM) sessionText = "PM";

        String section = sectionSpinner.getSelectedItem().toString();
        String safeSection = section.equals("ALL SECTIONS") ? "ALLSECTION" : section.replaceAll("[^a-zA-Z0-9]", "_");
        String fileName = "Absent_" + safeSection + "_" + sessionText + "_" + selectedDate + ".csv";

        try {
            File file = new File(getExternalFilesDir(null), fileName);
            FileWriter writer = new FileWriter(file);
            writer.append("ID Number,Name,Section,Date,Session,Status\n");
            for (AdminCacheDBHelper.StudentML s : list) {
                writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"ABSENT\"\n",
                        s.studentID, s.name, s.section, selectedDate, sessionText));
            }
            writer.flush();
            writer.close();

            analyticsManager.logExport("absent_checker");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Absent List"));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateDateFilterUI() {
        dateFilterButton.setText(selectedDate);
        dateFilterButton.setTextColor(getColor(R.color.md_theme_onSurface));
    }

    private String normalizeID(String id) {
        return id == null ? "" : id.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private boolean isFilled(String val) {
        return val != null && !val.equals("-") && !val.trim().isEmpty();
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(getColor(R.color.md_theme_secondary))
                .setTextColor(getColor(R.color.white))
                .show();
    }
}
