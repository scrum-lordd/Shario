package com.localshare.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Handles clicks on the "Approve" and "Decline" buttons in the Android status bar notification.
 */
public class AccessRequestActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String userId = intent.getStringExtra(AccessRequestNotifier.EXTRA_USER_ID);
        String username = intent.getStringExtra(AccessRequestNotifier.EXTRA_USERNAME);
        if (userId == null) return;

        if (AccessRequestNotifier.ACTION_APPROVE.equals(action)) {
            boolean ok = AccessRequestApprovalHelper.approve(context, userId, username);
            if (ok) {
                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "Approved access for " + (username != null ? username : "user"), Toast.LENGTH_SHORT).show()
                );
            }
        } else if (AccessRequestNotifier.ACTION_DENY.equals(action)) {
            boolean ok = AccessRequestApprovalHelper.reject(context, userId, username);
            if (ok) {
                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "Declined access for " + (username != null ? username : "user"), Toast.LENGTH_SHORT).show()
                );
            }
        }
    }
}
