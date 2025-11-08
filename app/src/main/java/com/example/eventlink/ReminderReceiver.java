package com.example.eventlink;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.RequiresPermission;

/**
 * BroadcastReceiver triggered by AlarmManager to show event reminders.
 */
public class ReminderReceiver extends BroadcastReceiver {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @Override
    public void onReceive(Context context, Intent intent) {
        String eventName = intent.getStringExtra("eventName");
        String eventDate = intent.getStringExtra("eventDate");

        if (eventName == null) eventName = "Upcoming Event";
        if (eventDate == null) eventDate = "soon";

        // ✅ Use unified notification utility
        NotificationUtils.showEventReminder(context, eventName, eventDate);
    }
}
