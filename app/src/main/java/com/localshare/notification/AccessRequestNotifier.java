package com.localshare.notification;

import android.R;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.localshare.MainActivity;
import com.localshare.service.FileShareService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/* JADX INFO: loaded from: classes12.dex */
public class AccessRequestNotifier {
    public static final String ACTION_APPROVE = "com.localshare.ACTION_APPROVE_REQUEST";
    public static final String ACTION_DENY = "com.localshare.ACTION_DENY_REQUEST";
    public static final String CHANNEL_REQUESTS = "access_requests_channel";
    public static final String EXTRA_USERNAME = "extra_username";
    public static final String EXTRA_USER_ID = "extra_user_id";
    private static final List<OnAccessRequestsChangeListener> listeners = new CopyOnWriteArrayList();

    public interface OnAccessRequestsChangeListener {
        void onRequestsChanged();
    }

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
        NotificationManager nm = (NotificationManager) context.getSystemService(NotificationManager.class);
        if (nm != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_REQUESTS, "Access Requests", 4);
            channel.setDescription("Alerts when new users request access to shared files");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            nm.createNotificationChannel(channel);
        }
    }

    public static void notifyNewRequest(Context context, String userId, String username) {
        notifyChanged(context);
        if (context != null && userId != null) {
            showStatusBarNotification(context, userId, username != null ? username : "New User");
        }
    }

    public static void notifyChanged(Context context) {
        new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: com.localshare.notification.AccessRequestNotifier$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                AccessRequestNotifier.lambda$notifyChanged$0();
            }
        });
        if (context != null) {
            try {
                Intent broadcast = new Intent(FileShareService.BROADCAST_REQUESTS_UPDATED);
                broadcast.setPackage(context.getPackageName());
                context.sendBroadcast(broadcast);
            } catch (Exception e) {
            }
        }
    }

    static /* synthetic */ void lambda$notifyChanged$0() {
        for (OnAccessRequestsChangeListener l : listeners) {
            try {
                l.onRequestsChanged();
            } catch (Exception e) {
            }
        }
    }

    public static void showStatusBarNotification(Context context, String userId, String username) {
        try {
            createNotificationChannel(context);
            int notifId = Math.abs(userId.hashCode());
            Intent openIntent = new Intent(context, (Class<?>) MainActivity.class);
            openIntent.setFlags(335544320);
            openIntent.putExtra("highlight_requests", true);
            PendingIntent openPending = PendingIntent.getActivity(context, notifId, openIntent, 201326592);
            Intent approveIntent = new Intent(context, (Class<?>) AccessRequestActionReceiver.class);
            approveIntent.setAction(ACTION_APPROVE);
            approveIntent.putExtra(EXTRA_USER_ID, userId);
            approveIntent.putExtra(EXTRA_USERNAME, username);
            PendingIntent approvePending = PendingIntent.getBroadcast(context, (notifId * 3) + 1, approveIntent, 201326592);
            Intent denyIntent = new Intent(context, (Class<?>) AccessRequestActionReceiver.class);
            denyIntent.setAction(ACTION_DENY);
            denyIntent.putExtra(EXTRA_USER_ID, userId);
            denyIntent.putExtra(EXTRA_USERNAME, username);
            PendingIntent denyPending = PendingIntent.getBroadcast(context, (notifId * 3) + 2, denyIntent, 201326592);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REQUESTS).setSmallIcon(R.drawable.ic_dialog_info).setContentTitle("New Access Request").setContentText(username + " is requesting access to shared files").setStyle(new NotificationCompat.BigTextStyle().bigText("User '" + username + "' wants access to your shared files. You can approve or decline right here.")).setPriority(1).setCategory(NotificationCompat.CATEGORY_STATUS).setAutoCancel(true).setContentIntent(openPending).addAction(R.drawable.ic_menu_add, "Approve", approvePending).addAction(R.drawable.ic_menu_close_clear_cancel, "Decline", denyPending);
            NotificationManagerCompat.from(context).notify(notifId, builder.build());
        } catch (SecurityException se) {
            se.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void cancelNotification(Context context, String userId) {
        if (context == null || userId == null) {
            return;
        }
        try {
            int notifId = Math.abs(userId.hashCode());
            NotificationManagerCompat.from(context).cancel(notifId);
        } catch (Exception e) {
        }
    }
}
