package cics.csup.qrattendancecontrol;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsCompat;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
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

import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import android.Manifest;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ConfigHelper configHelper;
    private AnalyticsManager analyticsManager;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 101;
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
    private RadioGroup.OnCheckedChangeListener amListener;
    private RadioGroup.OnCheckedChangeListener pmListener;

    private final SimpleDateFormat displayTimeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
    private final SimpleDateFormat storageDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private final ActivityResultLauncher<Intent> qrScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String qrContent = result.getData().getStringExtra("SCAN_RESULT");
                    if (qrContent == null) {
                        showConfirmationDialog("Scan Failed", "No QR code was returned.");
                    } else {
                        RadioButton selectedRadioButton = findViewById(
                                amRadioGroup.getCheckedRadioButtonId() != -1 ?
                                        amRadioGroup.getCheckedRadioButtonId() :
                                        pmRadioGroup.getCheckedRadioButtonId()
                        );
                        String timeSlotFriendlyName = selectedRadioButton.getText().toString();
                        handleScanResult(qrContent, timeSlotFriendlyName);
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

        FirebaseInstallations.getInstance().getId()
                .addOnSuccessListener(id -> {
                    Log.d("InAppMessage", "Instance ID: " + id);
                });

        // 1. Initialize ConfigHelper & AnalyticsManager
        configHelper = new ConfigHelper();
        analyticsManager = new AnalyticsManager(this); // <--- ADDED: Init Analytics

        // 2. Request Notification Permission (Required for Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
            }
        }

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

        graphButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, GraphActivity.class)));
        historyButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
        adminButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.putExtra("target", "admin");
            startActivity(intent);
        });
        scanButton.setOnClickListener(v -> startQRScanner());

        setupSectionSpinner();
        setupRadioGroupLogic();
        applyWindowInsetPadding();

        networkChangeReceiver = new NetworkChangeReceiver(this::syncUnsyncedRecords);
        registerReceiver(networkChangeReceiver, new android.content.IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        syncUnsyncedRecords();
        updateDateTimeLabels();
    }

    private void setupSectionSpinner() {
        // 2. Fetch data from Firebase Remote Config
        configHelper.fetchAndActivate(this, () -> {

            // 3. Get the dynamic list (or defaults if offline)
            List<String> sections = configHelper.getSections();

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.custom_spinner_item, sections) {
                @Override
                public boolean isEnabled(int position) {
                    return position != 0; // Disable "Select a Section"
                }

                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) view;
                    // Dynamic color logic
                    tv.setTextColor(ContextCompat.getColor(getContext(), position == 0 ? R.color.hint_text_color : R.color.md_theme_onSurface));
                    return view;
                }
            };

            adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
            sectionSpinner.setAdapter(adapter);

            // 4. Restore selection if it exists in the new list
            String lastSection = sharedPreferences.getString(KEY_SECTION, "Select a Section");
            int lastIndex = sections.indexOf(lastSection);
            if (lastIndex != -1) sectionSpinner.setSelection(lastIndex);
        });

        // Listener
        sectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != 0) {
                    // Safely get string from adapter
                    String selected = (String) parent.getItemAtPosition(position);
                    sharedPreferences.edit().putString(KEY_SECTION, selected).apply();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void setupRadioGroupLogic() {
        amListener = (group, checkedId) -> {
            if (checkedId != -1) {
                pmRadioGroup.setOnCheckedChangeListener(null);
                pmRadioGroup.clearCheck();
                pmRadioGroup.setOnCheckedChangeListener(pmListener);
            }
        };
        pmListener = (group, checkedId) -> {
            if (checkedId != -1) {
                amRadioGroup.setOnCheckedChangeListener(null);
                amRadioGroup.clearCheck();
                amRadioGroup.setOnCheckedChangeListener(amListener);
            }
        };
        amRadioGroup.setOnCheckedChangeListener(amListener);
        pmRadioGroup.setOnCheckedChangeListener(pmListener);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startQRScanner() {
        hideKeyboard();
        String timeSlotField = getSelectedTimeSlotField();
        if (timeSlotField == null) {
            showSnackbar("Please select a time slot.");
            return;
        }
        String section = sectionSpinner.getSelectedItem().toString();
        if ("Select a Section".equals(section)) {
            showSnackbar("Please select your section before scanning.");
            return;
        }
        sharedPreferences.edit().putString(KEY_SECTION, section).apply();

        RadioButton selectedRadioButton = findViewById(
                amRadioGroup.getCheckedRadioButtonId() != -1 ?
                        amRadioGroup.getCheckedRadioButtonId() :
                        pmRadioGroup.getCheckedRadioButtonId()
        );
        String timeSlotFriendlyName = selectedRadioButton.getText().toString();

        Intent intent = new Intent(this, CustomScanActivity.class);
        intent.putExtra("SCAN_TITLE", "Scan Student QR Code");
        intent.putExtra("SCAN_INDICATOR", "(" + timeSlotFriendlyName + ")");
        qrScannerLauncher.launch(intent);
    }

    private void handleScanResult(String qrContent, String timeSlotFriendlyName) {
        qrContent = qrContent.trim();
        String[] parts = qrContent.split("\\|");
        String studentID;
        String studentName;

        if (parts.length < 2) {
            if (qrContent.contains("-") || qrContent.matches(".*\\d.*")) {
                studentID = qrContent;
                studentName = qrContent;
                qrDataText.setText("Name: " + studentName);
                showConfirmationDialog("Old QR Code Scanned", "Please change the QR Code to the new format.");
            } else {
                showConfirmationDialog("Invalid QR Code", "The code should contain:\nID Number & Name.");
                // Log failed attempt
                analyticsManager.logScan("unknown", "unknown", timeSlotFriendlyName, false);
                return;
            }
        } else {
            studentID = parts[0];
            studentName = parts[1];
            qrDataText.setText("Name: " + studentName);
        }

        String section = sectionSpinner.getSelectedItem().toString().trim().toUpperCase();
        Date now = new Date();
        String currentTimeDisplay = displayTimeFormat.format(now);
        String currentDateStorage = storageDateFormat.format(now);

        timeText.setText("Time: " + currentTimeDisplay);
        dateText.setText("Date: " + currentDateStorage);

        String field = getSelectedTimeSlotField();
        if (field == null) return;
        statusText.setText("Status: " + field.replace("_", " ").toUpperCase(Locale.getDefault()));

        AttendanceRecord localRecord = db.getRecordByStudentID(studentID, currentDateStorage, section);

        if (!validateScan(field, localRecord)) {
            // Log failed attempt (validation error)
            analyticsManager.logScan(studentID, section, timeSlotFriendlyName, false);
            return;
        }

        db.markDetailedAttendance(studentID, studentName, currentDateStorage, section, field, currentTimeDisplay);

        // <--- ADDED: Log Successful Scan to Analytics
        analyticsManager.logScan(studentID, section, timeSlotFriendlyName, true);

        showConfirmationDialog("Success", "Attendance recorded for:\n\n" + "Name: " + studentName + "\nID Number: " + studentID + "\nSlot: " + timeSlotFriendlyName);
        syncUnsyncedRecords();
    }

    private String getSelectedTimeSlotField() {
        int selectedId = amRadioGroup.getCheckedRadioButtonId();
        if (selectedId == -1) selectedId = pmRadioGroup.getCheckedRadioButtonId();

        if (selectedId == R.id.radioTimeInAM) return "time_in_am";
        if (selectedId == R.id.radioTimeOutAM) return "time_out_am";
        if (selectedId == R.id.radioTimeInPM) return "time_in_pm";
        if (selectedId == R.id.radioTimeOutPM) return "time_out_pm";
        return null;
    }

    private boolean validateScan(String field, AttendanceRecord record) {
        if (record == null) return true;

        String existing = record.getFieldValue(field);
        if (existing != null && !existing.equals("-")) {
            showConfirmationDialog("Scan Error", "That time slot is already scanned.");
            return false;
        }
        if (field.equals("time_in_am") && !record.getFieldValue("time_out_am").equals("-")) {
            showConfirmationDialog("Scan Error", "You cannot Time In AM\nYou have already Timed Out AM.");
            return false;
        }
        if (field.equals("time_in_pm") && !record.getFieldValue("time_out_pm").equals("-")) {
            showConfirmationDialog("Scan Error", "You cannot Time In PM\nYou have already Timed Out PM.");
            return false;
        }
        boolean hasPmRecord = !record.getFieldValue("time_in_pm").equals("-") || !record.getFieldValue("time_out_pm").equals("-");
        if (field.contains("am") && hasPmRecord) {
            showConfirmationDialog("Scan Error", "Cannot record AM attendance.\nPM attendance has already started.");
            return false;
        }
        return true;
    }

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
                        if (shouldSyncField(existing, "time_in_am", record.getTimeInAM())) uploadData.put("time_in_am", record.getTimeInAM());
                        if (shouldSyncField(existing, "time_out_am", record.getTimeOutAM())) uploadData.put("time_out_am", record.getTimeOutAM());
                        if (shouldSyncField(existing, "time_in_pm", record.getTimeInPM())) uploadData.put("time_in_pm", record.getTimeInPM());
                        if (shouldSyncField(existing, "time_out_pm", record.getTimeOutPM())) uploadData.put("time_out_pm", record.getTimeOutPM());

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
        if (localValue == null || localValue.equals("-")) return false;
        return !existing.containsKey(key) || existing.get(key) == null || existing.get(key).equals("-");
    }

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

    private void showConfirmationDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(new androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_QRAttendanceControl));
        builder.setTitle(title).setMessage(message).setPositiveButton("OK", (dialog, which) -> dialog.dismiss()).setCancelable(false).show();
    }

    private void showSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
        else Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void updateDateTimeLabels() {
        Date now = new Date();
        timeText.setText("Time: " + displayTimeFormat.format(now));
        dateText.setText("Date: " + displayDateFormat.format(now));
    }
}