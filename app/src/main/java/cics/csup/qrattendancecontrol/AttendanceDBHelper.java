package cics.csup.qrattendancecontrol;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceDBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "attendance.db";
    private static final int DB_VERSION = 2;
    private static final String TABLE_NAME = "attendance";

    public AttendanceDBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "student_id TEXT, " +
                "date TEXT, " +
                "time_in TEXT, " +
                "time_out TEXT, " +
                "section TEXT, " +
                "is_synced INTEGER DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN section TEXT");
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN is_synced INTEGER DEFAULT 0");
        }
    }

    public String markAttendance(String studentName, String mode, String section) {
        SQLiteDatabase db = this.getWritableDatabase();

        String currentDate = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(new Date());
        String now = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());

        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_NAME + " WHERE student_id = ? AND date = ?",
                new String[]{studentName, currentDate}
        );

        if (cursor.moveToFirst()) {
            String recordId = cursor.getString(cursor.getColumnIndexOrThrow("id"));
            String timeOut = cursor.getString(cursor.getColumnIndexOrThrow("time_out"));

            if ("in".equals(mode)) {
                cursor.close();
                return "Duplicate Time-In. Entry ignored.";
            } else {
                if (timeOut == null || timeOut.isEmpty()) {
                    ContentValues values = new ContentValues();
                    values.put("time_out", now);
                    values.put("section", section);
                    values.put("is_synced", 0);
                    db.update(TABLE_NAME, values, "id = ?", new String[]{recordId});
                    cursor.close();
                    return "Time-Out recorded for " + studentName;
                } else {
                    cursor.close();
                    return "Already timed out today.";
                }
            }
        } else {
            ContentValues values = new ContentValues();
            values.put("student_id", studentName);
            values.put("date", currentDate);
            values.put("section", section);
            values.put("is_synced", 0);

            if ("in".equals(mode)) {
                values.put("time_in", now);
                db.insert(TABLE_NAME, null, values);
                cursor.close();
                return "Time-In recorded for " + studentName;
            } else {
                values.put("time_in", "");
                values.put("time_out", now);
                db.insert(TABLE_NAME, null, values);
                cursor.close();
                return "⚠ Time-In not found. Time-Out recorded.";
            }
        }
    }

    public List<AttendanceRecord> getAttendanceRecords() {
        List<AttendanceRecord> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY date DESC, time_in DESC", null);

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("student_id"));
                String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
                String timeIn = cursor.getString(cursor.getColumnIndexOrThrow("time_in"));
                String timeOut = cursor.getString(cursor.getColumnIndexOrThrow("time_out"));
                String section = cursor.getString(cursor.getColumnIndexOrThrow("section"));

                if (timeIn == null || timeIn.isEmpty()) timeIn = "-";
                if (timeOut == null || timeOut.isEmpty()) timeOut = "-";

                AttendanceRecord record = new AttendanceRecord(id, name, date, timeIn, timeOut, section);
                list.add(record);
            } while (cursor.moveToNext());
        }

        cursor.close();
        return list;
    }

    public List<AttendanceRecord> getUnsyncedRecords() {
        List<AttendanceRecord> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE is_synced = 0", null);

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("student_id"));
                String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
                String timeIn = cursor.getString(cursor.getColumnIndexOrThrow("time_in"));
                String timeOut = cursor.getString(cursor.getColumnIndexOrThrow("time_out"));
                String section = cursor.getString(cursor.getColumnIndexOrThrow("section"));

                if (timeIn == null || timeIn.isEmpty()) timeIn = "-";
                if (timeOut == null || timeOut.isEmpty()) timeOut = "-";

                AttendanceRecord record = new AttendanceRecord(id, name, date, timeIn, timeOut, section);
                list.add(record);
            } while (cursor.moveToNext());
        }

        cursor.close();
        return list;
    }

    public void markAsSynced(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_synced", 1);
        db.update(TABLE_NAME, values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void deleteAttendanceById(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, "id = ?", new String[]{String.valueOf(id)});
    }

    public void clearAllAttendance() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
    }
}
