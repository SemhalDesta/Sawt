package sosina.terefe.adu.ac.ae.sawt;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

public class SawtMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "sawt_sos_channel";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        saveTokenForCurrentUser(token);
    }

    /**
     * Called once per process start with the current token, and again whenever
     * FCM rotates it. Also called manually right after login/registration
     * (see LoginFragment) so the very first token after sign-in is captured too.
     */
    public static void saveTokenForCurrentUser(String token) {
        if (token == null) return;
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return; // not logged in yet, nothing to attach it to

        String uid = auth.getCurrentUser().getUid();
        Map<String, Object> update = new HashMap<>();
        update.put("fcmToken", token);

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update(update)
                .addOnFailureListener(e ->
                        android.util.Log.e("SAWT", "Failed to save fcmToken: " + e.getMessage()));
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "SAWT";
        String body = "You have a new alert";

        // Works whether the Cloud Function sends a "notification" payload or a plain "data" payload
        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        } else if (!remoteMessage.getData().isEmpty()) {
            if (remoteMessage.getData().containsKey("title")) {
                title = remoteMessage.getData().get("title");
            }
            if (remoteMessage.getData().containsKey("message")) {
                body = remoteMessage.getData().get("message");
            }
        }

        showNotification(title, body);
    }

    private void showNotification(String title, String body) {
        createChannelIfNeeded();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(this).notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SOS Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Emergency alerts from patients");
            manager.createNotificationChannel(channel);
        }
    }
}