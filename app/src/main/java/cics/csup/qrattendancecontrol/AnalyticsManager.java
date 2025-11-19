package cics.csup.qrattendancecontrol;

import android.content.Context;
import android.os.Bundle;
import com.google.firebase.analytics.FirebaseAnalytics;

public class AnalyticsManager {

    private final FirebaseAnalytics firebaseAnalytics;

    public AnalyticsManager(Context context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context);
    }

    public void logScan(String studentID, String section, String timeSlot, boolean isSuccess) {
        Bundle bundle = new Bundle();
        bundle.putString("student_id", studentID);
        bundle.putString("section", section);
        bundle.putString("time_slot", timeSlot);
        bundle.putString("status", isSuccess ? "success" : "failed");

        // Log custom event "qr_scan_attempt"
        firebaseAnalytics.logEvent("qr_scan_attempt", bundle);
    }
}