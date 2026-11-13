package com.localshare.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.localshare.MainActivity;
import com.localshare.service.FileShareService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handles status bar notifications for access requests and provides
 * instant in-memory reactive updates to the UI without requiring app restarts.
 */
public class AccessRequestNotifier {
    public static final String CHANNEL_REQUESTS = "access_requests_channel";
    public static final String ACTION_APPROVE = "com.localshare.ACTION_APPROVE_REQUEST";
    public static final String ACTION_DENY = "com.localshare.ACTION_DENY_REQUEST";
    public static final String EXTRA_USER_ID = "extra_user_id";
    public static final String EXTRA_USERNAME = "extra_username";

    public interface OnAccessRequestsChangeListener {
        void onRequestsChanged();
    }

    private static final List<OnAccessRequestsChangeListener> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(OnAccessRequestsChangeListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public static void removeListener(OnAccessRequestsChangeListener l) {
        if (l != null) {
            listeners.remove(l);
        }
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_REQUESTS,
                    "Access Requests",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Alerts when new users request access to shared files");
                channel.enableVibration(true);
                channel.setShowBadge(true);
                nm.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Called whenever a user registers, logs in without approval, or requests access.
     */
    public static void notifyNewRequest(Context context, String userId, String username) {
        // 1. Instant in-memory dispatch to UI
        notifyChanged(context);

        // 2. Post high-priority notification to the system status bar
        if (context != null && userId != null) {
            showStatusBarNotification(context, userId, username != null ? username : "New User");
        }
    }

    /**
     * Notifies all active UI components and sends package-scoped broadcast.
     */
    public static void notifyChanged(Context context) {
        new Handler(Looper.getMainLooper()).post(() -> {
            for (OnAccessRequestsChangeListener l : listeners) {
                try {
                    l.onRequestsChanged();
                } catch (Exception ignore) {}
            }
        });

        if (context != null) {
            try {
                Intent broadcast = new Intent(FileShareService.BROADCAST_REQUESTS_UPDATED);
                broadcast.setPackage(context.getPackageName());
                context.sendBroadcast(broadcast);
            } catch (Exception ignore) {}
        }
    }

    public static void showStatusBarNotification(Context context, String userId, String username) {
        try {
            createNotificationChannel(context);

            int notifId = Math.abs(userId.hashCode());

            // Tap notification to open MainActivity
            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            openIntent.putExtra("highlight_requests", true);
            PendingIntent openPending = PendingIntent.getActivity(
                context,
                notifId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Notification Action: Approve
            Intent approveIntent = new Intent(context, AccessRequestActionReceiver.class);
            approveIntent.setAction(ACTION_APPROVE);
            approveIntent.putExtra(EXTRA_USER_ID, userId);
            approveIntent.putExtra(EXTRA_USERNAME, username);
            PendingIntent approvePending = PendingIntent.getBroadcast(
                context,
                notifId * 3 + 1,
                approveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Notification Action: Deny
            Intent denyIntent = new Intent(context, AccessRequestActionReceiver.class);
            denyIntent.setAction(ACTION_DENY);
            denyIntent.putExtra(EXTRA_USER_ID, userId);
            denyIntent.putExtra(EXTRA_USERNAME, username);
            PendingIntent denyPending = PendingIntent.getBroadcast(
                context,
                notifId * 3 + 2,
                denyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REQUESTS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("New Access Request")
                .setContentText(username + " is requesting access to shared files")
                .setStyle(new NotificationCompat.BigTextStyle()
                    .bigText("User '" + username + "' wants access to your shared files. You can approve or decline right here."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(openPending)
                .addAction(android.R.drawable.ic_menu_add, "Approve", approvePending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", denyPending);

            NotificationManagerCompat.from(context).notify(notifId, builder.build());
        } catch (SecurityException se) {
            // Permission POST_NOTIFICATIONS not yet granted on Android 13+
            se.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void cancelNotification(Context context, String userId) {
        if (context == null || userId == null) return;
        try {
            int notifId = Math.abs(userId.hashCode());
            NotificationManagerCompat.from(context).cancel(notifId);
        } catch (Exception ignore) {}
    }
}
