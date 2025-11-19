package cics.csup.qrattendancecontrol;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AttendanceRecord {

    private int id;
    private String name;
    private String date; // stored as yyyy-MM-dd for consistency
    private String timeInAM;
    private String timeOutAM;
    private String timeInPM;
    private String timeOutPM;
    private String section;
    private boolean synced;
    private String studentID;
    private boolean isHidden;

    public AttendanceRecord(int id, String name, String studentID, String date, String timeInAM, String timeOutAM,
                            String timeInPM, String timeOutPM, String section) {
        this.id = id;
        this.name = name;
        this.studentID = studentID;
        this.date = date;
        this.timeInAM = timeInAM;
        this.timeOutAM = timeOutAM;
        this.timeInPM = timeInPM;
        this.timeOutPM = timeOutPM;
        this.section = section;
        this.synced = false;
        this.isHidden = false;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getStudentID() { return studentID; }
    public String getDate() { return date; }
    public String getTimeInAM() { return timeInAM; }
    public String getTimeOutAM() { return timeOutAM; }
    public String getTimeInPM() { return timeInPM; }
    public String getTimeOutPM() { return timeOutPM; }
    public String getSection() { return section; }
    public boolean isSynced() { return synced; }
    public boolean isHidden() { return isHidden; }

    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setStudentID(String studentID) { this.studentID = studentID; }
    public void setDate(String date) { this.date = date; }
    public void setTimeInAM(String timeInAM) { this.timeInAM = timeInAM; }
    public void setTimeOutAM(String timeOutAM) { this.timeOutAM = timeOutAM; }
    public void setTimeInPM(String timeInPM) { this.timeInPM = timeInPM; }
    public void setTimeOutPM(String timeOutPM) { this.timeOutPM = timeOutPM; }
    public void setSection(String section) { this.section = section; }
    public void setSynced(boolean synced) { this.synced = synced; }
    public void setHidden(boolean hidden) { isHidden = hidden; }

    // Compatibility aliases
    public String getTimeInAm() { return timeInAM; }
    public String getTimeOutAm() { return timeOutAM; }
    public String getTimeInPm() { return timeInPM; }
    public String getTimeOutPm() { return timeOutPM; }

    public void setField(String field, String value) {
        switch (field) {
            case "time_in_am": setTimeInAM(value); break;
            case "time_out_am": setTimeOutAM(value); break;
            case "time_in_pm": setTimeInPM(value); break;
            case "time_out_pm": setTimeOutPM(value); break;
        }
    }

    public String getFieldValue(String field) {
        String value;
        switch (field) {
            case "time_in_am": value = timeInAM; break;
            case "time_out_am": value = timeOutAM; break;
            case "time_in_pm": value = timeInPM; break;
            case "time_out_pm": value = timeOutPM; break;
            default: return "-";
        }
        if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("null")) {
            return "-";
        }
        return value;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("studentID", studentID);
        map.put("date", date);
        map.put("section", section);
        map.put("time_in_am", timeInAM);
        map.put("time_out_am", timeOutAM);
        map.put("time_in_pm", timeInPM);
        map.put("time_out_pm", timeOutPM);
        return map;
    }

    public String getIdHash() {
        String safeID = studentID != null ? studentID.replaceAll("[^a-zA-Z0-9-]", "_") : "unknown";
        String safeSection = section != null ? section.replaceAll("\\s+", "_").toLowerCase(Locale.getDefault()) : "nosection";
        String safeDate = date != null ? date.replaceAll("\\s+", "_").toLowerCase(Locale.getDefault()) : "nodate";
        return safeID + "_" + safeDate + "_" + safeSection;
    }
}