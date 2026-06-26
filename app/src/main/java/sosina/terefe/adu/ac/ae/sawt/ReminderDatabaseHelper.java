package sosina.terefe.adu.ac.ae.sawt;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class ReminderDatabaseHelper extends SQLiteOpenHelper {

    private static final String DBNAME = "ReminderDB";
    private static final String REMINDER_TABLE = "reminders";
    private static final int VERSION = 2; // ← bumped from 1
    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String TIME = "time";
    private static final String FREQUENCY = "frequency";
    private static final String IS_DONE = "is_done";
    private static final String PATIENT_NAME = "patient_name";
    private static final String TRIGGER_AT = "trigger_at"; // ← new column

    public ReminderDatabaseHelper(Context context) {
        super(context, DBNAME, null, VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        String sqlCreate = "CREATE TABLE " + REMINDER_TABLE;
        sqlCreate += " (";
        sqlCreate += ID + " INTEGER PRIMARY KEY AUTOINCREMENT, ";
        sqlCreate += TITLE + " TEXT, ";
        sqlCreate += TIME + " TEXT, ";
        sqlCreate += FREQUENCY + " TEXT, ";
        sqlCreate += IS_DONE + " INTEGER, ";
        sqlCreate += PATIENT_NAME + " TEXT, ";
        sqlCreate += TRIGGER_AT + " INTEGER DEFAULT 0";
        sqlCreate += ")";
        db.execSQL(sqlCreate);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + REMINDER_TABLE
                    + " ADD COLUMN " + TRIGGER_AT + " INTEGER DEFAULT 0");
        }
    }

    /** Returns the new row's ID, so the caller can schedule its alarm immediately. */
    public long insert(Reminder reminder) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TITLE, reminder.getTitle());
        values.put(TIME, reminder.getTime());
        values.put(FREQUENCY, reminder.getFrequency());
        values.put(IS_DONE, reminder.getIsDone());
        values.put(PATIENT_NAME, reminder.getPatientName());
        values.put(TRIGGER_AT, reminder.getTriggerAtMillis());
        long newId = db.insert(REMINDER_TABLE, null, values);
        db.close();
        return newId;
    }

    public void deleteById(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(REMINDER_TABLE, ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void updateDone(int id, int isDone) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(IS_DONE, isDone);
        db.update(REMINDER_TABLE, values, ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    /** Called by ReminderAlarmReceiver after firing a daily/weekly reminder, to log the next occurrence. */
    public void updateTriggerAt(int id, long triggerAtMillis) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(TRIGGER_AT, triggerAtMillis);
        db.update(REMINDER_TABLE, values, ID + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public ArrayList<Reminder> selectAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.query(REMINDER_TABLE, null, null, null, null, null, TRIGGER_AT + " ASC");
        ArrayList<Reminder> reminders = new ArrayList<>();
        while (cursor.moveToNext()) {
            Reminder currentReminder = new Reminder(
                    cursor.getInt(cursor.getColumnIndexOrThrow(ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(TITLE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(TIME)),
                    cursor.getString(cursor.getColumnIndexOrThrow(FREQUENCY)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(IS_DONE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(PATIENT_NAME)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(TRIGGER_AT)));
            reminders.add(currentReminder);
        }
        cursor.close();
        db.close();
        return reminders;
    }
}