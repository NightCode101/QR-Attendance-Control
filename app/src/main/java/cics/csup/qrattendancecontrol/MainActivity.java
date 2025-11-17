package cics.csup.qrattendancecontrol;

// 1. ADDED: Make sure this import is here
import androidx.core.view.WindowInsetsCompat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher; // 2. ADDED: Modern API
import androidx.activity.result.contract.ActivityResultContracts; // 3. ADDED: Modern API
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private RadioGroup amRadioGroup, pmRadioGroup;
    private Button scanButton, historyButton;
    private TextView qrDataText, statusText, timeText, dateText;
    private AttendanceDBHelper db;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "AttendancePrefs";
    private static final String KEY_SECTION = "last_section";
    private FirebaseFirestore firestore;
    private Spinner sectionSpinner;
    private NetworkChangeReceiver networkChangeReceiver;

    private static final String HIDDEN_PREFS = "HiddenRecords";
    private static final String HIDDEN_KEY = "hidden_keys";
    private RadioGroup.OnCheckedChangeListener amListener;
    private RadioGroup.OnCheckedChangeListener pmListener;

    // Date formats
    private final SimpleDateFormat displayTimeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
    private final SimpleDateFormat storageDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // 4. CHANGED: Modern way to handle Activity Results
    private final ActivityResultLauncher<Intent> qrScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                IntentResult scanResult = IntentIntegrator.parseActivityResult(result.getResultCode(), result.getData());
                if (scanResult != null) {
                    if (scanResult.getContents() == null) {
                        showCenteredToast("No QR code detected.");
                    } else {
                        // Success! Process the scan.
                        handleScanResult(scanResult.getContents());
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);
        firestore = FirebaseFirestore.getInstance();
        db = new AttendanceDBHelper(this);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // --- Find Views ---
        amRadioGroup = findViewById(R.id.amRadioGroup);
        pmRadioGroup = findViewById(R.id.pmRadioGroup);
        scanButton = findViewById(R.id.scanButton);
        historyButton = findViewById(R.id.historyButton);
        qrDataText = findViewById(R.id.qrDataText);
        statusText = findViewById(R.id.statusText);
        timeText = findViewById(R.id.timeText);
        dateText = findViewById(R.id.dateText);
        sectionSpinner = findViewById(R.id.sectionSpinner);
        Button adminButton = findViewById(R.id.adminButton);
        Button graphButton = findViewById(R.id.graphButton);

        // --- Set Listeners ---
        graphButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, GraphActivity.class)));
        historyButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
        adminButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.putExtra("target", "admin");
            startActivity(intent);
        });
        scanButton.setOnClickListener(v -> startQRScanner());

        // --- Setup ---
        setupSectionSpinner();
        setupRadioGroupLogic();
        applyWindowInsetPadding();

        // 5. FUTURE NOTE: NetworkChangeReceiver is an older API.
        // For a future update, consider migrating this to WorkManager
        // for more reliable, battery-efficient background syncs.
        networkChangeReceiver = new NetworkChangeReceiver(this::syncUnsyncedRecords);
        registerReceiver(networkChangeReceiver, new android.content.IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        syncUnsyncedRecords();
        updateDateTimeLabels();
    }

    // ------------------- SECTION SPINNER -------------------

    private void setupSectionSpinner() {
        List<String> sections = Arrays.asList(
                "Select a Section", "1A", "1B", "1C", "1D",
                "2A", "2B", "2C", "3A", "3B", "3C", "4A", "4B", "4C",
                "COLSC", "TESTING PURPOSES"
        );

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sections) {
            @Override
            public boolean isEnabled(int position) {
                return position != 0; // Disable "Select a Section"
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                tv.setTextColor(getResources().getColor(position == 0 ? R.color.hint_text_color : R.color.md_theme_onSurface));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sectionSpinner.setAdapter(adapter);

        String lastSection = sharedPreferences.getString(KEY_SECTION, "Select a Section");
        int lastIndex = sections.indexOf(lastSection);
        if (lastIndex != -1) sectionSpinner.setSelection(lastIndex);

        sectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != 0)
                    sharedPreferences.edit().putString(KEY_SECTION, sections.get(position)).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    // ------------------- RADIO BUTTONS -------------------

    // 6. CHANGED: This is the robust logic that prevents listeners from fighting
    private void setupRadioGroupLogic() {

        amListener = (group, checkedId) -> {
            if (checkedId != -1) {
                // A button in AM was checked
                pmRadioGroup.setOnCheckedChangeListener(null); // Disable PM listener
                pmRadioGroup.clearCheck();                     // Clear PM
                pmRadioGroup.setOnCheckedChangeListener(pmListener); // Re-enable PM listener
            }
        };

        pmListener = (group, checkedId) -> {
            if (checkedId != -1) {
                // A button in PM was checked
                amRadioGroup.setOnCheckedChangeListener(null); // Disable AM listener
                amRadioGroup.clearCheck();                     // Clear AM
                amRadioGroup.setOnCheckedChangeListener(amListener); // Re-enable AM listener
            }
        };

        amRadioGroup.setOnCheckedChangeListener(amListener);
        pmRadioGroup.setOnCheckedChangeListener(pmListener);
    }

    // ------------------- QR SCANNER -------------------

    private void startQRScanner() {
        hideKeyboard();

        // Find the selected field (e.g., "time_in_am")
        String timeSlotField = getSelectedTimeSlotField();
        if (timeSlotField == null) {
            showCenteredToast("Please select a time slot.");
            return;
        }

        String section = sectionSpinner.getSelectedItem().toString();
        if ("Select a Section".equals(section)) {
            showCenteredToast("Please select your section before scanning.");
            return;
        }

        // Save the chosen section for next time
        sharedPreferences.edit().putString(KEY_SECTION, section).apply();

        // Get the friendly name (e.g., "Time In (AM)")
        RadioButton selectedRadioButton = findViewById(
                amRadioGroup.getCheckedRadioButtonId() != -1 ?
                        amRadioGroup.getCheckedRadioButtonId() :
                        pmRadioGroup.getCheckedRadioButtonId()
        );
        String timeSlotFriendlyName = selectedRadioButton.getText().toString();

        IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
        integrator.setPrompt("Scan QR Code\n(" + timeSlotFriendlyName + ")");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(true);
        integrator.setCaptureActivity(QRScanActivity.class);

        // 7. CHANGED: Launch the modern activity launcher
        qrScannerLauncher.launch(integrator.createScanIntent());
    }

    // 8. REMOVED: onActivityResult is now handled by qrScannerLauncher

    // 9. REPLACED: Final version of handleScanResult
    private void handleScanResult(String qrContent) {
        qrContent = qrContent.trim();

        // --- 1. Split by the "|" character ---
        // We must use "\\|" because "|" is a special character in regex
        String[] parts = qrContent.split("\\|");

        String studentID;
        String studentName;

        if (parts.length < 2) {
            // BACKWARD COMPATIBILITY: No "|" found, assume old QR code
            studentID = qrContent;
            studentName = qrContent; // Use the ID as the name
            qrDataText.setText("QR Data: " + studentName);
            Toast.makeText(this, "Old QR code scanned.", Toast.LENGTH_SHORT).show();
        } else {
            // NEW QR CODE: "20-06281|BAOIT, JEYLO T."
            studentID = parts[0];   // "20-06281"
            studentName = parts[1]; // "BAOIT, JEYLO T."
            qrDataText.setText("QR Data: " + studentName); // Show the friendly name
        }

        // --- End of new logic ---

        String section = sectionSpinner.getSelectedItem().toString().trim().toUpperCase();
        Date now = new Date();
        String currentTimeDisplay = displayTimeFormat.format(now);
        String currentDateStorage = storageDateFormat.format(now);

        // --- 2. UPDATE THE UI (FOR THE USER) ---
        timeText.setText("Time: " + currentTimeDisplay);
        dateText.setText("Date: " + currentDateStorage);

        String field = getSelectedTimeSlotField();
        if (field == null) {
            showCenteredToast("Please select a time slot.");
            return;
        }
        statusText.setText("Status: " + field.replace("_", " ").toUpperCase(Locale.getDefault()));


        // --- 3. UPDATE THE LOGIC (FOR THE APP) ---

        // CHANGED: Use the new getRecordByStudentID
        AttendanceRecord localRecord = db.getRecordByStudentID(studentID, currentDateStorage, section);

        if (!validateScan(field, localRecord)) {
            return; // Validation failed
        }

        // CHANGED: Pass BOTH the ID and the Name to the database
        db.markDetailedAttendance(studentID, studentName, currentDateStorage, section, field, currentTimeDisplay);

        // Show success and trigger sync
        showCenteredToast("Attendance recorded for " + studentName);

        syncUnsyncedRecords();
    }

    // 10. ADDED: Helper to get the selected field
    private String getSelectedTimeSlotField() {
        int selectedId = amRadioGroup.getCheckedRadioButtonId();
        if (selectedId == -1) {
            selectedId = pmRadioGroup.getCheckedRadioButtonId();
        }

        if (selectedId == R.id.radioTimeInAM) return "time_in_am";
        if (selectedId == R.id.radioTimeOutAM) return "time_out_am";
        if (selectedId == R.id.radioTimeInPM) return "time_in_pm";
        if (selectedId == R.id.radioTimeOutPM) return "time_out_pm";

        return null; // Nothing selected
    }

    // 11. ADDED: Refactored validation logic
    private boolean validateScan(String field, AttendanceRecord record) {
        if (record == null) {
            return true; // No record exists, so any scan is valid
        }

        // Check if a time is already recorded for this slot
        String existing = record.getFieldValue(field);
        if (existing != null && !existing.equals("-")) {
            showCenteredToast("That time slot is already filled.");
            return false;
        }

        // Check for logical errors (e.g., timing out before timing in)
        if (field.equals("time_out_am") && record.getFieldValue("time_in_am").equals("-")) {
            showCenteredToast("You must time in AM before time out AM.");
            return false;
        }
        if (field.equals("time_out_pm") && record.getFieldValue("time_in_pm").equals("-")) {
            showCenteredToast("You must time in PM before time out PM.");
            return false;
        }

        // Check for cross-session errors (e.g., AM scan during PM)
        boolean hasPmRecord = !record.getFieldValue("time_in_pm").equals("-") ||
                !record.getFieldValue("time_out_pm").equals("-");

        if (field.contains("am") && hasPmRecord) {
            showCenteredToast("Cannot record AM attendance after PM session started.");
            return false;
        }

        return true;
    }

    // 12. ADDED: Refactored Firestore logic
    private void syncRecordToFirestore(String name, String date, String section, String field, String time) {
        String docId = name.replaceAll("[^a-zA-Z0-9]", "_")
                + "_" + date + "_" + section;

        DocumentReference docRef = firestore.collection("attendance_records").document(docId);

        // Run a transaction to safely merge data
        // This prevents overwriting data if two devices scan at the same time
        firestore.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot snapshot = transaction.get(docRef);
                    Map<String, Object> data = snapshot.getData();

                    // Check if field is empty or doesn't exist
                    if (data == null || !data.containsKey(field) || data.get(field) == null || data.get(field).equals("-")) {
                        Map<String, Object> uploadData = new HashMap<>();
                        uploadData.put("name", name);
                        uploadData.put("date", date);
                        uploadData.put("section", section);
                        uploadData.put(field, time);
                        // Use merge to add/update fields without overwriting others
                        transaction.set(docRef, uploadData, SetOptions.merge());
                    }
                    return null;
                }).addOnSuccessListener(unused -> showCenteredToast("Attendance recorded successfully"))
                .addOnFailureListener(e -> {
                    showCenteredToast("Sync failed — will retry when online.");
                    e.printStackTrace();
                });
    }

    // ------------------- BACKGROUND SYNC -------------------

    private void syncUnsyncedRecords() {
        if (!isOnline()) return;

        List<AttendanceRecord> unsyncedRecords = db.getUnsyncedRecords();
        if (unsyncedRecords.isEmpty()) return;

        for (AttendanceRecord record : unsyncedRecords) {
            String docId = record.getIdHash(); // Use the hash from the model
            DocumentReference docRef = firestore.collection("attendance_records").document(docId);

            firestore.runTransaction((Transaction.Function<Void>) transaction -> {
                        DocumentSnapshot snapshot = transaction.get(docRef);
                        Map<String, Object> existing = snapshot.getData();
                        if (existing == null) existing = new HashMap<>();

                        Map<String, Object> uploadData = new HashMap<>();

                        // Check each field before adding it to the upload batch
                        if (shouldSyncField(existing, "time_in_am", record.getTimeInAM()))
                            uploadData.put("time_in_am", record.getTimeInAM());
                        if (shouldSyncField(existing, "time_out_am", record.getTimeOutAM()))
                            uploadData.put("time_out_am", record.getTimeOutAM());
                        if (shouldSyncField(existing, "time_in_pm", record.getTimeInPM()))
                            uploadData.put("time_in_pm", record.getTimeInPM());
                        if (shouldSyncField(existing, "time_out_pm", record.getTimeOutPM()))
                            uploadData.put("time_out_pm", record.getTimeOutPM());

                        if (!uploadData.isEmpty()) {
                            // Only upload if there's something new
                            uploadData.put("name", record.getName());
                            uploadData.put("date", record.getDate());
                            uploadData.put("section", record.getSection());
                            transaction.set(docRef, uploadData, SetOptions.merge());
                        }

                        return null;
                    }).addOnSuccessListener(unused -> db.markAsSynced(record.getId()))
                    .addOnFailureListener(Throwable::printStackTrace);
        }
    }

    // 13. ADDED: Helper for sync logic
    private boolean shouldSyncField(Map<String, Object> existing, String key, String localValue) {
        if (localValue == null || localValue.equals("-")) {
            return false; // Don't sync empty local values
        }
        // Sync if Firestore doesn't have the key OR if Firestore's value is empty/null
        return !existing.containsKey(key) || existing.get(key) == null || existing.get(key).equals("-");
    }

    // ------------------- HIDDEN RECORD UTILS -------------------

    private String makeHiddenKey(String name, String date) {
        return name.trim().toLowerCase() + "_" + date;
    }

    private void removeHiddenRecord(String name, String date) {
        // This is tied to the old SharedPreferences logic.
        // It's kept here to un-hide records scanned in MainActivity
        SharedPreferences prefs = getSharedPreferences(HIDDEN_PREFS, MODE_PRIVATE);
        Set<String> keys = new HashSet<>(prefs.getStringSet(HIDDEN_KEY, new HashSet<>()));
        keys.remove(makeHiddenKey(name, date));
        prefs.edit().putStringSet(HIDDEN_KEY, new HashSet<>(keys)).apply();
    }

    // ------------------- UTILITY METHODS -------------------

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    // 14. CHANGED: Fixed with the correct import
    private void applyWindowInsetPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkChangeReceiver != null) unregisterReceiver(networkChangeReceiver);
    }

    private void showCenteredToast(String message) {
        Toast t = Toast.makeText(this, message, Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }

    private void updateDateTimeLabels() {
        Date now = new Date();
        timeText.setText("Time: " + displayTimeFormat.format(now));
        dateText.setText("Date: " + displayDateFormat.format(now));
    }
}