package com.localshare.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/* JADX INFO: loaded from: classes12.dex */
public class AccessRequestActionReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(final Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        String userId = intent.getStringExtra(AccessRequestNotifier.EXTRA_USER_ID);
        final String username = intent.getStringExtra(AccessRequestNotifier.EXTRA_USERNAME);
        if (userId == null) {
            return;
        }
        if (AccessRequestNotifier.ACTION_APPROVE.equals(action)) {
            boolean ok = AccessRequestApprovalHelper.approve(context, userId, username);
            if (ok) {
                new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: com.localshare.notification.AccessRequestActionReceiver$$ExternalSyntheticLambda0
                    @Override // java.lang.Runnable
                    public final void run() {
                        Context context2 = context;
                        String str = username;
                        Toast.makeText(context2, "Approved access for " + (str != null ? str : "user"), 0).show();
                    }
                });
                return;
            }
            return;
        }
        if (AccessRequestNotifier.ACTION_DENY.equals(action)) {
            boolean ok2 = AccessRequestApprovalHelper.reject(context, userId, username);
            if (ok2) {
                new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: com.localshare.notification.AccessRequestActionReceiver$$ExternalSyntheticLambda1
                    @Override // java.lang.Runnable
                    public final void run() {
                        Context context2 = context;
                        String str = username;
                        Toast.makeText(context2, "Declined access for " + (str != null ? str : "user"), 0).show();
                    }
                });
            }
        }
    }
}
