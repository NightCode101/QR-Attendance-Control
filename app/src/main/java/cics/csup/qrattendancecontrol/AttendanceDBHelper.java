package cics.csup.qrattendancecontrol;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AttendanceDBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "attendance.db";
    private static final int DATABASE_VERSION = 7;
    private static final String TABLE_NAME = "attendance_records";

    private static final String COL_ID = "id";
    private static final String COL_NAME = "name";
    private static final String COL_STUDENT_ID = "studentID";
    private static final String COL_DATE = "date";
    private static final String COL_TIME_IN_AM = "time_in_am";
    private static final String COL_TIME_OUT_AM = "time_out_am";
    private static final String COL_TIME_IN_PM = "time_in_pm";
    private static final String COL_TIME_OUT_PM = "time_out_pm";
    private static final String COL_SECTION = "section";
    private static final String COL_SYNCED = "synced";
    private static final String COL_IS_HIDDEN = "isHidden";

    public AttendanceDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME + " TEXT, "
                + COL_STUDENT_ID + " TEXT, "
                + COL_DATE + " TEXT, "
                + COL_TIME_IN_AM + " TEXT, "
                + COL_TIME_OUT_AM + " TEXT, "
                + COL_TIME_IN_PM + " TEXT, "
                + COL_TIME_OUT_PM + " TEXT, "
                + COL_SECTION + " TEXT, "
                + COL_SYNCED + " INTEGER DEFAULT 0, "
                + COL_IS_HIDDEN + " INTEGER DEFAULT 0, "
                + "UNIQUE (" + COL_STUDENT_ID + ", " + COL_DATE + ", " + COL_SECTION + ")"
                + ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 5) {
            try { db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN synced INTEGER DEFAULT 0"); } catch (Exception ignored) {}
        }
        if (oldVersion < 6) {
            try { db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_IS_HIDDEN + " INTEGER DEFAULT 0"); } catch (Exception ignored) {}
        }
        if (oldVersion < 7) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_STUDENT_ID + " TEXT");
                db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_STUDENT_ID + " = " + COL_NAME);
            } catch (Exception ignored) {}
        }
    }

    public void markDetailedAttendance(String studentID, String studentName, String date, String section, String field, String value) {
        SQLiteDatabase db = this.getWritableDatabase();
        String normalizedStudentId = normalizeStudentId(studentID);
        String normalizedSection = normalizeSection(section);
        String normalizedDate = normalizeDate(date);
        List<AttendanceRecord> matches = getRecordsByCanonicalKey(db, normalizedStudentId, normalizedDate, normalizedSection);
        AttendanceRecord existing = matches.isEmpty() ? null : matches.get(0);

        String mergedTimeInAm = existing != null ? existing.getTimeInAM() : "-";
        String mergedTimeOutAm = existing != null ? existing.getTimeOutAM() : "-";
        String mergedTimeInPm = existing != null ? existing.getTimeInPM() : "-";
        String mergedTimeOutPm = existing != null ? existing.getTimeOutPM() : "-";

        // Merge values from duplicates created before canonical normalization.
        for (int i = 1; i < matches.size(); i++) {
            AttendanceRecord duplicate = matches.get(i);
            if (isFilled(duplicate.getTimeInAM())) mergedTimeInAm = duplicate.getTimeInAM();
            if (isFilled(duplicate.getTimeOutAM())) mergedTimeOutAm = duplicate.getTimeOutAM();
            if (isFilled(duplicate.getTimeInPM())) mergedTimeInPm = duplicate.getTimeInPM();
            if (isFilled(duplicate.getTimeOutPM())) mergedTimeOutPm = duplicate.getTimeOutPM();
        }

        ContentValues values = new ContentValues();
        values.put(COL_NAME, studentName);
        values.put(COL_STUDENT_ID, normalizedStudentId);
        values.put(COL_DATE, normalizedDate);
        values.put(COL_SECTION, normalizedSection);
        values.put(COL_SYNCED, 0);
        values.put(COL_IS_HIDDEN, 0);
        values.put(COL_TIME_IN_AM, mergedTimeInAm);
        values.put(COL_TIME_OUT_AM, mergedTimeOutAm);
        values.put(COL_TIME_IN_PM, mergedTimeInPm);
        values.put(COL_TIME_OUT_PM, mergedTimeOutPm);
        values.put(field, value);

        if (existing == null) {
            db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        } else {
            db.update(TABLE_NAME, values, COL_ID + "=?", new String[]{String.valueOf(existing.getId())});
            for (int i = 1; i < matches.size(); i++) {
                db.delete(TABLE_NAME, COL_ID + "=?", new String[]{String.valueOf(matches.get(i).getId())});
            }
        }
        db.close();
    }

    private AttendanceRecord cursorToRecord(Cursor cursor) {
        AttendanceRecord record = new AttendanceRecord(
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_STUDENT_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME_IN_AM)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME_OUT_AM)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME_IN_PM)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME_OUT_PM)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_SECTION))
        );
        record.setSynced(cursor.getInt(cursor.getColumnIndexOrThrow(COL_SYNCED)) == 1);
        record.setHidden(cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_HIDDEN)) == 1);
        return record;
    }

    public AttendanceRecord getRecordByStudentID(String studentID, String date, String section) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<AttendanceRecord> matches = getRecordsByCanonicalKey(db,
                normalizeStudentId(studentID),
                normalizeDate(date),
                normalizeSection(section));
        return matches.isEmpty() ? null : matches.get(0);
    }

    private List<AttendanceRecord> getRecordsByCanonicalKey(SQLiteDatabase db, String studentID, String date, String section) {
        List<AttendanceRecord> records = new ArrayList<>();
        String selection =
                "UPPER(REPLACE(TRIM(" + COL_STUDENT_ID + "), ' ', '')) = ? AND " +
                "TRIM(" + COL_DATE + ") = ? AND " +
                "UPPER(TRIM(" + COL_SECTION + ")) = ?";
        String[] args = new String[]{studentID, date, section};

        Cursor cursor = db.query(TABLE_NAME, null, selection, args, null, null, COL_ID + " ASC");
        if (cursor.moveToFirst()) {
            do {
                records.add(cursorToRecord(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return records;
    }

    private String normalizeStudentId(String studentID) {
        if (studentID == null) return "";
        return studentID.replace("\uFEFF", "")
                .replace("\u0000", "")
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeSection(String section) {
        if (section == null) return "";
        return section.replace("\uFEFF", "").trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDate(String date) {
        return date == null ? "" : date.trim();
    }

    private boolean isFilled(String value) {
        return value != null && !value.trim().isEmpty() && !"-".equals(value.trim());
    }

    public AttendanceRecord findNameIdConflict(String studentName, String date, String section, String currentStudentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        AttendanceRecord conflict = null;

        String normalizedName = normalizeStudentName(studentName);
        String normalizedDate = normalizeDate(date);
        String normalizedSection = normalizeSection(section);
        String normalizedStudentId = normalizeStudentId(currentStudentId);

        Cursor cursor = db.query(
                TABLE_NAME,
                null,
                "TRIM(" + COL_DATE + ") = ? AND UPPER(TRIM(" + COL_SECTION + ")) = ?",
                new String[]{normalizedDate, normalizedSection},
                null,
                null,
                null
        );

        if (cursor.moveToFirst()) {
            do {
                AttendanceRecord record = cursorToRecord(cursor);
                String recordName = normalizeStudentName(record.getName());
                String recordStudentId = normalizeStudentId(record.getStudentID());

                if (recordName.equals(normalizedName) && !recordStudentId.equals(normalizedStudentId)) {
                    conflict = record;
                    break;
                }
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return conflict;
    }

    private String normalizeStudentName(String studentName) {
        if (studentName == null) return "";
        return studentName.replace("\uFEFF", "")
                .replace("\u0000", "")
                .trim()
                .replaceAll("\\s{2,}", " ")
                .toUpperCase(Locale.ROOT);
    }

    public List<AttendanceRecord> getUnsyncedRecords() {
        List<AttendanceRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, COL_SYNCED + "=?", new String[]{"0"},
                null, null, null);

        if (cursor.moveToFirst()) {
            do { records.add(cursorToRecord(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return records;
    }

    public void markAsSynced(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SYNCED, 1);
        db.update(TABLE_NAME, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void clearAllAttendance() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
        db.close();
    }

    public void deleteAttendanceById(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public List<AttendanceRecord> getVisibleAttendanceRecords(String nameFilter) {
        List<AttendanceRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String selection;
        String[] selectionArgs;

        if (nameFilter == null || nameFilter.trim().isEmpty()) {
            selection = COL_IS_HIDDEN + " = 0";
            selectionArgs = null;
        } else {
            selection = COL_IS_HIDDEN + " = 0 AND (" + COL_NAME + " LIKE ? OR " + COL_STUDENT_ID + " LIKE ?)";
            selectionArgs = new String[]{"%" + nameFilter + "%", "%" + nameFilter + "%"};
        }

        Cursor cursor = db.query(TABLE_NAME, null, selection, selectionArgs, null, null, COL_DATE + " DESC");
        if (cursor.moveToFirst()) {
            do { records.add(cursorToRecord(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return records;
    }

    public void setRecordHidden(int id, boolean isHidden) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_IS_HIDDEN, isHidden ? 1 : 0);
        db.update(TABLE_NAME, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void hideAllVisibleRecords() {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_IS_HIDDEN, 1);
        db.update(TABLE_NAME, values, COL_IS_HIDDEN + " = 0", null);
        db.close();
    }
}