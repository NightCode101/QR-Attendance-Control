package cics.csup.qrattendancecontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AccessControlManager {

    private static final String PREFS_NAME = "AccessControlPrefs";
    private static final String KEY_GRANTED = "granted";
    private static final String KEY_CODE = "code";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_LAST_VERIFIED_AT = "last_verified_at";
    private static final String KEY_REVOKED = "revoked";
    private static final long OFFLINE_GRACE_MS = 7L * 24L * 60L * 60L * 1000L;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final FirebaseFunctions functions;

    public interface Callback {
        void onResult(boolean success, @NonNull String message);
    }

    public AccessControlManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        functions = FirebaseFunctions.getInstance();
    }

    public boolean hasCachedAccess() {
        if (!prefs.getBoolean(KEY_GRANTED, false)) {
            return false;
        }
        if (prefs.getBoolean(KEY_REVOKED, false)) {
            return false;
        }

        long now = System.currentTimeMillis();
        long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L);
        if (expiresAt > 0L && now > expiresAt) {
            clearAccess();
            return false;
        }

        long lastVerifiedAt = prefs.getLong(KEY_LAST_VERIFIED_AT, 0L);
        if (lastVerifiedAt <= 0L) {
            return false;
        }

        // Allow temporary offline use for previously verified devices.
        return (now - lastVerifiedAt) <= OFFLINE_GRACE_MS;
    }

    public void verifyCurrentAccess(@NonNull Callback callback) {
        String code = prefs.getString(KEY_CODE, "");
        String cachedDeviceId = prefs.getString(KEY_DEVICE_ID, "");
        String currentDeviceId = getDeviceId();

        if (TextUtils.isEmpty(code) || TextUtils.isEmpty(cachedDeviceId) || !cachedDeviceId.equals(currentDeviceId)) {
            clearAccess();
            callback.onResult(false, "Access session missing or invalid on this device.");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("code", code);
        payload.put("deviceId", currentDeviceId);

        functions
                .getHttpsCallable("verifyAccessCodeSession")
                .call(payload)
                .addOnSuccessListener(result -> {
                    Timestamp expiresAtTs = null;
                    Object data = result.getData();
                    if (data instanceof Map<?, ?> mapData) {
                        Object rawExpiresAt = mapData.get("expiresAt");
                        if (rawExpiresAt instanceof Timestamp) {
                            expiresAtTs = (Timestamp) rawExpiresAt;
                        }
                    }
                    persistAccess(code, currentDeviceId, expiresAtTs);
                    callback.onResult(true, "Access verified.");
                })
                .addOnFailureListener(e -> {
                    if (isAccessDeniedError(e)) {
                        clearAccess();
                        callback.onResult(false, extractMessage(e, "Access verification failed."));
                        return;
                    }
                    callback.onResult(hasCachedAccess(), "Unable to verify right now.");
                });
    }

    public void activateCode(@NonNull String rawCode, @NonNull Callback callback) {
        final String code = normalizeCode(rawCode);
        if (TextUtils.isEmpty(code)) {
            callback.onResult(false, "Enter a valid access code.");
            return;
        }

        final String deviceId = getDeviceId();
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", code);
        payload.put("deviceId", deviceId);

        functions
                .getHttpsCallable("activateAccessCode")
                .call(payload)
                .addOnSuccessListener(result -> {
                    Timestamp expiresAtTs = null;
                    Object data = result.getData();
                    if (data instanceof Map<?, ?> mapData) {
                        Object rawExpiresAt = mapData.get("expiresAt");
                        if (rawExpiresAt instanceof Timestamp) {
                            expiresAtTs = (Timestamp) rawExpiresAt;
                        }
                    }
                    persistAccess(code, deviceId, expiresAtTs);
                    callback.onResult(true, "Access granted.");
                })
                .addOnFailureListener(e -> callback.onResult(false, extractMessage(e, "Access verification failed.")));
    }

    public void revokeCode(@NonNull String rawCode, @NonNull Callback callback) {
        String code = normalizeCode(rawCode);
        if (TextUtils.isEmpty(code)) {
            callback.onResult(false, "Access code is required.");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("code", code);

        functions
                .getHttpsCallable("revokeAccessCode")
                .call(payload)
                .addOnSuccessListener(result -> callback.onResult(true, "Access code revoked for all devices."))
                .addOnFailureListener(e -> callback.onResult(false, extractMessage(e, "Revoke failed.")));
    }

    public void clearAccess() {
        prefs.edit().clear().apply();
    }

    public String normalizeCode(String rawCode) {
        if (rawCode == null) {
            return "";
        }
        return rawCode.trim().toUpperCase(Locale.US).replaceAll("\\s+", "");
    }

    private void persistAccess(@NonNull String code, @NonNull String deviceId, Timestamp expiresAt) {
        long expiresAtMillis = expiresAt != null ? expiresAt.toDate().getTime() : 0L;
        prefs.edit()
                .putBoolean(KEY_GRANTED, true)
                .putString(KEY_CODE, code)
                .putString(KEY_DEVICE_ID, deviceId)
                .putBoolean(KEY_REVOKED, false)
                .putLong(KEY_EXPIRES_AT, expiresAtMillis)
                .putLong(KEY_LAST_VERIFIED_AT, System.currentTimeMillis())
                .apply();
    }

    private String getDeviceId() {
        String androidId = Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (TextUtils.isEmpty(androidId)) {
            androidId = "unknown_device";
        }
        return sha256(androidId + ":" + appContext.getPackageName());
    }

    private String sha256(@NonNull String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private boolean isAccessDeniedError(Throwable throwable) {
        if (!(throwable instanceof FirebaseFunctionsException functionsException)) {
            return false;
        }
        FirebaseFunctionsException.Code code = functionsException.getCode();
        return code == FirebaseFunctionsException.Code.PERMISSION_DENIED
                || code == FirebaseFunctionsException.Code.NOT_FOUND
                || code == FirebaseFunctionsException.Code.FAILED_PRECONDITION;
    }

    private String extractMessage(Throwable throwable, String fallback) {
        if (throwable instanceof FirebaseFunctionsException functionsException) {
            String message = functionsException.getMessage();
            if (!TextUtils.isEmpty(message)) {
                return message;
            }
        }
        String message = throwable.getMessage();
        return TextUtils.isEmpty(message) ? fallback : message;
    }
}

