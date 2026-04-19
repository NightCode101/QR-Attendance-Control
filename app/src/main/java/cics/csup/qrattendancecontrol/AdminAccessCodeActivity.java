package cics.csup.qrattendancecontrol;

import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.functions.FirebaseFunctions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminAccessCodeActivity extends AppCompatActivity {

    private EditText codeInput;
    private EditText maxDevicesInput;
    private TextView expiryInput;
    private TextView listEmptyText;
    private View progressBar;
    private long expiresAtMillis;
    private FirebaseFunctions functions;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private AdView bannerAdView;
    private RecyclerView codesRecyclerView;
    private CodesAdapter codesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_access_code);

        functions = FirebaseFunctions.getInstance();


        codeInput = findViewById(R.id.codeInput);
        maxDevicesInput = findViewById(R.id.maxDevicesInput);
        expiryInput = findViewById(R.id.expiryInput);
        listEmptyText = findViewById(R.id.codesEmptyText);
        progressBar = findViewById(R.id.loadingIndicator);
        bannerAdView = findViewById(R.id.bannerAdView);
        codesRecyclerView = findViewById(R.id.codesRecyclerView);

        codesAdapter = new CodesAdapter();
        codesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        codesRecyclerView.setAdapter(codesAdapter);

        Button createButton = findViewById(R.id.createCodeButton);
        Button refreshListButton = findViewById(R.id.refreshCodesButton);

        expiresAtMillis = getDefaultExpiryMillis();
        expiryInput.setText(dateFormat.format(new Date(expiresAtMillis)));
        maxDevicesInput.setText("20");

        expiryInput.setOnClickListener(v -> showDatePicker());
        createButton.setOnClickListener(v -> createOrUpdateCode());
        refreshListButton.setOnClickListener(v -> loadCodes());

        loadCodes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bannerAdView != null) {
            bannerAdView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bannerAdView != null) {
            bannerAdView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bannerAdView != null) {
            bannerAdView.destroy();
        }
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(expiresAtMillis);
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, dayOfMonth, 23, 59, 59);
            picked.set(Calendar.MILLISECOND, 0);
            expiresAtMillis = picked.getTimeInMillis();
            expiryInput.setText(dateFormat.format(new Date(expiresAtMillis)));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        picker.show();
    }

    private void createOrUpdateCode() {
        String code = normalizedCodeOrError();
        if (code == null) return;

        int maxDevices;
        try {
            maxDevices = Integer.parseInt(maxDevicesInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            showSnackbar(getString(R.string.access_code_invalid_max_devices));
            return;
        }

        if (maxDevices < 1 || maxDevices > 500) {
            showSnackbar(getString(R.string.access_code_invalid_max_devices));
            return;
        }

        if (expiresAtMillis <= System.currentTimeMillis()) {
            showSnackbar(getString(R.string.access_code_invalid_expiry));
            return;
        }

        setLoading(true);
        functions.getHttpsCallable("createAccessCode")
                .call(Map.of(
                        "code", code,
                        "maxDevices", maxDevices,
                        "expiresAtMs", expiresAtMillis
                ))
                .addOnSuccessListener(result -> {
                    setLoading(false);
                    showSnackbar(getString(R.string.access_code_saved));
                    checkCodeStatus(code);
                    loadCodes();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showSnackbar(errorMessage(e, getString(R.string.access_code_operation_failed)));
                });
    }

    private void checkCodeStatus() {
        String code = normalizedCodeOrError();
        if (code == null) return;
        checkCodeStatus(code);
    }

    private void checkCodeStatus(String code) {
        codeInput.setText(code);
        if (code == null) return;

        setLoading(true);
        functions.getHttpsCallable("getAccessCodeStatus")
                .call(Map.of("code", code))
                .addOnSuccessListener(result -> {
                    setLoading(false);
                    Object raw = result.getData();
                    if (!(raw instanceof Map<?, ?> data)) {
                        showSnackbar(getString(R.string.access_code_status_invalid));
                        return;
                    }

                    boolean exists = Boolean.TRUE.equals(data.get("exists"));
                    if (!exists) {
                        showSnackbar(getString(R.string.access_code_not_found));
                        return;
                    }

                    String status = String.valueOf(data.get("status"));
                    boolean revoked = Boolean.TRUE.equals(data.get("revoked"));
                    int maxDevices = intValue(data.get("maxDevices"), 20);
                    int activeDevices = intValue(data.get("activeDevices"), 0);
                    int revokedDevices = intValue(data.get("revokedDevices"), 0);
                    long expires = longValue(data.get("expiresAtMs"));
                    long lastUsed = longValue(data.get("lastUsedAtMs"));

                    String detail = "Status: " + status
                            + " | Active: " + activeDevices + "/" + maxDevices
                            + " | Expires: " + formatTimestamp(expires)
                            + (revoked ? " | Revoked" : "");
                    showSnackbar(detail);
                    loadCodes();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showSnackbar(errorMessage(e, getString(R.string.access_code_operation_failed)));
                });
    }

    private void revokeCode() {
        String code = normalizedCodeOrError();
        if (code == null) return;
        revokeCode(code);
    }

    private void revokeCode(String code) {
        codeInput.setText(code);
        if (code == null) return;

        setLoading(true);
        functions.getHttpsCallable("revokeAccessCode")
                .call(Map.of("code", code))
                .addOnSuccessListener(result -> {
                    setLoading(false);
                    showSnackbar(getString(R.string.access_code_revoked));
                    loadCodes();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showSnackbar(errorMessage(e, getString(R.string.access_code_operation_failed)));
                });
    }

    private void confirmRevokeCode(String code) {
        if (code == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.access_code_confirm_revoke_title)
                .setMessage(getString(R.string.access_code_confirm_revoke_message, code))
                .setPositiveButton(R.string.access_code_revoke_short, (dialog, which) -> revokeCode(code))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteCode(String code) {
        codeInput.setText(code);
        if (code == null) return;

        setLoading(true);
        functions.getHttpsCallable("deleteAccessCode")
                .call(Map.of("code", code))
                .addOnSuccessListener(result -> {
                    setLoading(false);
                    showSnackbar(getString(R.string.access_code_deleted));
                    String currentCode = codeInput.getText().toString().trim().toUpperCase(Locale.US);
                    if (code.equals(currentCode)) {
                        codeInput.setText("");
                    }
                    loadCodes();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showSnackbar(errorMessage(e, getString(R.string.access_code_operation_failed)));
                });
    }

    private void confirmDeleteCode(String code) {
        if (code == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.access_code_confirm_delete_title)
                .setMessage(getString(R.string.access_code_confirm_delete_message, code))
                .setPositiveButton(R.string.access_code_delete_short, (dialog, which) -> deleteCode(code))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadCodes() {
        functions.getHttpsCallable("listAccessCodes")
                .call(Map.of("limit", 30))
                .addOnSuccessListener(result -> {
                    Object raw = result.getData();
                    if (!(raw instanceof Map<?, ?> data)) {
                        listEmptyText.setVisibility(View.VISIBLE);
                        codesAdapter.submit(new ArrayList<>());
                        return;
                    }
                    Object codesObj = data.get("codes");
                    if (!(codesObj instanceof List<?> codes) || codes.isEmpty()) {
                        listEmptyText.setVisibility(View.VISIBLE);
                        codesAdapter.submit(new ArrayList<>());
                        return;
                    }

                    List<AccessCodeListItem> items = new ArrayList<>();
                    for (Object item : codes) {
                        if (!(item instanceof Map<?, ?> codeMap)) continue;
                        String code = String.valueOf(codeMap.get("code"));
                        String status = String.valueOf(codeMap.get("status"));
                        int maxDevices = intValue(codeMap.get("maxDevices"), 20);
                        long expires = longValue(codeMap.get("expiresAtMs"));
                        items.add(new AccessCodeListItem(code, status, maxDevices, expires));
                    }

                    listEmptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    codesAdapter.submit(items);
                })
                .addOnFailureListener(e -> {
                    listEmptyText.setVisibility(View.VISIBLE);
                    listEmptyText.setText(errorMessage(e, getString(R.string.access_code_operation_failed)));
                    codesAdapter.submit(new ArrayList<>());
                });
    }

    private String normalizedCodeOrError() {
        String code = codeInput.getText().toString().trim().toUpperCase(Locale.US).replaceAll("\\s+", "");
        if (code.isEmpty()) {
            showSnackbar(getString(R.string.access_code_required));
            return null;
        }
        return code;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private String formatTimestamp(long millis) {
        if (millis <= 0L) {
            return getString(R.string.access_code_not_available);
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    private long getDefaultExpiryMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 30);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String errorMessage(Throwable throwable, String fallback) {
        String message = throwable.getMessage();
        return TextUtils.isEmpty(message) ? fallback : message;
    }

    private void showSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        if (rootView == null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }

        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getColor(R.color.md_theme_secondary))
                .setTextColor(getColor(R.color.white))
                .show();
    }

    private void useForUpdate(AccessCodeListItem item) {
        codeInput.setText(item.code);
        maxDevicesInput.setText(String.valueOf(item.maxDevices));
        if (item.expiresAtMs > 0L) {
            expiresAtMillis = item.expiresAtMs;
            expiryInput.setText(dateFormat.format(new Date(expiresAtMillis)));
        }
        showSnackbar(getString(R.string.access_code_loaded_for_update));
    }

    private class CodesAdapter extends RecyclerView.Adapter<CodesAdapter.CodeViewHolder> {
        private final List<AccessCodeListItem> items = new ArrayList<>();

        void submit(List<AccessCodeListItem> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @Override
        public CodeViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_admin_access_code, parent, false);
            return new CodeViewHolder(view);
        }

        @Override
        public void onBindViewHolder(CodeViewHolder holder, int position) {
            AccessCodeListItem item = items.get(position);
            holder.codeText.setText(item.code);
            holder.metaText.setText(getString(R.string.access_codes_list_meta, item.status, item.maxDevices, formatTimestamp(item.expiresAtMs)));

            holder.statusButton.setOnClickListener(v -> checkCodeStatus(item.code));
            holder.revokeButton.setOnClickListener(v -> confirmRevokeCode(item.code));
            holder.deleteButton.setOnClickListener(v -> confirmDeleteCode(item.code));
            holder.useButton.setOnClickListener(v -> useForUpdate(item));
            holder.itemView.setOnClickListener(v -> checkCodeStatus(item.code));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class CodeViewHolder extends RecyclerView.ViewHolder {
            final TextView codeText;
            final TextView metaText;
            final com.google.android.material.button.MaterialButton statusButton;
            final com.google.android.material.button.MaterialButton revokeButton;
            final com.google.android.material.button.MaterialButton deleteButton;
            final com.google.android.material.button.MaterialButton useButton;

            CodeViewHolder(View itemView) {
                super(itemView);
                codeText = itemView.findViewById(R.id.itemCodeText);
                metaText = itemView.findViewById(R.id.itemMetaText);
                statusButton = itemView.findViewById(R.id.itemStatusButton);
                revokeButton = itemView.findViewById(R.id.itemRevokeButton);
                deleteButton = itemView.findViewById(R.id.itemDeleteButton);
                useButton = itemView.findViewById(R.id.itemUseButton);
            }
        }
    }

    private static class AccessCodeListItem {
        final String code;
        final String status;
        final int maxDevices;
        final long expiresAtMs;

        AccessCodeListItem(String code, String status, int maxDevices, long expiresAtMs) {
            this.code = code;
            this.status = status;
            this.maxDevices = maxDevices;
            this.expiresAtMs = expiresAtMs;
        }
    }
}


