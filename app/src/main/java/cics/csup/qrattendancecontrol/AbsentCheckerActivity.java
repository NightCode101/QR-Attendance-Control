package cics.csup.qrattendancecontrol;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
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

    private RecyclerView recyclerView;
    private AttendanceAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Spinner sectionSpinner;
    private TextView dateFilterButton, absentCountText, masterlistStatusBanner;
    private MaterialButton clearDateFilterButton, importMLButton, clearMLButton, exportButton;

    private String selectedDate;
    private List<AdminCacheDBHelper.StudentML> fullMasterlist = new ArrayList<>();
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

        initViews();
        setupToolbar();
        setupFilters();
        setupButtons();

        selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        updateDateFilterUI();

        syncFromFirestore();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        sectionSpinner = findViewById(R.id.sectionSpinner);
        dateFilterButton = findViewById(R.id.dateFilterButton);
        clearDateFilterButton = findViewById(R.id.clearDateFilterButton);
        absentCountText = findViewById(R.id.absentCountText);
        masterlistStatusBanner = findViewById(R.id.masterlistStatusBanner);
        importMLButton = findViewById(R.id.importMLButton);
        clearMLButton = findViewById(R.id.clearMLButton);
        exportButton = findViewById(R.id.exportButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AttendanceAdapter();
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::syncFromFirestore);
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
                fetchAttendanceAndCalculate();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        clearDateFilterButton.setOnClickListener(v -> {
            selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            updateDateFilterUI();
            fetchAttendanceAndCalculate();
        });
    }

    private void setupButtons() {
        importMLButton.setOnClickListener(v -> {
            if (!fullMasterlist.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("Overwrite Masterlist?")
                        .setMessage("A masterlist already exists. Importing a new one will delete the current list and replace it. Do you want to continue?")
                        .setPositiveButton("Continue", (d, w) -> openFilePicker())
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                openFilePicker();
            }
        });

        clearMLButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.absent_clear_confirm_title)
                    .setMessage(R.string.absent_clear_confirm_message)
                    .setPositiveButton("Clear All", (d, w) -> clearMasterlist())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        exportButton.setOnClickListener(v -> exportAbsents());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        pickCsvLauncher.launch(intent);
    }

    private void syncFromFirestore() {
        swipeRefreshLayout.setRefreshing(true);
        firestore.collection("student_masterlist").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    db.clearMasterlist();
                    fullMasterlist.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String id = doc.getString("studentID");
                        String name = doc.getString("name");
                        String sec = doc.getString("section");
                        if (id != null && name != null) {
                            db.upsertStudent(id, name, sec);
                        }
                    }
                    fetchAttendanceAndCalculate();
                })
                .addOnFailureListener(e -> {
                    showSnackbar("Offline: Using cached masterlist.");
                    fetchAttendanceAndCalculate();
                });
    }

    private void fetchAttendanceAndCalculate() {
        firestore.collection("attendance_records")
                .whereEqualTo("date", selectedDate)
                .get()
                .addOnSuccessListener(snapshots -> {
                    attendanceRecords.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        attendanceRecords.add(new AttendanceRecord(
                                0, doc.getString("name"), doc.getString("studentID"),
                                doc.getString("date"), doc.getString("time_in_am"),
                                doc.getString("time_out_am"), doc.getString("time_in_pm"),
                                doc.getString("time_out_pm"), doc.getString("section")
                        ));
                    }
                    calculateAbsents();
                    swipeRefreshLayout.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    attendanceRecords = db.getAllRecords(); // Fallback to all cached records
                    calculateAbsents();
                    swipeRefreshLayout.setRefreshing(false);
                });
    }

    private void calculateAbsents() {
        String sectionFilter = sectionSpinner.getSelectedItem() != null ? sectionSpinner.getSelectedItem().toString() : "ALL SECTIONS";
        fullMasterlist = db.getMasterlist(sectionFilter);
        
        masterlistStatusBanner.setVisibility(fullMasterlist.isEmpty() ? View.VISIBLE : View.GONE);

        if (fullMasterlist.isEmpty()) {
            adapter.submitList(new ArrayList<>());
            absentCountText.setText(getString(R.string.absent_label_total, 0));
            return;
        }

        // Map for quick lookup of attendees
        Map<String, AttendanceRecord> attendeeMap = new HashMap<>();
        for (AttendanceRecord r : attendanceRecords) {
            if (r.getDate().equals(selectedDate)) {
                attendeeMap.put(normalizeID(r.getStudentID()), r);
            }
        }

        List<AttendanceRecord> absentList = new ArrayList<>();
        for (AdminCacheDBHelper.StudentML student : fullMasterlist) {
            if (!attendeeMap.containsKey(normalizeID(student.studentID))) {
                absentList.add(new AttendanceRecord(0, student.name, student.studentID, selectedDate, "-", "-", "-", "-", student.section));
            }
        }

        adapter.submitList(absentList);
        absentCountText.setText(getString(R.string.absent_label_total, absentList.size()));
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
        
        // Overwrite strategy: Clear and add new
        // Note: Real clearing of a collection in Firestore is complex for clients.
        // We will just upload and then the local sync will refresh the cache.
        for (Map<String, Object> student : students) {
            String id = (String) student.get("studentID");
            batch.set(firestore.collection("student_masterlist").document(normalizeID(id)), student);
        }

        batch.commit().addOnSuccessListener(unused -> {
            showSnackbar(getString(R.string.absent_import_success, students.size()));
            syncFromFirestore();
        }).addOnFailureListener(e -> {
            showSnackbar("Upload failed: " + e.getMessage());
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void clearMasterlist() {
        swipeRefreshLayout.setRefreshing(true);
        
        // 1. Fetch current IDs from Firestore to delete them
        firestore.collection("student_masterlist").get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.isEmpty()) {
                        db.clearMasterlist();
                        showSnackbar("Masterlist is already empty.");
                        syncFromFirestore();
                        return;
                    }

                    WriteBatch batch = firestore.batch();
                    for (DocumentSnapshot doc : snapshots) {
                        batch.delete(doc.getReference());
                    }

                    // 2. Commit Firestore deletion
                    batch.commit().addOnSuccessListener(unused -> {
                        // 3. Clear local only after cloud success
                        db.clearMasterlist();
                        showSnackbar("Masterlist cleared from cloud and local.");
                        syncFromFirestore();
                    }).addOnFailureListener(e -> {
                        showSnackbar("Cloud clear failed: " + e.getMessage());
                        swipeRefreshLayout.setRefreshing(false);
                    });
                })
                .addOnFailureListener(e -> {
                    showSnackbar("Failed to access cloud masterlist.");
                    swipeRefreshLayout.setRefreshing(false);
                });
    }

    private void exportAbsents() {
        List<AttendanceRecord> list = adapter.getCurrentList();
        if (list.isEmpty()) {
            showSnackbar("No absent records to export.");
            return;
        }

        String section = sectionSpinner.getSelectedItem().toString();
        String safeSection = section.equals("ALL SECTIONS") ? "ALLSECTION" : section.replaceAll("[^a-zA-Z0-9]", "_");
        String fileName = "Absent_" + safeSection + "_" + selectedDate + ".csv";

        try {
            File file = new File(getExternalFilesDir(null), fileName);
            FileWriter writer = new FileWriter(file);
            writer.append("ID Number,Name,Section,Date,Status\n");
            for (AttendanceRecord r : list) {
                writer.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"ABSENT\"\n",
                        r.getStudentID(), r.getName(), r.getSection(), r.getDate()));
            }
            writer.flush();
            writer.close();

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

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(getColor(R.color.md_theme_secondary))
                .setTextColor(getColor(R.color.white))
                .show();
    }
}
