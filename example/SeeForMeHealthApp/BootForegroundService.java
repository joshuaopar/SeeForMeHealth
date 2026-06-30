
package com.example.SeeForMeHealthApp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

public class BootForegroundService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Create notification channel
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "boot_channel",
                    "See For Me Health",
                    NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        // Launch intent
        Intent appIntent = new Intent(this, MainActivity.class);
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build foreground notification
        Notification notification = new androidx.core.app.NotificationCompat.Builder(this, "boot_channel")
                .setSmallIcon(R.mipmap.see_for_me_health_foreground)
                .setContentTitle("See For Me Health")
                .setContentText("Starting your health assistant...")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .build();

        // Start foreground so Android allows us to show UI
        startForeground(1, notification);

        // Short delay then launch app to screen
        new Handler().postDelayed(() -> {
            startActivity(appIntent);
            stopSelf();
        }, 2000);

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}