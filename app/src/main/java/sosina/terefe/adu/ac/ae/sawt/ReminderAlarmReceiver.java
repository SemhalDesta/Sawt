package sosina.terefe.adu.ac.ae.sawt;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Calendar;

public class ReminderAlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "sawt_reminder_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        android.util.Log.d("SAWT_REMINDER", "Receiver FIRED");
        int reminderId = intent.getIntExtra("reminder_id", -1);
        String title = intent.getStringExtra("reminder_title");
        String patientName = intent.getStringExtra("reminder_patient");
        String frequency = intent.getStringExtra("reminder_frequency");

        showNotification(context, reminderId, title, patientName);

        if ("daily".equals(frequency) || "weekly".equals(frequency)) {
            rescheduleNext(context, reminderId, title, patientName, frequency);
        }
    }

    private void showNotification(Context context, int reminderId, String title, String patientName) {
        createChannelIfNeeded(context);

        String body = (patientName != null && !patientName.isEmpty())
                ? "Reminder for " + patientName
                : "It's time for this reminder";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title != null ? title : "SAWT Reminder")
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(reminderId, builder.build());
    }

    private void rescheduleNext(Context context, int reminderId, String title,
                                String patientName, String frequency) {
        Calendar next = Calendar.getInstance();
        if ("daily".equals(frequency)) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        } else {
            next.add(Calendar.WEEK_OF_YEAR, 1);
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return;

        Intent rescheduleIntent = ReminderScheduler.buildIntent(context, reminderId, title, patientName, frequency);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, reminderId, rescheduleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pendingIntent);

        ReminderDatabaseHelper dbHelper = new ReminderDatabaseHelper(context);
        dbHelper.updateTriggerAt(reminderId, next.getTimeInMillis());
        dbHelper.close();
    }

    private void createChannelIfNeeded(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Scheduled patient-care reminders");
            manager.createNotificationChannel(channel);
        }
    }
}