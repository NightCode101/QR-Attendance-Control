package cics.csup.qrattendancecontrol;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class AdminCacheDBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "admin_cache.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_NAME = "admin_cache";

    private static final String COL_ID = "id";
    private static final String COL_NAME = "name";
    private static final String COL_STUDENT_ID = "studentID";
    private static final String COL_DATE = "date";
    private static final String COL_SECTION = "section";
    private static final String COL_TIME_IN_AM = "time_in_am";
    private static final String COL_TIME_OUT_AM = "time_out_am";
    private static final String COL_TIME_IN_PM = "time_in_pm";
    private static final String COL_TIME_OUT_PM = "time_out_pm";

    public AdminCacheDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String create = "CREATE TABLE " + TABLE_NAME + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NAME + " TEXT, "
                + COL_STUDENT_ID + " TEXT, "
                + COL_DATE + " TEXT, "
                + COL_SECTION + " TEXT, "
                + COL_TIME_IN_AM + " TEXT, "
                + COL_TIME_OUT_AM + " TEXT, "
                + COL_TIME_IN_PM + " TEXT, "
                + COL_TIME_OUT_PM + " TEXT)";
        db.execSQL(create);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_STUDENT_ID + " TEXT"); } catch (Exception ignored) {}
        }
    }

    public void insertOrUpdate(AttendanceRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM " + TABLE_NAME +
                        " WHERE " + COL_STUDENT_ID + "=? AND " + COL_DATE + "=? AND " + COL_SECTION + "=?",
                new String[]{record.getStudentID(), record.getDate(), record.getSection()});
        boolean exists = cursor.moveToFirst();
        cursor.close();

        ContentValues cv = new ContentValues();
        cv.put(COL_NAME, record.getName());
        cv.put(COL_STUDENT_ID, record.getStudentID());
        cv.put(COL_DATE, record.getDate());
        cv.put(COL_SECTION, record.getSection());
        cv.put(COL_TIME_IN_AM, record.getTimeInAM());
        cv.put(COL_TIME_OUT_AM, record.getTimeOutAM());
        cv.put(COL_TIME_IN_PM, record.getTimeInPM());
        cv.put(COL_TIME_OUT_PM, record.getTimeOutPM());

        if (exists) {
            db.update(TABLE_NAME, cv,
                    COL_STUDENT_ID + "=? AND " + COL_DATE + "=? AND " + COL_SECTION + "=?",
                    new String[]{record.getStudentID(), record.getDate(), record.getSection()});
        } else {
            db.insert(TABLE_NAME, null, cv);
        }
    }

    public List<AttendanceRecord> getAllRecords() {
        List<AttendanceRecord> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY date DESC", null);
        while (c.moveToNext()) {
            list.add(new AttendanceRecord(
                    c.getInt(c.getColumnIndexOrThrow(COL_ID)),
                    c.getString(c.getColumnIndexOrThrow(COL_NAME)),
                    c.getString(c.getColumnIndexOrThrow(COL_STUDENT_ID)),
                    c.getString(c.getColumnIndexOrThrow(COL_DATE)),
                    c.getString(c.getColumnIndexOrThrow(COL_TIME_IN_AM)),
                    c.getString(c.getColumnIndexOrThrow(COL_TIME_OUT_AM)),
                    c.getString(c.getColumnIndexOrThrow(COL_TIME_IN_PM)),
                    c.getString(c.getColumnIndexOrThrow(COL_TIME_OUT_PM)),
                    c.getString(c.getColumnIndexOrThrow(COL_SECTION))
            ));
        }
        c.close();
        return list;
    }

    public void clearCache() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_NAME);
    }

    public void deleteByNameDateSection(String studentID, String date, String section) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME,
                COL_STUDENT_ID + "=? AND " + COL_DATE + "=? AND " + COL_SECTION + "=?",
                new String[]{studentID, date, section});
    }
}