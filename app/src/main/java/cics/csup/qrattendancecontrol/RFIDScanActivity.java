package cics.csup.qrattendancecontrol;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.button.MaterialButton;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class RFIDScanActivity extends AppCompatActivity {

    private static final long DUPLICATE_SCAN_COOLDOWN_MS = 1200L;

    private AttendanceDBHelper db;
    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;

    private TextView statusText;
    private TextView hintText;
    private MaterialButton continueScanButton;

    private String section;
    private String timeSlotField;
    private String timeSlotName;

    private boolean continueScanning = true;
    private long lastScanTimeMs = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rfid_scan);

        db = new AttendanceDBHelper(this);

        hintText = findViewById(R.id.rfidHintText);
        statusText = findViewById(R.id.scanStatusText);
        continueScanButton = findViewById(R.id.continueScanButton);
        MaterialButton doneButton = findViewById(R.id.doneButton);

        section = getIntent().getStringExtra("SECTION");
        timeSlotField = getIntent().getStringExtra("TIME_SLOT_FIELD");
        timeSlotName = getIntent().getStringExtra("TIME_SLOT_NAME");

        if (section == null || section.trim().isEmpty() || timeSlotField == null || timeSlotField.trim().isEmpty() || timeSlotName == null || timeSlotName.trim().isEmpty()) {
            showBlockingDialog("RFID Setup Error", "Missing scan context. Please return to home and select section/slot again.", true);
            return;
        }

        continueScanButton.setOnClickListener(v -> {
            continueScanning = !continueScanning;
            updateContinueScanButton();
        });

        doneButton.setOnClickListener(v -> finish());

        updateContinueScanButton();
        statusText.setText(getString(R.string.rfid_tap_card));

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            showBlockingDialog("NFC Not Supported", "This device does not support NFC.", true);
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            showBlockingDialog("NFC Disabled", "Please enable NFC in device settings and try again.", true);
            return;
        }

        Intent nfcIntent = new Intent(this, RFIDScanActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingFlags |= PendingIntent.FLAG_MUTABLE;
        }
        pendingIntent = PendingIntent.getActivity(this, 0, nfcIntent, pendingFlags);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && pendingIntent != null) {
            try {
                nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
            } catch (Exception e) {
                Log.e("RFIDScanActivity", "Failed to enable foreground dispatch", e);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableForegroundDispatch(this);
            } catch (Exception e) {
                Log.e("RFIDScanActivity", "Failed to disable foreground dispatch", e);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (intent == null) return;
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) return;

        long now = System.currentTimeMillis();
        if (now - lastScanTimeMs < DUPLICATE_SCAN_COOLDOWN_MS) {
            return;
        }
        lastScanTimeMs = now;

        processTag(tag);
    }

    private void processTag(Tag tag) {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) {
            updateInlineStatus("Invalid card. Use a card with NDEF student data (ID|Name).", false);
            return;
        }

        try {
            ndef.connect();
            android.nfc.NdefMessage message = ndef.getNdefMessage();
            if (message == null) {
                updateInlineStatus("Card has no readable NDEF message.", false);
                return;
            }

            String data = readNdefMessage(message);
            if (data == null || data.trim().isEmpty()) {
                updateInlineStatus("Card does not contain valid student data (ID|Name).", false);
                return;
            }

            handleRfidPayload(data);
        } catch (Exception e) {
            Log.e("RFIDScanActivity", "NFC read failed", e);
            updateInlineStatus("Unable to read this NFC card. Please try again.", false);
        } finally {
            try {
                ndef.close();
            } catch (Exception ignored) {
            }
        }
    }

    private String readNdefMessage(android.nfc.NdefMessage ndefMessage) {
        for (android.nfc.NdefRecord record : ndefMessage.getRecords()) {
            int tnf = record.getTnf();
            byte[] type = record.getType();
            byte[] payload = record.getPayload();
            if (payload == null || payload.length == 0) continue;

            if (tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN && Arrays.equals(type, android.nfc.NdefRecord.RTD_TEXT)) {
                int languageCodeLength = payload[0] & 0x3F;
                if (payload.length < languageCodeLength + 1) continue;
                Charset charset = ((payload[0] & 0x80) == 0) ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16;
                return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, charset);
            }

            if (tnf == android.nfc.NdefRecord.TNF_MIME_MEDIA) {
                return new String(payload, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void handleRfidPayload(String rawPayload) {
        String payload = sanitizeScanPayload(rawPayload);
        String[] parts = payload.split("\\|", 2);
        if (parts.length < 2) {
            updateInlineStatus("Invalid card format. Expected: ID|Name", false);
            return;
        }

        String studentID = normalizeStudentId(parts[0]);
        String studentName = normalizeStudentName(parts[1]);
        if (studentID.isEmpty() || studentName.isEmpty()) {
            updateInlineStatus("Invalid card format. Expected: ID|Name", false);
            return;
        }

        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        AttendanceRecord idConflict = db.findNameIdConflict(studentName, date, section, studentID);
        if (idConflict != null) {
            updateInlineStatus(
                    "ID mismatch detected. Existing ID: " + idConflict.getStudentID() + " / Scanned ID: " + studentID,
                    false
            );
            return;
        }

        AttendanceRecord existing = db.getRecordByStudentID(studentID, date, section);
        if (!validateScan(timeSlotField, existing)) {
            return;
        }

        String currentTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
        db.markDetailedAttendance(studentID, studentName, date, section, timeSlotField, currentTime);

        updateInlineStatus(
                "Attendance recorded for:\n\n"
                        + "Name: " + studentName + "\n"
                        + "ID Number: " + studentID + "\n"
                        + "Slot: " + timeSlotName,
                true
        );

        if (!continueScanning) {
            finish();
        }
    }

    private boolean validateScan(String field, AttendanceRecord record) {
        if (record == null) return true;

        String existing = record.getFieldValue(field);
        if (existing != null && !"-".equals(existing)) {
            updateInlineStatus("That time slot is already scanned.", false);
            return false;
        }
        if ("time_in_am".equals(field) && !"-".equals(record.getFieldValue("time_out_am"))) {
            updateInlineStatus("You cannot Time In AM. Already timed out AM.", false);
            return false;
        }
        if ("time_in_pm".equals(field) && !"-".equals(record.getFieldValue("time_out_pm"))) {
            updateInlineStatus("You cannot Time In PM. Already timed out PM.", false);
            return false;
        }
        boolean hasPmRecord = !"-".equals(record.getFieldValue("time_in_pm")) || !"-".equals(record.getFieldValue("time_out_pm"));
        if (field.contains("am") && hasPmRecord) {
            updateInlineStatus("Cannot record AM attendance. PM attendance has already started.", false);
            return false;
        }
        return true;
    }

    private void updateContinueScanButton() {
        continueScanButton.setText(continueScanning
                ? getString(R.string.continue_scanning_on)
                : getString(R.string.continue_scanning_off));
    }

    private void updateInlineStatus(String message, boolean success) {
        hintText.setText(success ? getString(R.string.rfid_tap_card) : "Try another card.");
        statusText.setText(message);
        statusText.setTextColor(getColor(success ? R.color.md_theme_primary : R.color.md_theme_error));
    }

    private String sanitizeScanPayload(String raw) {
        if (raw == null) return "";
        return raw.replace("\uFEFF", "")
                .replace("\u0000", "")
                .trim();
    }

    private String normalizeStudentId(String id) {
        String cleaned = sanitizeScanPayload(id).replaceAll("\\s+", "");
        return cleaned.toUpperCase(Locale.getDefault());
    }

    private String normalizeStudentName(String name) {
        return sanitizeScanPayload(name).replaceAll("\\s{2,}", " ");
    }

    private void showBlockingDialog(String title, String message, boolean finishOnClose) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> {
                    d.dismiss();
                    if (finishOnClose) finish();
                })
                .create();
        dialog.show();
    }

}


