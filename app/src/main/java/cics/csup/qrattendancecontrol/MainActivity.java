package cics.csup.qrattendancecontrol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RadioGroup modeRadioGroup;
    private RadioButton radioTimeIn, radioTimeOut;
    private Button scanButton, historyButton;
    private TextView qrDataText, statusText, timeText, dateText;
    private EditText sectionEditText;

    private AttendanceDBHelper db;
    private SharedPreferences sharedPreferences;

    private static final String PREFS_NAME = "AttendancePrefs";
    private static final String KEY_SECTION = "last_section";

    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseApp.initializeApp(this);
        firestore = FirebaseFirestore.getInstance();

        View customToastView = LayoutInflater.from(this).inflate(R.layout.custom_toast, null);
        int offsetY = (int) (100 * getResources().getDisplayMetrics().density);
        Toast customToast = new Toast(getApplicationContext());
        customToast.setView(customToastView);
        customToast.setDuration(Toast.LENGTH_SHORT);
        customToast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, offsetY);
        customToast.show();

        applyWindowInsetPadding();

        modeRadioGroup = findViewById(R.id.modeRadioGroup);
        radioTimeIn = findViewById(R.id.radioTimeIn);
        radioTimeOut = findViewById(R.id.radioTimeOut);
        scanButton = findViewById(R.id.scanButton);
        historyButton = findViewById(R.id.historyButton);
        qrDataText = findViewById(R.id.qrDataText);
        statusText = findViewById(R.id.statusText);
        timeText = findViewById(R.id.timeText);
        dateText = findViewById(R.id.dateText);
        sectionEditText = findViewById(R.id.sectionEditText);
        Button adminButton = findViewById(R.id.adminButton);

        db = new AttendanceDBHelper(this);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String lastSection = sharedPreferences.getString(KEY_SECTION, "");
        sectionEditText.setText(lastSection);

        historyButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        adminButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.putExtra("target", "admin"); // tell LoginActivity it's for admin access
            startActivity(intent);
        });

        scanButton.setOnClickListener(v -> {
            hideKeyboard();

            int selectedId = modeRadioGroup.getCheckedRadioButtonId();
            if (selectedId == -1) {
                Toast.makeText(this, "Please select Time In or Time Out.", Toast.LENGTH_SHORT).show();
                return;
            }

            String section = sectionEditText.getText().toString().trim();
            if (section.isEmpty()) {
                Toast.makeText(this, "Please enter your section before scanning.", Toast.LENGTH_SHORT).show();
                return;
            }

            sharedPreferences.edit().putString(KEY_SECTION, section).apply();

            IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
            integrator.setPrompt("Scan QR Code");
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
            String qrContent = result.getContents().trim();
            String mode = radioTimeIn.isChecked() ? "in" : "out";
            String section = sectionEditText.getText().toString().trim();

            String status = db.markAttendance(qrContent, mode, section);

            String currentTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
            String currentDate = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(new Date());

            qrDataText.setText("QR Data: " + qrContent);
            statusText.setText("Status: " + (mode.equals("out") ? "Time-Out" : "Time-In"));
            timeText.setText("Time: " + currentTime);
            dateText.setText("Date: " + currentDate);

            Toast.makeText(this, status, Toast.LENGTH_SHORT).show();

            syncUnsyncedDataToFirebase();
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

    // ✅ Prevents duplicate Firestore records
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
                            // Document exists — update it
                            for (QueryDocumentSnapshot doc : query) {
                                firestore.collection("attendance_records")
                                        .document(doc.getId())
                                        .update(record.toMap())
                                        .addOnSuccessListener(unused -> db.markAsSynced(record.getId()));
                                break;
                            }
                        } else {
                            // Document does not exist — add new
                            firestore.collection("attendance_records")
                                    .add(record.toMap())
                                    .addOnSuccessListener(unused -> db.markAsSynced(record.getId()));
                        }
                    });
        }
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
