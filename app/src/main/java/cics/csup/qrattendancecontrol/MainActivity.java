package cics.csup.qrattendancecontrol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {
    private static final String[] SECTIONS = {
            "1A", "1B", "1C", "1D",
            "2A", "2B", "2C",
            "3A", "3B", "3C",
            "4A", "4B", "4C"
    };
    private RadioGroup amRadioGroup, pmRadioGroup;
    private Button scanButton, historyButton;
    private TextView qrDataText, statusText, timeText, dateText;
    private AttendanceDBHelper db;
    private SharedPreferences sharedPreferences;
    private RadioButton radioTimeInAM, radioTimeOutAM, radioTimeInPM, radioTimeOutPM;

    private static final String PREFS_NAME = "AttendancePrefs";
    private static final String KEY_SECTION = "last_section";
    private FirebaseFirestore firestore;
    private Spinner sectionSpinner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Find views
        amRadioGroup = findViewById(R.id.amRadioGroup);
        pmRadioGroup = findViewById(R.id.pmRadioGroup);
        RadioButton radioTimeInPM = findViewById(R.id.radioTimeInPM);
        RadioButton radioTimeOutPM = findViewById(R.id.radioTimeOutPM);

        // Use array to hold amListener so it can be accessed inside pmListener
        final RadioGroup.OnCheckedChangeListener[] amListener = new RadioGroup.OnCheckedChangeListener[1];

        // PM listener
        CompoundButton.OnCheckedChangeListener pmListener = (buttonView, isChecked) -> {
            if (isChecked) {
                amRadioGroup.setOnCheckedChangeListener(null);
                amRadioGroup.clearCheck();
                amRadioGroup.setOnCheckedChangeListener(amListener[0]);
            }
        };

        // Now define the AM listener and store it in array
        amListener[0] = (group, checkedId) -> {
            if (checkedId != -1) {
                radioTimeInPM.setOnCheckedChangeListener(null);
                radioTimeOutPM.setOnCheckedChangeListener(null);
                radioTimeInPM.setChecked(false);
                radioTimeOutPM.setChecked(false);
                radioTimeInPM.setOnCheckedChangeListener(pmListener);
                radioTimeOutPM.setOnCheckedChangeListener(pmListener);
            }
        };

        // Attach listeners
        amRadioGroup.setOnCheckedChangeListener(amListener[0]);
        radioTimeInPM.setOnCheckedChangeListener(pmListener);
        radioTimeOutPM.setOnCheckedChangeListener(pmListener);

        // Attach PM listener
        radioTimeInPM.setOnCheckedChangeListener(pmListener);
        radioTimeOutPM.setOnCheckedChangeListener(pmListener);

        sectionSpinner = findViewById(R.id.sectionSpinner);

        getWindow().setNavigationBarColor(Color.parseColor("#121212"));
        getWindow().setStatusBarColor(Color.parseColor("#121212"));

        // Make status bar and nav bar icons light (only works on Android 6.0+)
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(0); // Clears flags like LIGHT_STATUS_BAR


        FirebaseApp.initializeApp(this);
        firestore = FirebaseFirestore.getInstance();

        View customToastView = LayoutInflater.from(this).inflate(R.layout.custom_toast, null);
        Toast customToast = new Toast(getApplicationContext());
        customToast.setView(customToastView);
        customToast.setDuration(Toast.LENGTH_SHORT);
        customToast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, (int) (100 * getResources().getDisplayMetrics().density));
        customToast.show();

        applyWindowInsetPadding();

        // RadioGroup modeRadioGroup = findViewById(R.id.modeRadioGroup);

        scanButton = findViewById(R.id.scanButton);
        historyButton = findViewById(R.id.historyButton);
        qrDataText = findViewById(R.id.qrDataText);
        statusText = findViewById(R.id.statusText);
        timeText = findViewById(R.id.timeText);
        dateText = findViewById(R.id.dateText);
        Button adminButton = findViewById(R.id.adminButton);

        db = new AttendanceDBHelper(this);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Populate dropdown
        List<String> sectionList = Arrays.asList(
                "Select a Section", // Hint item
                "1A", "1B", "1C", "1D",
                "2A", "2B", "2C",
                "3A", "3B", "3C",
                "4A", "4B", "4C"
        );
        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.SpinnerPopupStyle);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, sectionList) {
            @Override
            public boolean isEnabled(int position) {
                return position != 0; // disable "Select a Section"
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;

                // Set text color
                if (position == 0) {
                    textView.setTextColor(getResources().getColor(R.color.hint));
                } else {
                    textView.setTextColor(getResources().getColor(R.color.text_primary));
                }

                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sectionSpinner.setAdapter(adapter);

        // Restore last selected
        String lastSection = sharedPreferences.getString(KEY_SECTION, "Select a Section");
        int lastIndex = sectionList.indexOf(lastSection);
        if (lastIndex != -1) sectionSpinner.setSelection(lastIndex);

        // Save on select
        sectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != 0) {
                    sharedPreferences.edit().putString(KEY_SECTION, sectionList.get(position)).apply();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        historyButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));

        adminButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.putExtra("target", "admin");
            startActivity(intent);
        });

        scanButton.setOnClickListener(v -> {
            hideKeyboard();

            int amId = amRadioGroup.getCheckedRadioButtonId();
            int pmId = pmRadioGroup.getCheckedRadioButtonId();

            if (amId == -1 && pmId == -1) {
                Toast.makeText(this, "Please select a time slot.", Toast.LENGTH_SHORT).show();
                return;
            }

            RadioButton selectedRadioButton = findViewById(amId != -1 ? amId : pmId);
            String timeSlot = selectedRadioButton.getText().toString();

            String section = sectionSpinner.getSelectedItem().toString();
            if (section.equals("Select a Section")) {
                Toast.makeText(this, "Please select your section before scanning.", Toast.LENGTH_SHORT).show();
                return;
            }

            sharedPreferences.edit().putString(KEY_SECTION, section).apply();

            IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
            integrator.setPrompt("Scan QR Code\n(" + timeSlot + ")");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(true);
            integrator.setCaptureActivity(QRScanActivity.class);
            integrator.initiateScan();
        });
        syncUnsyncedDataToFirebase();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result != null && result.getContents() != null) {
            final String qrContent = result.getContents().trim();
            final String section = sectionSpinner.getSelectedItem().toString().trim().toUpperCase();
            final String currentTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
            final String currentDate = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(new Date());

            qrDataText.setText("QR Data: " + qrContent);
            timeText.setText("Time: " + currentTime);
            dateText.setText("Date: " + currentDate);

            int selectedId = amRadioGroup.getCheckedRadioButtonId();
            if (selectedId == -1) selectedId = pmRadioGroup.getCheckedRadioButtonId();
            final String field;
            if (selectedId == R.id.radioTimeInAM) field = "time_in_am";
            else if (selectedId == R.id.radioTimeOutAM) field = "time_out_am";
            else if (selectedId == R.id.radioTimeInPM) field = "time_in_pm";
            else if (selectedId == R.id.radioTimeOutPM) field = "time_out_pm";
            else {
                Toast.makeText(this, "Please select a time slot.", Toast.LENGTH_SHORT).show();
                return;
            }

            statusText.setText("Status: " + field.replace("_", " ").toUpperCase(Locale.getDefault()));

            // Local validation
            AttendanceRecord localRecord = db.getRecordByNameDateSection(qrContent, currentDate, section);
            String localInPM = localRecord != null ? localRecord.getTimeInPM() : "-";
            String localOutPM = localRecord != null ? localRecord.getTimeOutPM() : "-";
            String localOutAM = localRecord != null ? localRecord.getTimeOutAM() : "-";

            // Prevent AM actions if any PM slot is filled
            if ((field.equals("time_in_am") || field.equals("time_out_am")) &&
                    ((localInPM != null && !localInPM.equals("-")) || (localOutPM != null && !localOutPM.equals("-")))) {
                Toast.makeText(this, "Cannot mark AM slots after PM slots.", Toast.LENGTH_LONG).show();
                return;
            }

            // Block Time In AM if already timed out in AM
            if (field.equals("time_in_am") &&
                    (localOutAM != null && !localOutAM.equals("-"))) {
                Toast.makeText(this, "You already timed out in the morning. Cannot time in now.", Toast.LENGTH_LONG).show();
                return;
            }

            // Block Time In PM if PM already filled
            if (field.equals("time_in_pm") &&
                    ((localInPM != null && !localInPM.equals("-")) || (localOutPM != null && !localOutPM.equals("-")))) {
                Toast.makeText(this, "PM slot already filled. Cannot time in again.", Toast.LENGTH_LONG).show();
                return;
            }

            // Firestore validation
            firestore.collection("attendance_records")
                    .whereEqualTo("name", qrContent)
                    .whereEqualTo("date", currentDate)
                    .whereEqualTo("section", section)
                    .get()
                    .addOnSuccessListener(query -> {
                        if (!query.isEmpty()) {
                            for (QueryDocumentSnapshot doc : query) {
                                String existingFieldValue = doc.getString(field);
                                String remoteInPM = doc.getString("time_in_pm");
                                String remoteOutPM = doc.getString("time_out_pm");
                                String remoteOutAM = doc.getString("time_out_am");

                                // Prevent AM actions if any PM slot exists remotely
                                if ((field.equals("time_in_am") || field.equals("time_out_am")) &&
                                        ((remoteInPM != null && !remoteInPM.equals("-")) ||
                                                (remoteOutPM != null && !remoteOutPM.equals("-")))) {
                                    Toast.makeText(this, "Cannot mark AM slots after PM slots (cloud).", Toast.LENGTH_LONG).show();
                                    return;
                                }

                                // Block Time In AM if already timed out remotely
                                if (field.equals("time_in_am") &&
                                        (remoteOutAM != null && !remoteOutAM.equals("-"))) {
                                    Toast.makeText(this, "You already timed out in the morning (cloud). Cannot time in now.", Toast.LENGTH_LONG).show();
                                    return;
                                }

                                // Block Time In PM if any PM slot filled remotely
                                if (field.equals("time_in_pm") &&
                                        ((remoteInPM != null && !remoteInPM.equals("-")) ||
                                                (remoteOutPM != null && !remoteOutPM.equals("-")))) {
                                    Toast.makeText(this, "PM slot already filled (cloud). Cannot time in again.", Toast.LENGTH_LONG).show();
                                    return;
                                }

                                // Block overwrite
                                if (existingFieldValue != null && !existingFieldValue.equals("-")) {
                                    Toast.makeText(this, "This time slot is already filled. Cannot overwrite.", Toast.LENGTH_LONG).show();
                                    return;
                                }

                                // Update record
                                db.markDetailedAttendance(qrContent, currentDate, section, field, currentTime);
                                firestore.collection("attendance_records")
                                        .document(doc.getId())
                                        .update(field, currentTime)
                                        .addOnSuccessListener(unused ->
                                                Toast.makeText(this, "Updated record", Toast.LENGTH_SHORT).show());
                                break;
                            }
                        } else {
                            // No existing record, create new
                            db.markDetailedAttendance(qrContent, currentDate, section, field, currentTime);
                            AttendanceRecord record = new AttendanceRecord(
                                    0, qrContent, currentDate, "-", "-", "-", "-", section
                            );
                            record.setField(field, currentTime);

                            firestore.collection("attendance_records")
                                    .document(record.getIdHash())
                                    .set(record.toMap())
                                    .addOnSuccessListener(unused ->
                                            Toast.makeText(this, "New record added", Toast.LENGTH_SHORT).show());
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error accessing Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void syncUnsyncedDataToFirebase() {
        if (!isOnline()) return;

        List<AttendanceRecord> unsynced = db.getUnsyncedRecords();
        for (AttendanceRecord record : unsynced) {
            firestore.collection("attendance_records")
                    .whereEqualTo("name", record.getName())
                    .whereEqualTo("date", record.getDate())
                    .whereEqualTo("section", record.getSection())
                    .get()
                    .addOnSuccessListener(query -> {
                        if (!query.isEmpty()) {
                            for (QueryDocumentSnapshot doc : query) {
                                String inAM = doc.getString("time_in_am");
                                String outAM = doc.getString("time_out_am");
                                String inPM = doc.getString("time_in_pm");
                                String outPM = doc.getString("time_out_pm");

                                String newInAM = inAM != null && !inAM.equals("-") ? inAM : record.getTimeInAM();
                                String newOutAM = outAM != null && !outAM.equals("-") ? outAM : record.getTimeOutAM();
                                String newInPM = inPM != null && !inPM.equals("-") ? inPM : record.getTimeInPM();
                                String newOutPM = outPM != null && !outPM.equals("-") ? outPM : record.getTimeOutPM();

                                if (!newInAM.equals(inAM) || !newOutAM.equals(outAM)
                                        || !newInPM.equals(inPM) || !newOutPM.equals(outPM)) {
                                    firestore.collection("attendance_records")
                                            .document(doc.getId())
                                            .update("time_in_am", newInAM,
                                                    "time_out_am", newOutAM,
                                                    "time_in_pm", newInPM,
                                                    "time_out_pm", newOutPM)
                                            .addOnSuccessListener(unused -> db.markAsSynced(record.getId()));
                                } else {
                                    db.markAsSynced(record.getId());
                                }
                                break;
                            }
                        } else {
                            firestore.collection("attendance_records")
                                    .add(record.toMap())
                                    .addOnSuccessListener(unused -> db.markAsSynced(record.getId()));
                        }
                    });
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void applyWindowInsetPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (view, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            view.setPadding(0, topInset, 0, bottomInset);
            return insets;
        });
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        }
        return false;
    }
}
