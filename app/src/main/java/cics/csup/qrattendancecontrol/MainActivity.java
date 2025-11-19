package cics.csup.qrattendancecontrol;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.core.view.WindowInsetsCompat;
import android.app.AlertDialog;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

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

    // 4. FINALIZED: ActivityResultLauncher
    private final ActivityResultLauncher<Intent> qrScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String qrContent = result.getData().getStringExtra("SCAN_RESULT");

                    if (qrContent == null) {
                        showConfirmationDialog("Scan Failed", "No QR code was returned.");
                    } else {
                        // --- FINAL REFACTOR: Get the friendly name and pass it ---
                        RadioButton selectedRadioButton = findViewById(
                                amRadioGroup.getCheckedRadioButtonId() != -1 ?
                                        amRadioGroup.getCheckedRadioButtonId() :
                                        pmRadioGroup.getCheckedRadioButtonId()
                        );
                        String timeSlotFriendlyName = selectedRadioButton.getText().toString();

                        handleScanResult(qrContent, timeSlotFriendlyName);
                        // --- END OF FIX ---
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

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.custom_spinner_item, sections) {
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
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
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

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startQRScanner() {
        hideKeyboard();

        String timeSlotField = getSelectedTimeSlotField();
        if (timeSlotField == null) {
            // CHANGED: Use Snackbar
            showSnackbar("Please select a time slot.");
            return;
        }

        String section = sectionSpinner.getSelectedItem().toString();
        if ("Select a Section".equals(section)) {
            // CHANGED: Use Snackbar
            showSnackbar("Please select your section before scanning.");
            return;
        }

        sharedPreferences.edit().putString(KEY_SECTION, section).apply();

        // Get the friendly name (e.g., "Time In (AM)")
        RadioButton selectedRadioButton = findViewById(
                amRadioGroup.getCheckedRadioButtonId() != -1 ?
                        amRadioGroup.getCheckedRadioButtonId() :
                        pmRadioGroup.getCheckedRadioButtonId()
        );
        String timeSlotFriendlyName = selectedRadioButton.getText().toString();

        // --- THIS IS THE NEW LAUNCH LOGIC ---
        Intent intent = new Intent(this, CustomScanActivity.class);

        // CHANGED: We now send two pieces of text
        intent.putExtra("SCAN_TITLE", "Scan Student QR Code");
        intent.putExtra("SCAN_INDICATOR", "(" + timeSlotFriendlyName + ")");

        // Launch the activity using our new launcher
        qrScannerLauncher.launch(intent);
    }

    // REPLACED: Final version of handleScanResult (now accepts two args)
    private void handleScanResult(String qrContent, String timeSlotFriendlyName) {
        qrContent = qrContent.trim();

        // --- 1. Split by the "|" character ---
        String[] parts = qrContent.split("\\|");

        String studentID;
        String studentName;

        if (parts.length < 2) {
            // BACKWARD COMPATIBILITY: No "|" found
            if (qrContent.contains("-") || qrContent.matches(".*\\d.*")) {
                studentID = qrContent;
                studentName = qrContent;
                qrDataText.setText("Name: " + studentName);
                showConfirmationDialog("Old QR Code Scanned", "Please change the QR Code to the new format.");
            } else {
                showConfirmationDialog("Invalid QR Code", "The code should contain:\nID Number & Name.\n\nExample:\nID Number: 24-0000\nName: BAOIT, JEYLO T.\n\nPlease generate a new one.");
                return;
            }
        } else {
            // NEW QR CODE: "20-06281|BAOIT, JEYLO T."
            studentID = parts[0];
            studentName = parts[1];
            qrDataText.setText("Name: " + studentName);
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
            showSnackbar("Please select a time slot.");
            return;
        }
        statusText.setText("Status: " + field.replace("_", " ").toUpperCase(Locale.getDefault()));

        // --- 3. UPDATE THE LOGIC (FOR THE APP) ---

        AttendanceRecord localRecord = db.getRecordByStudentID(studentID, currentDateStorage, section);

        if (!validateScan(field, localRecord)) {
            return; // Validation failed
        }

        db.markDetailedAttendance(studentID, studentName, currentDateStorage, section, field, currentTimeDisplay);

        // --- FINAL DIALOG MESSAGE ---
        showConfirmationDialog("Success", "Attendance recorded for:\n\n" + "Name: " +studentName + "\nID Number: " + studentID + "\nSlot: " + timeSlotFriendlyName);

        syncUnsyncedRecords();
    }

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

    private boolean validateScan(String field, AttendanceRecord record) {
        if (record == null) {
            return true; // No record exists, so any scan is valid
        }

        // --- RULE 1: Check if slot is already filled ---
        String existing = record.getFieldValue(field);
        if (existing != null && !existing.equals("-")) {
            showConfirmationDialog("Scan Error", "That time slot is already scanned please double check.");
            return false;
        }

        // --- RULE 2 (NEW): Check for scanning "IN" after "OUT" ---
        if (field.equals("time_in_am") && !record.getFieldValue("time_out_am").equals("-")) {
            showConfirmationDialog("Scan Error", "You cannot Time In AM\nYou have already Timed Out AM.");
            return false;
        }
        if (field.equals("time_in_pm") && !record.getFieldValue("time_out_pm").equals("-")) {
            showConfirmationDialog("Scan Error", "You cannot Time In PM\nYou have already Timed Out PM.");
            return false;
        }

        // --- RULE 3: Check for scanning "AM" after "PM" ---
        boolean hasPmRecord = !record.getFieldValue("time_in_pm").equals("-") ||
                !record.getFieldValue("time_out_pm").equals("-");

        if (field.contains("am") && hasPmRecord) {
            showConfirmationDialog("Scan Error", "Cannot record AM attendance.\nPM attendance has already started.");
            return false;
        }

        return true;
    }

    // ------------------- BACKGROUND SYNC -------------------

    private void syncUnsyncedRecords() {
        if (!isOnline()) return;

        List<AttendanceRecord> unsyncedRecords = db.getUnsyncedRecords();
        if (unsyncedRecords.isEmpty()) return;

        for (AttendanceRecord record : unsyncedRecords) {
            String docId = record.getIdHash();
            DocumentReference docRef = firestore.collection("attendance_records").document(docId);

            firestore.runTransaction((Transaction.Function<Void>) transaction -> {
                        DocumentSnapshot snapshot = transaction.get(docRef);
                        Map<String, Object> existing = snapshot.getData();
                        if (existing == null) existing = new HashMap<>();

                        Map<String, Object> uploadData = new HashMap<>();

                        if (shouldSyncField(existing, "time_in_am", record.getTimeInAM()))
                            uploadData.put("time_in_am", record.getTimeInAM());
                        if (shouldSyncField(existing, "time_out_am", record.getTimeOutAM()))
                            uploadData.put("time_out_am", record.getTimeOutAM());
                        if (shouldSyncField(existing, "time_in_pm", record.getTimeInPM()))
                            uploadData.put("time_in_pm", record.getTimeInPM());
                        if (shouldSyncField(existing, "time_out_pm", record.getTimeOutPM()))
                            uploadData.put("time_out_pm", record.getTimeOutPM());

                        if (!uploadData.isEmpty()) {
                            uploadData.put("name", record.getName());
                            uploadData.put("studentID", record.getStudentID());
                            uploadData.put("date", record.getDate());
                            uploadData.put("section", record.getSection());
                            transaction.set(docRef, uploadData, SetOptions.merge());
                        }

                        return null;
                    }).addOnSuccessListener(unused -> db.markAsSynced(record.getId()))
                    .addOnFailureListener(Throwable::printStackTrace);
        }
    }

    private boolean shouldSyncField(Map<String, Object> existing, String key, String localValue) {
        if (localValue == null || localValue.equals("-")) {
            return false;
        }
        return !existing.containsKey(key) || existing.get(key) == null || existing.get(key).equals("-");
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

    // ------------------- UI HELPERS -------------------

    /**
     * Displays a simple confirmation dialog (popup) instead of a Toast.
     */
    private void showConfirmationDialog(String title, String message) {
        // Use ContextThemeWrapper to apply your app's theme
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_QRAttendanceControl)
        );

        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss(); // Just close the dialog
                })
                .setCancelable(false) // User must press OK
                .show();
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

    /**
     * Displays a non-stacking Snackbar message.
     */
    private void showSnackbar(String message) {
        // We need to find a root view to attach the Snackbar to.
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
        } else {
            // Fallback to a toast if the view isn't ready
            showCenteredToast(message);
        }
    }
}