package com.localshare.notification;

import android.content.Context;
import com.localshare.domain.model.AccessRequest;
import com.localshare.domain.model.PermissionType;
import com.localshare.domain.model.SharedResource;
import com.localshare.domain.service.AccessControlService;
import com.localshare.persistence.AccessRequestDao;
import com.localshare.persistence.DbHelper;
import com.localshare.persistence.SharedResourceDao;
import com.localshare.persistence.UserDao;
import java.util.EnumSet;
import java.util.List;

/* JADX INFO: loaded from: classes12.dex */
public class AccessRequestApprovalHelper {
    public static boolean approve(Context context, String userId, String username) {
        if (context == null || userId == null) {
            return false;
        }
        try {
            DbHelper dbHelper = new DbHelper(context);
            UserDao userDao = new UserDao(dbHelper);
            userDao.setApproved(userId, true);
            AccessControlService acs = new AccessControlService(dbHelper);
            SharedResourceDao srDao = new SharedResourceDao(dbHelper);
            List<SharedResource> resources = srDao.listAll();
            for (SharedResource sr : resources) {
                acs.grant(userId, sr.opaqueId, EnumSet.of(PermissionType.VIEW, PermissionType.DOWNLOAD));
            }
            AccessRequestDao arDao = new AccessRequestDao(dbHelper);
            AccessRequest req = arDao.findByUserId(userId);
            if (req != null) {
                arDao.updateStatus(req.id, "APPROVED");
            }
            AccessRequestNotifier.cancelNotification(context, userId);
            AccessRequestNotifier.notifyChanged(context);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean reject(Context context, String userId, String username) {
        if (context == null || userId == null) {
            return false;
        }
        try {
            DbHelper dbHelper = new DbHelper(context);
            UserDao userDao = new UserDao(dbHelper);
            userDao.setApproved(userId, false);
            AccessRequestDao arDao = new AccessRequestDao(dbHelper);
            AccessRequest req = arDao.findByUserId(userId);
            if (req != null) {
                arDao.updateStatus(req.id, "DENIED");
            }
            AccessRequestNotifier.cancelNotification(context, userId);
            AccessRequestNotifier.notifyChanged(context);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
