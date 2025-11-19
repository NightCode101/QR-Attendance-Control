package cics.csup.qrattendancecontrol;

import android.app.Activity;
import android.util.Log;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

public class ConfigHelper {

    private static final String TAG = "ConfigHelper";
    private final FirebaseRemoteConfig remoteConfig;

    public ConfigHelper() {
        remoteConfig = FirebaseRemoteConfig.getInstance();

        // Fetch interval: 43202s (12 hour) for production.
        // Use 0s during development to see changes instantly.
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(43200)
                .build();
        remoteConfig.setConfigSettingsAsync(configSettings);

        // Set defaults from the XML file we will create next
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);
    }

    public void fetchAndActivate(Activity activity, Runnable onSuccess) {
        remoteConfig.fetchAndActivate()
                .addOnCompleteListener(activity, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Remote Config updated.");
                    } else {
                        Log.e(TAG, "Remote Config update failed.");
                    }
                    // Run the callback regardless of success/failure so the app loads
                    if (onSuccess != null) onSuccess.run();
                });
    }

    public boolean isAdmin(String uid) {
        // Get the comma-separated string of UIDs
        String uidsString = remoteConfig.getString("admin_uids");
        return uidsString.contains(uid);
    }

    public List<String> getSections() {
        String jsonString = remoteConfig.getString("sections_list");
        List<String> sections = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                sections.add(jsonArray.getString(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse sections JSON", e);
            sections.add("Select a Section");
        }
        return sections;
    }
}