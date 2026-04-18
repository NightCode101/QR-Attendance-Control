package cics.csup.qrattendancecontrol;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsCompat;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
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
import com.google.android.material.appbar.MaterialToolbar;

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

import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import android.app.PendingIntent;

public class MainActivity extends AppCompatActivity {

    private ConfigHelper configHelper;
    private AnalyticsManager analyticsManager;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 101;
    private RadioGroup amRadioGroup, pmRadioGroup;
    private Button scanButton, rfidScanButton, historyButton;
    private TextView qrDataText, statusText, timeText, dateText;
    private AttendanceDBHelper db;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "AttendancePrefs";
    private static final String KEY_SECTION = "last_section";
    private static final String KEY_NFC_MODE_ACTIVE = "nfc_mode_active";
    private static final String KEY_PENDING_NFC_TIME_SLOT_FIELD = "pending_nfc_field";
    private static final String KEY_PENDING_NFC_TIME_SLOT_NAME = "pending_nfc_name";
    private FirebaseFirestore firestore;
    private Spinner sectionSpinner;
    private NetworkChangeReceiver networkChangeReceiver;
    private RadioGroup.OnCheckedChangeListener amListener;
    private RadioGroup.OnCheckedChangeListener pmListener;
    private NfcAdapter nfcAdapter;
    private android.app.PendingIntent pendingIntent;
    private boolean nfcModeActive = false;
    private boolean isInForeground = false;
    private String pendingNfcTimeSlotField;
    private String pendingNfcTimeSlotName;
    private String pendingQrContent;
    private String pendingQrTimeSlotField;
    private String pendingQrTimeSlotName;

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
                        String timeSlotField = pendingQrTimeSlotField != null ? pendingQrTimeSlotField : getSelectedTimeSlotField();
                        String timeSlotFriendlyName = pendingQrTimeSlotName != null ? pendingQrTimeSlotName : getSelectedTimeSlotFriendlyName();
                        if (timeSlotField == null || timeSlotFriendlyName == null) {
                            showSnackbar("Please select a time slot.");
                        } else {
                            cachePendingQrResult(qrContent, timeSlotField, timeSlotFriendlyName);
                            if (!tryProcessPendingQrResult()) {
                                showSnackbar("Please wait, loading section data...");
                            }
                        }
                    }
                } else {
                    // Avoid carrying stale pending slot/content into a later scan.
                    clearPendingQrResult();
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

        // Debug: Log NFC adapter status and device capabilities
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Log.d("MainActivity", "DEBUG: NFC adapter is NULL - device may not have NFC");
        } else {
            Log.d("MainActivity", "DEBUG: NFC adapter found");
            Log.d("MainActivity", "DEBUG: NFC enabled: " + nfcAdapter.isEnabled());

            // Create PendingIntent for NFC foreground dispatch
            Intent nfcIntent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // NFC dispatch injects extras/action at delivery time, so PendingIntent must be mutable.
                pendingFlags |= PendingIntent.FLAG_MUTABLE;
            }
            pendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, pendingFlags);
            Log.d("MainActivity", "DEBUG: PendingIntent created for NFC foreground dispatch");
        }
        Log.d("MainActivity", "DEBUG: Intent action on startup: " + (getIntent() != null ? getIntent().getAction() : "null"));

        FirebaseInstallations.getInstance().getId()
                .addOnSuccessListener(id -> Log.d("InAppMessage", "Instance ID: " + id));

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
        rfidScanButton = findViewById(R.id.rfidScanButton);
        historyButton = findViewById(R.id.historyButton);
        qrDataText = findViewById(R.id.qrDataText);
        statusText = findViewById(R.id.statusText);
        timeText = findViewById(R.id.timeText);
        dateText = findViewById(R.id.dateText);
        sectionSpinner = findViewById(R.id.sectionSpinner);
        Button graphButton = findViewById(R.id.graphButton);
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        topAppBar.setTitle(R.string.app_title);

        graphButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, GraphActivity.class)));
        historyButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
        topAppBar.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_graph) {
                startActivity(new Intent(MainActivity.this, GraphActivity.class));
                return true;
            }
            if (itemId == R.id.action_admin_panel) {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.putExtra("target", "admin");
                startActivity(intent);
                return true;
            }
            if (itemId == R.id.action_about) {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
                return true;
            }
            return false;
        });
        scanButton.setOnClickListener(v -> startQRScanner());
        rfidScanButton.setOnClickListener(v -> startRFIDScanner());

        setupSectionSpinner();
        setupRadioGroupLogic();
        applyWindowInsetPadding();

        networkChangeReceiver = new NetworkChangeReceiver(this::syncUnsyncedRecords);
        registerReceiver(networkChangeReceiver, new android.content.IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        syncUnsyncedRecords();
        updateDateTimeLabels();

        // Restore NFC mode from SharedPreferences
        nfcModeActive = sharedPreferences.getBoolean(KEY_NFC_MODE_ACTIVE, false);
        if (nfcModeActive) {
            pendingNfcTimeSlotField = sharedPreferences.getString(KEY_PENDING_NFC_TIME_SLOT_FIELD, "");
            pendingNfcTimeSlotName = sharedPreferences.getString(KEY_PENDING_NFC_TIME_SLOT_NAME, "");
            Log.d("MainActivity", "DEBUG: Restored NFC mode from SharedPreferences - field: " + pendingNfcTimeSlotField);
        }

        // Handle NFC intent if app was launched via NFC
        Log.d("MainActivity", "DEBUG: onCreate - checking initial intent for NFC data");
        handleNfcIntent(getIntent(), "onCreate");
    }

    @Override
    protected void onResume() {
        super.onResume();
        isInForeground = true;
        // Restore NFC mode in case it was set by a previous instance
        nfcModeActive = sharedPreferences.getBoolean(KEY_NFC_MODE_ACTIVE, false);
        if (nfcModeActive) {
            pendingNfcTimeSlotField = sharedPreferences.getString(KEY_PENDING_NFC_TIME_SLOT_FIELD, "");
            pendingNfcTimeSlotName = sharedPreferences.getString(KEY_PENDING_NFC_TIME_SLOT_NAME, "");
        }
        Log.d("MainActivity", "onResume: isInForeground=true, nfcModeActive=" + nfcModeActive);

        // Enable NFC foreground dispatch to receive NFC intents
        if (nfcModeActive && nfcAdapter != null && pendingIntent != null) {
            try {
                nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
                Log.d("MainActivity", "DEBUG: NFC foreground dispatch ENABLED");
                handleNfcIntent(getIntent(), "onResume");
            } catch (Exception e) {
                Log.e("MainActivity", "DEBUG: Failed to enable NFC foreground dispatch: " + e.getMessage());
            }
        } else if (nfcModeActive) {
            Log.d("MainActivity", "DEBUG: Cannot enable foreground dispatch - nfcAdapter=" + (nfcAdapter != null ? "OK" : "NULL") + ", pendingIntent=" + (pendingIntent != null ? "OK" : "NULL"));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isInForeground = false;
        Log.d("MainActivity", "onPause: isInForeground=false");

        // Disable NFC foreground dispatch when app goes to background
        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableForegroundDispatch(this);
                Log.d("MainActivity", "DEBUG: NFC foreground dispatch DISABLED");
            } catch (Exception e) {
                Log.e("MainActivity", "DEBUG: Failed to disable NFC foreground dispatch: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) {
            Log.w("MainActivity", "DEBUG: onNewIntent called with null intent");
            return;
        }
        setIntent(intent);
        Log.d("MainActivity", "DEBUG: ===== onNewIntent CALLED =====");
        Log.d("MainActivity", "DEBUG: nfcModeActive=" + nfcModeActive + ", isInForeground=" + isInForeground);

        String action = intent.getAction();
        Log.d("MainActivity", "DEBUG: Intent action: " + action);

        // Log all extras in the intent
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                Log.d("MainActivity", "DEBUG: Intent extra: " + key + " = " + (value != null ? value.getClass().getSimpleName() : "null"));
            }
        }
        handleNfcIntent(intent, "onNewIntent");
    }

    private void handleNfcIntent(Intent intent, String source) {
        if (intent == null) return;

        String action = intent.getAction();
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        boolean isNfcAction = NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action);
        boolean hasTag = tag != null;

        Log.d("MainActivity", "DEBUG: handleNfcIntent(" + source + ") action=" + action + ", hasTag=" + hasTag + ", nfcModeActive=" + nfcModeActive + ", isInForeground=" + isInForeground);

        if (!isNfcAction && !hasTag) {
            return;
        }

        if (!nfcModeActive) {
            Log.d("MainActivity", "DEBUG: NFC card tapped but RFID mode is not active");
            return;
        }

        if (!hasTag) {
            Log.d("MainActivity", "DEBUG: NFC-related intent received without tag payload");
            return;
        }

        processNFCTag(tag);
    }

    private void processNFCTag(Tag tag) {
        Log.d("MainActivity", "DEBUG: === processNFCTag called ===");

        // Log tag details
        Log.d("MainActivity", "DEBUG: Tag ID: " + java.util.Arrays.toString(tag.getId()));
        Log.d("MainActivity", "DEBUG: Tag tech list: " + Arrays.toString(tag.getTechList()));

        Ndef ndef = Ndef.get(tag);
        Log.d("MainActivity", "DEBUG: Ndef instance: " + (ndef != null ? "found" : "null"));

        if (ndef != null) {
            try {
                ndef.connect();
                android.nfc.NdefMessage ndefMsg = ndef.getNdefMessage();
                if (ndefMsg != null) {
                    Log.d("MainActivity", "DEBUG: NDEF message found, records count: " + ndefMsg.getRecords().length);
                    String data = readNdefMessage(ndefMsg);
                    Log.d("MainActivity", "DEBUG: Parsed NDEF data: " + data);
                    if (data != null && !data.isEmpty()) {
                        processNFCData(data);
                    } else {
                        Log.d("MainActivity", "DEBUG: No valid NDEF data extracted");
                        showConfirmationDialog("Invalid NFC Card Data", "Card does not contain valid student data (ID|Name).");
                    }
                } else {
                    Log.d("MainActivity", "DEBUG: NDEF message is null");
                    showConfirmationDialog("Invalid NFC Card", "Card is not NDEF formatted. Please use a card with NDEF message.");
                }
            } catch (Exception e) {
                Log.e("MainActivity", "DEBUG: Error reading NFC", e);
                showConfirmationDialog("NFC Read Error", "Unable to read this NFC card. Please try again.");
            } finally {
                try {
                    ndef.close();
                } catch (Exception closeError) {
                    Log.w("MainActivity", "DEBUG: Failed to close NDEF connection", closeError);
                }
            }
        } else {
            Log.d("MainActivity", "DEBUG: Not an NDEF tag, reading raw UID as fallback");
            try {
                byte[] id = tag.getId();
                String uidHex = bytesToHex(id);
                Log.d("MainActivity", "DEBUG: Card UID (hex): " + uidHex + ", length: " + id.length);
                showConfirmationDialog("Not NDEF formatted", "Card UID: " + uidHex + "\n\nPlease use a card formatted with NDEF student data (ID|Name).");
            } catch (Exception e) {
                Log.e("MainActivity", "DEBUG: Error reading tag UID", e);
            }
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private String readNdefMessage(android.nfc.NdefMessage ndefMessage) {
        Log.d("MainActivity", "DEBUG: === readNdefMessage processing ===");
        android.nfc.NdefRecord[] records = ndefMessage.getRecords();
        Log.d("MainActivity", "DEBUG: Total NDEF records found: " + records.length);

        for (int i = 0; i < records.length; i++) {
            android.nfc.NdefRecord record = records[i];
            int tnf = record.getTnf();
            byte[] type = record.getType();
            byte[] payload = record.getPayload();

            if (payload == null || payload.length == 0) {
                Log.d("MainActivity", "DEBUG: Record " + i + " has empty payload");
                continue;
            }

            Log.d("MainActivity", "DEBUG: Record " + i + " - TNF: " + tnf + ", Type: " + Arrays.toString(type) + ", Payload length: " + payload.length);

            if (tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN) {
                Log.d("MainActivity", "DEBUG: Record " + i + " is WELL_KNOWN");

                if (Arrays.equals(type, android.nfc.NdefRecord.RTD_TEXT)) {
                    Log.d("MainActivity", "DEBUG: Record " + i + " is RTD_TEXT");
                    try {
                        // RTD_TEXT structure: first byte = status code
                        // bit 7 = text encoding (0=UTF-8, 1=UTF-16)
                        // bits 5-0 = language code length
                        String textEncoding = ((payload[0] & 0x80) == 0) ? "UTF-8" : "UTF-16";
                        int languageCodeLength = payload[0] & 0x3F;
                        if (payload.length < languageCodeLength + 1) {
                            Log.d("MainActivity", "DEBUG: Invalid RTD_TEXT payload length for record " + i);
                            continue;
                        }

                        Log.d("MainActivity", "DEBUG: Text encoding: " + textEncoding + ", language code length: " + languageCodeLength);

                        String result = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
                        Log.d("MainActivity", "DEBUG: Extracted text: " + result);
                        return result;
                    } catch (Exception e) {
                        Log.e("MainActivity", "DEBUG: Error parsing RTD_TEXT", e);
                    }
                } else if (Arrays.equals(type, android.nfc.NdefRecord.RTD_URI)) {
                    Log.d("MainActivity", "DEBUG: Record " + i + " is RTD_URI");
                    try {
                        if (payload.length < 2) {
                            Log.d("MainActivity", "DEBUG: Invalid RTD_URI payload length for record " + i);
                            continue;
                        }
                        String uri = new String(payload, 1, payload.length - 1, StandardCharsets.UTF_8);
                        Log.d("MainActivity", "DEBUG: Extracted URI: " + uri);
                        return uri;
                    } catch (Exception e) {
                        Log.e("MainActivity", "DEBUG: Error parsing RTD_URI", e);
                    }
                }
            } else if (tnf == android.nfc.NdefRecord.TNF_MIME_MEDIA) {
                Log.d("MainActivity", "DEBUG: Record " + i + " is MIME_MEDIA, MIME type: " + new String(type, StandardCharsets.UTF_8));
                try {
                    String data = new String(payload, StandardCharsets.UTF_8);
                    Log.d("MainActivity", "DEBUG: MIME payload data: " + data);
                    return data;
                } catch (Exception e) {
                    Log.e("MainActivity", "DEBUG: Error parsing MIME payload", e);
                }
            } else {
                Log.d("MainActivity", "DEBUG: Record " + i + " TNF unknown: " + tnf);
            }
        }

        Log.d("MainActivity", "DEBUG: No matching NDEF records found");
        return null;
    }

    private void processNFCData(String data) {
        data = sanitizeScanPayload(data);
        String[] parts = data.split("\\|", 2);

        if (parts.length < 2) {
            showConfirmationDialog("Invalid NFC Card", "Card format invalid. Expected: ID|Name");
            clearNfcPendingState();
            return;
        }

        if (pendingNfcTimeSlotField == null || pendingNfcTimeSlotField.trim().isEmpty() || pendingNfcTimeSlotName == null || pendingNfcTimeSlotName.trim().isEmpty()) {
            showConfirmationDialog("NFC Scan Error", "Time slot was not selected. Tap RFID Scan again.");
            clearNfcPendingState();
            return;
        }

        // Use stored time slot from when RFID Scan button was tapped
        handleScanResult(data, pendingNfcTimeSlotField, pendingNfcTimeSlotName);
        clearNfcPendingState();
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

            // Process any QR result that arrived while the spinner was still loading.
            tryProcessPendingQrResult();
        });

        // Listener
        sectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != 0) {
                    // Safely get string from adapter
                    String selected = (String) parent.getItemAtPosition(position);
                    sharedPreferences.edit().putString(KEY_SECTION, selected).apply();
                    tryProcessPendingQrResult();
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
        String section = resolveSelectedSection();
        if (section == null) {
            showSnackbar("Please select your section before scanning.");
            return;
        }
        sharedPreferences.edit().putString(KEY_SECTION, section).apply();

        String timeSlotFriendlyName = getSelectedTimeSlotFriendlyName();
        if (timeSlotFriendlyName == null) {
            showSnackbar("Please select a time slot.");
            return;
        }
        pendingQrTimeSlotField = timeSlotField;
        pendingQrTimeSlotName = timeSlotFriendlyName;

        Intent intent = new Intent(this, CustomScanActivity.class);
        intent.putExtra("SCAN_TITLE", "Scan Student QR Code");
        intent.putExtra("SCAN_INDICATOR", "(" + timeSlotFriendlyName + ")");
        qrScannerLauncher.launch(intent);
    }

    private void startRFIDScanner() {
        hideKeyboard();
        if (nfcAdapter == null || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
            showConfirmationDialog("NFC Not Supported", "This device does not support NFC.");
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            showConfirmationDialog("NFC Disabled", "Please enable NFC in device settings and try again.");
            return;
        }

        String timeSlotField = getSelectedTimeSlotField();
        if (timeSlotField == null) {
            showSnackbar("Please select a time slot.");
            return;
        }
        String section = resolveSelectedSection();
        if (section == null) {
            showSnackbar("Please select your section before scanning.");
            return;
        }
        sharedPreferences.edit().putString(KEY_SECTION, section).apply();

        String timeSlotFriendlyName = getSelectedTimeSlotFriendlyName();
        if (timeSlotFriendlyName == null) {
            showSnackbar("Please select a time slot.");
            return;
        }

        // RFID scanning now runs in a dedicated screen for smoother continuous scans.
        clearNfcPendingState();
        Intent intent = new Intent(this, RFIDScanActivity.class);
        intent.putExtra("SECTION", section);
        intent.putExtra("TIME_SLOT_FIELD", timeSlotField);
        intent.putExtra("TIME_SLOT_NAME", timeSlotFriendlyName);
        startActivity(intent);
    }

    private void handleScanResult(String qrContent, String timeSlotField, String timeSlotFriendlyName) {
        qrContent = sanitizeScanPayload(qrContent);
        String[] parts = qrContent.split("\\|", 2);
        String studentID;
        String studentName;

        // Check QR Content Format
        if (parts.length < 2) {
            // It doesn't have the "|" separator. Check if it looks like an old ID.
            if (qrContent.contains("-") || qrContent.matches(".*\\d.*")) {
                // --- OLD FORMAT DETECTED ---
                qrDataText.setText("Status: Old QR Format Rejected");

                // 1. Show clearer error dialog notifying that it was NOT recorded
                showConfirmationDialog("Old QR Code Scanned", "This QR code uses an outdated format and was NOT recorded.\n\nPlease use the new format: ID|Name");

                // 2. Log this as a failed scan attempt
                // Using "old_format" as ID so you can track how often this happens in Firebase
                if (analyticsManager != null) {
                    analyticsManager.logScan("old_format_rejected", "unknown", timeSlotFriendlyName, false);
                }

                // 3. STOP EXECUTION IMMEDIATELY so it doesn't save to DB
                return;

            } else {
                // --- GARBAGE DATA DETECTED ---
                showConfirmationDialog("Invalid QR Code", "The code should contain:\nID Number & Name.");
                // Log failed attempt
                if (analyticsManager != null) {
                    analyticsManager.logScan("invalid_data", "unknown", timeSlotFriendlyName, false);
                }
                return;
            }
        } else {
            // --- NEW CORRECT FORMAT ---
            studentID = normalizeStudentId(parts[0]);
            studentName = normalizeStudentName(parts[1]);
            if (studentID.isEmpty() || studentName.isEmpty()) {
                showConfirmationDialog("Invalid QR Code", "The code should contain: ID|Name");
                if (analyticsManager != null) {
                    analyticsManager.logScan("invalid_data", "unknown", timeSlotFriendlyName, false);
                }
                return;
            }
            qrDataText.setText("Name: " + studentName);
        }

        // --- If code reaches here, the format is correct. Proceed with recording. ---

        String section = resolveSelectedSection();
        if (section == null) {
            cachePendingQrResult(qrContent, timeSlotField, timeSlotFriendlyName);
            showSnackbar("Please select a valid section first.");
            return;
        }

        Date now = new Date();
        String currentTimeDisplay = displayTimeFormat.format(now);
        String currentDateStorage = storageDateFormat.format(now);

        timeText.setText("Time: " + currentTimeDisplay);
        dateText.setText("Date: " + displayDateFormat.format(now));

        if (timeSlotField == null || timeSlotField.trim().isEmpty()) {
            showSnackbar("Please select a time slot (AM/PM In/Out).");
            return;
        }
        statusText.setText("Status: " + timeSlotField.replace("_", " ").toUpperCase(Locale.getDefault()));

        AttendanceRecord localRecord = db.getRecordByStudentID(studentID, currentDateStorage, section);
        AttendanceRecord idConflict = db.findNameIdConflict(studentName, currentDateStorage, section, studentID);

        if (idConflict != null) {
            showConfirmationDialog(
                    "ID Mismatch Detected",
                    "This student name was already recorded today with a different ID.\n\n"
                            + "Existing ID: " + idConflict.getStudentID() + "\n"
                            + "Scanned ID: " + studentID + "\n\n"
                            + "Attendance was NOT recorded. Please verify the QR/NFC data."
            );
            if (analyticsManager != null) {
                analyticsManager.logScan("id_mismatch_blocked", section, timeSlotFriendlyName, false);
            }
            return;
        }

        if (!validateScan(timeSlotField, localRecord)) {
            // Log failed attempt (validation error like double scan)
            if (analyticsManager != null) {
                analyticsManager.logScan(studentID, section, timeSlotFriendlyName, false);
            }
            return;
        }

        // SAVE TO LOCAL DATABASE
        db.markDetailedAttendance(studentID, studentName, currentDateStorage, section, timeSlotField, currentTimeDisplay);
        Log.d("MainActivity", "DEBUG: Saved to database - studentID: " + studentID + ", name: " + studentName + ", date: " + currentDateStorage + ", section: " + section + ", field: " + timeSlotField);

        // Verify record was saved by querying it back
        AttendanceRecord savedRecord = db.getRecordByStudentID(studentID, currentDateStorage, section);
        if (savedRecord != null) {
            Log.d("MainActivity", "DEBUG: Record verified in database - synced: " + savedRecord.isSynced());
        } else {
            Log.d("MainActivity", "DEBUG: ERROR - Record not found after saving!");
        }

        // Log Successful Scan to Analytics
        if (analyticsManager != null) {
            analyticsManager.logScan(studentID, section, timeSlotFriendlyName, true);
        }

        showConfirmationDialog("Success", "Attendance recorded for:\n\n" + "Name: " + studentName + "\nID Number: " + studentID + "\nSlot: " + timeSlotFriendlyName);

        // Try to sync to cloud immediately
        syncUnsyncedRecords();

        // Scan was successfully consumed.
        clearPendingQrResult();
    }

    private String sanitizeScanPayload(String raw) {
        if (raw == null) return "";
        // Remove hidden characters frequently found in NFC-written text payloads.
        return raw.replace("\uFEFF", "")
                .replace("\u0000", "")
                .trim();
    }

    private String normalizeStudentId(String id) {
        String cleaned = sanitizeScanPayload(id);
        // IDs should not differ by spacing/case between QR and NFC.
        cleaned = cleaned.replaceAll("\\s+", "");
        return cleaned.toUpperCase(Locale.getDefault());
    }

    private String normalizeStudentName(String name) {
        String cleaned = sanitizeScanPayload(name);
        return cleaned.replaceAll("\\s{2,}", " ");
    }

    private String resolveSelectedSection() {
        if (sectionSpinner != null) {
            Object selected = sectionSpinner.getSelectedItem();
            if (selected != null) {
                String section = selected.toString().trim().toUpperCase(Locale.getDefault());
                if (!section.isEmpty() && !"SELECT A SECTION".equals(section)) {
                    return section;
                }
            }
        }

        String savedSection = sharedPreferences != null ? sharedPreferences.getString(KEY_SECTION, "") : "";
        if (savedSection != null) {
            String normalized = savedSection.trim().toUpperCase(Locale.getDefault());
            if (!normalized.isEmpty() && !"SELECT A SECTION".equals(normalized)) {
                return normalized;
            }
        }

        return null;
    }

    private void cachePendingQrResult(String qrContent, String timeSlotField, String timeSlotFriendlyName) {
        pendingQrContent = qrContent;
        pendingQrTimeSlotField = timeSlotField;
        pendingQrTimeSlotName = timeSlotFriendlyName;
    }

    private void clearPendingQrResult() {
        pendingQrContent = null;
        pendingQrTimeSlotField = null;
        pendingQrTimeSlotName = null;
    }

    private boolean tryProcessPendingQrResult() {
        if (pendingQrContent == null || pendingQrTimeSlotField == null || pendingQrTimeSlotName == null) {
            return false;
        }
        if (resolveSelectedSection() == null) {
            return false;
        }

        String qrContent = pendingQrContent;
        String timeSlotField = pendingQrTimeSlotField;
        String timeSlotFriendlyName = pendingQrTimeSlotName;
        clearPendingQrResult();
        handleScanResult(qrContent, timeSlotField, timeSlotFriendlyName);
        return true;
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

    private String getSelectedTimeSlotFriendlyName() {
        int selectedId = amRadioGroup.getCheckedRadioButtonId();
        if (selectedId == -1) selectedId = pmRadioGroup.getCheckedRadioButtonId();
        if (selectedId == -1) return null;

        RadioButton selected = findViewById(selectedId);
        return selected != null ? selected.getText().toString() : null;
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
                            uploadData.put("version", "6.1");
                            transaction.set(docRef, uploadData, SetOptions.merge());
                        }
                        return null;
                    }).addOnSuccessListener(unused -> db.markAsSynced(record.getId()))
                    .addOnFailureListener(e -> Log.e("MainActivity", "DEBUG: Failed to sync record " + record.getId(), e));
        }
    }

    private boolean shouldSyncField(Map<String, Object> existing, String key, String localValue) {
        if (localValue == null || localValue.equals("-")) return false;
        return !existing.containsKey(key) || existing.get(key) == null || Objects.equals(existing.get(key), "-");
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) return false;
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }

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
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            view.setPadding(0, 0, 0, bottom);
            return insets;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkChangeReceiver != null) {
            try {
                unregisterReceiver(networkChangeReceiver);
            } catch (IllegalArgumentException ignored) {
                Log.w("MainActivity", "DEBUG: networkChangeReceiver was already unregistered");
            }
        }
    }

    private void showConfirmationDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(new androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_QRAttendanceControl));
        builder.setTitle(title).setMessage(message).setPositiveButton("OK", (dialog, which) -> dialog.dismiss()).setCancelable(false).show();
    }

    private void showSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        if (rootView == null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }

        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getColor(R.color.md_theme_secondary))
                .setTextColor(getColor(R.color.white))
                .show();
    }

    private void clearNfcPendingState() {
        nfcModeActive = false;
        pendingNfcTimeSlotField = null;
        pendingNfcTimeSlotName = null;
        sharedPreferences.edit()
                .putBoolean(KEY_NFC_MODE_ACTIVE, false)
                .remove(KEY_PENDING_NFC_TIME_SLOT_FIELD)
                .remove(KEY_PENDING_NFC_TIME_SLOT_NAME)
                .apply();
    }

    private void updateDateTimeLabels() {
        Date now = new Date();
        timeText.setText("Time: " + displayTimeFormat.format(now));
        dateText.setText("Date: " + displayDateFormat.format(now));
    }
}

