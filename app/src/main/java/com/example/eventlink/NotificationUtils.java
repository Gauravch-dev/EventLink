package com.example.eventlink;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationUtils {
    private static final String CHANNEL_ID = "eventlink_alerts";

    /**
     * Create or update the notification channel (Android 8+)
     */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "EventLink Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders and AI alerts for upcoming events");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public static void notify(Context context, int id, String title, String message) {
        createChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(id, builder.build());
    }
    /**
     * Send a notification safely (with permission check)
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public static void showNotification(Context context, String title, String message) {
        createChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify((int) System.currentTimeMillis(), builder.build());
    }

    /**
     * Shortcut for event reminders
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public static void showEventReminder(Context context, String eventName, String eventDate) {
        showNotification(context, "⏰ Event Reminder", eventName + " is happening on " + eventDate);
    }
}
