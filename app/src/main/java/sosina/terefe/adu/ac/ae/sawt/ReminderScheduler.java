package sosina.terefe.adu.ac.ae.sawt;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderScheduler {

    public static void schedule(Context context, Reminder reminder) {
        if (reminder.getTriggerAtMillis() <= System.currentTimeMillis()) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return; // permission not granted yet — fragment asks for it before this is ever called
        }

        Intent intent = buildIntent(context, reminder.getId(), reminder.getTitle(),
                reminder.getPatientName(), reminder.getFrequency());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, reminder.getId(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, reminder.getTriggerAtMillis(), pendingIntent);
    }

    public static void cancel(Context context, int reminderId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, reminderId, new Intent(context, ReminderAlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
    }

    static Intent buildIntent(Context context, int id, String title, String patientName, String frequency) {
        Intent intent = new Intent(context, ReminderAlarmReceiver.class);
        intent.putExtra("reminder_id", id);
        intent.putExtra("reminder_title", title);
        intent.putExtra("reminder_patient", patientName);
        intent.putExtra("reminder_frequency", frequency);
        return intent;
    }
}