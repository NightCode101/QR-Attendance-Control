package cics.csup.qrattendancecontrol;

import java.util.HashMap;
import java.util.Map;

public class AttendanceRecord {
    private int id;
    private String name;
    private String date;
    private String timeIn;
    private String timeOut;
    private String section;
    private String idHash; // Add this

    // Constructor with all fields
    public AttendanceRecord(int id, String name, String date, String timeIn, String timeOut, String section) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.timeIn = timeIn;
        this.timeOut = timeOut;
        this.section = section;
    }

    // Getters
    public int getId() {
        return id;
    }

    // Setters
    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTimeIn() {
        return timeIn;
    }

    public void setTimeIn(String timeIn) {
        this.timeIn = timeIn;
    }

    public String getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(String timeOut) {
        this.timeOut = timeOut;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    // Convert to Firestore-compatible Map
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name != null ? name : "");
        map.put("date", date != null ? date : "");
        map.put("time_in", timeIn != null ? timeIn : "");
        map.put("time_out", timeOut != null ? timeOut : "");
        map.put("section", section != null ? section : "");
        return map;
    }

    public String getIdHash() {
        return idHash;
    }

    public void setIdHash(String idHash) {
        this.idHash = idHash;
    }
}
