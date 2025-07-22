package cics.csup.qrattendancecontrol;

import java.util.HashMap;
import java.util.Map;

public class AttendanceRecord {
    private final int id;
    private final String name;
    private final String date;
    private String timeInAM;
    private String timeOutAM;
    private String timeInPM;
    private String timeOutPM;
    private final String section;
    private boolean synced;

    public AttendanceRecord(int id, String name, String date,
                            String timeInAM, String timeOutAM,
                            String timeInPM, String timeOutPM,
                            String section) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.timeInAM = timeInAM;
        this.timeOutAM = timeOutAM;
        this.timeInPM = timeInPM;
        this.timeOutPM = timeOutPM;
        this.section = section;
        this.synced = false;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("date", date);
        map.put("time_in_am", timeInAM);
        map.put("time_out_am", timeOutAM);
        map.put("time_in_pm", timeInPM);
        map.put("time_out_pm", timeOutPM);
        map.put("section", section);
        return map;
    }

    public void setField(String field, String value) {
        switch (field) {
            case "time_in_am":
                timeInAM = value;
                break;
            case "time_out_am":
                timeOutAM = value;
                break;
            case "time_in_pm":
                timeInPM = value;
                break;
            case "time_out_pm":
                timeOutPM = value;
                break;
        }
    }

    public String getField(String field) {
        switch (field) {
            case "time_in_am":
                return timeInAM;
            case "time_out_am":
                return timeOutAM;
            case "time_in_pm":
                return timeInPM;
            case "time_out_pm":
                return timeOutPM;
            default:
                return "-";
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDate() {
        return date;
    }

    public String getTimeInAM() {
        return timeInAM;
    }

    public String getTimeOutAM() {
        return timeOutAM;
    }

    public String getTimeInPM() {
        return timeInPM;
    }

    public String getTimeOutPM() {
        return timeOutPM;
    }

    public String getSection() {
        return section;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }

    // ✅ Document ID generator to avoid duplicates
    public String getIdHash() {
        return (name + "_" + date + "_" + section).replaceAll("\\s+", "_").toLowerCase();
    }
}
