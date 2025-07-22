package cics.csup.qrattendancecontrol;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class AttendanceDBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "attendance.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_NAME = "attendance";

    public AttendanceDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT," +
                "date TEXT," +
                "section TEXT," +
                "time_in_am TEXT," +
                "time_out_am TEXT," +
                "time_in_pm TEXT," +
                "time_out_pm TEXT," +
                "synced INTEGER DEFAULT 0" +
                ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    // ✅ Insert or update attendance
    public void markDetailedAttendance(String name, String date, String section, String field, String timeValue) {
        SQLiteDatabase db = this.getWritableDatabase();

        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE name=? AND date=? AND section=?",
                new String[]{name, date, section});

        if (cursor.moveToFirst()) {
            String outAM = cursor.getString(cursor.getColumnIndexOrThrow("time_out_am"));
            String outPM = cursor.getString(cursor.getColumnIndexOrThrow("time_out_pm"));
            String inPM = cursor.getString(cursor.getColumnIndexOrThrow("time_in_pm"));
            String existingFieldValue = cursor.getString(cursor.getColumnIndexOrThrow(field));

            // Block overwriting any filled field
            if (existingFieldValue != null && !existingFieldValue.equals("-")) {
                cursor.close();
                db.close();
                return;
            }

            // Prevent AM actions if PM time_in already exists
            if ((field.equals("time_in_am") || field.equals("time_out_am")) &&
                    inPM != null && !inPM.equals("-")) {
                cursor.close();
                db.close();
                return;
            }

            // Prevent time_in if corresponding time_out is already filled
            if (field.equals("time_in_am") && outAM != null && !outAM.equals("-")) {
                cursor.close();
                db.close();
                return;
            }

            if (field.equals("time_in_pm") && outPM != null && !outPM.equals("-")) {
                cursor.close();
                db.close();
                return;
            }

            // Safe to update
            String updateSQL = "UPDATE " + TABLE_NAME + " SET " + field + "=?, synced=0 WHERE name=? AND date=? AND section=?";
            db.execSQL(updateSQL, new Object[]{timeValue, name, date, section});

        } else {
            // Create new record
            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("date", date);
            values.put("section", section);
            values.put("time_in_am", "-");
            values.put("time_out_am", "-");
            values.put("time_in_pm", "-");
            values.put("time_out_pm", "-");
            values.put(field, timeValue);
            values.put("synced", 0);
            db.insert(TABLE_NAME, null, values);
        }

        cursor.close();
        db.close();
    }


    // ✅ Fetch record for validation
    public AttendanceRecord getRecordByNameDateSection(String name, String date, String section) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE name=? AND date=? AND section=?",
                new String[]{name, date, section});

        AttendanceRecord record = null;

        if (cursor.moveToFirst()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
            String inAM = cursor.getString(cursor.getColumnIndexOrThrow("time_in_am"));
            String outAM = cursor.getString(cursor.getColumnIndexOrThrow("time_out_am"));
            String inPM = cursor.getString(cursor.getColumnIndexOrThrow("time_in_pm"));
            String outPM = cursor.getString(cursor.getColumnIndexOrThrow("time_out_pm"));

            record = new AttendanceRecord(id, name, date, inAM, outAM, inPM, outPM, section);
        }

        cursor.close();
        db.close();
        return record;
    }

    // ✅ Mark record as synced
    public void markAsSynced(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("synced", 1);
        db.update(TABLE_NAME, values, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    // ✅ Get all unsynced records
    public List<AttendanceRecord> getUnsyncedRecords() {
        List<AttendanceRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE synced = 0", null);

        if (cursor.moveToFirst()) {
            do {
                records.add(extractRecordFromCursor(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return records;
    }

    // ✅ Get all records
    public List<AttendanceRecord> getAttendanceRecords() {
        List<AttendanceRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY id DESC", null);

        if (cursor.moveToFirst()) {
            do {
                records.add(extractRecordFromCursor(cursor));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return records;
    }

    // ✅ Clear all
    public void clearAllAttendance() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
        db.close();
    }

    // ✅ Delete one by ID
    public void deleteAttendanceById(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, "id = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    // 🔧 Extract helper
    private AttendanceRecord extractRecordFromCursor(Cursor cursor) {
        int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
        String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
        String section = cursor.getString(cursor.getColumnIndexOrThrow("section"));
        String inAM = cursor.getString(cursor.getColumnIndexOrThrow("time_in_am"));
        String outAM = cursor.getString(cursor.getColumnIndexOrThrow("time_out_am"));
        String inPM = cursor.getString(cursor.getColumnIndexOrThrow("time_in_pm"));
        String outPM = cursor.getString(cursor.getColumnIndexOrThrow("time_out_pm"));

        return new AttendanceRecord(id, name, date, inAM, outAM, inPM, outPM, section);
    }
}
