package com.localshare.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.core.app.NotificationCompat;
import com.localshare.domain.model.AccessRequest;
import com.localshare.security.SecureTokenGenerator;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes3.dex */
public class AccessRequestDao {
    private final DbHelper h;

    public AccessRequestDao(DbHelper h) {
        this.h = h;
    }

    public void insert(AccessRequest ar) {
        ContentValues cv = new ContentValues();
        cv.put("id", ar.id);
        cv.put("user_id", ar.userId);
        cv.put("username", ar.username);
        cv.put(NotificationCompat.CATEGORY_STATUS, ar.status);
        cv.put("created_at", Long.valueOf(ar.createdAt));
        this.h.getWritableDatabase().insertOrThrow("access_requests", null, cv);
    }

    public List<AccessRequest> listPending() {
        List<AccessRequest> list = new ArrayList<>();
        SQLiteDatabase db = this.h.getReadableDatabase();
        Cursor c = db.query("access_requests", null, "status=?", new String[]{"PENDING"}, null, null, "created_at DESC");
        while (c.moveToNext()) {
            try {
                AccessRequest ar = new AccessRequest();
                ar.id = c.getString(c.getColumnIndexOrThrow("id"));
                ar.userId = c.getString(c.getColumnIndexOrThrow("user_id"));
                ar.username = c.getString(c.getColumnIndexOrThrow("username"));
                ar.status = c.getString(c.getColumnIndexOrThrow(NotificationCompat.CATEGORY_STATUS));
                ar.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                list.add(ar);
            } catch (Throwable th) {
                if (c != null) {
                    try {
                        c.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        if (c != null) {
            c.close();
        }
        return list;
    }

    public AccessRequest findByUserId(String userId) {
        SQLiteDatabase db = this.h.getReadableDatabase();
        Cursor c = db.query("access_requests", null, "user_id=?", new String[]{userId}, null, null, "created_at DESC", "1");
        try {
            if (c.moveToFirst()) {
                AccessRequest ar = new AccessRequest();
                ar.id = c.getString(c.getColumnIndexOrThrow("id"));
                ar.userId = c.getString(c.getColumnIndexOrThrow("user_id"));
                ar.username = c.getString(c.getColumnIndexOrThrow("username"));
                ar.status = c.getString(c.getColumnIndexOrThrow(NotificationCompat.CATEGORY_STATUS));
                ar.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                if (c != null) {
                    c.close();
                }
                return ar;
            }
            if (c != null) {
                c.close();
                return null;
            }
            return null;
        } catch (Throwable th) {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    public void upsertPending(String userId, String username) {
        AccessRequest existing = findByUserId(userId);
        long now = System.currentTimeMillis();
        SQLiteDatabase db = this.h.getWritableDatabase();
        if (existing != null) {
            ContentValues cv = new ContentValues();
            cv.put(NotificationCompat.CATEGORY_STATUS, "PENDING");
            if (username != null) {
                cv.put("username", username);
            }
            cv.put("created_at", Long.valueOf(now));
            db.update("access_requests", cv, "id=?", new String[]{existing.id});
            return;
        }
        ContentValues cv2 = new ContentValues();
        cv2.put("id", SecureTokenGenerator.generateOpaqueId());
        cv2.put("user_id", userId);
        cv2.put("username", username);
        cv2.put(NotificationCompat.CATEGORY_STATUS, "PENDING");
        cv2.put("created_at", Long.valueOf(now));
        db.insertOrThrow("access_requests", null, cv2);
    }

    public void updateStatus(String id, String status) {
        ContentValues cv = new ContentValues();
        cv.put(NotificationCompat.CATEGORY_STATUS, status);
        this.h.getWritableDatabase().update("access_requests", cv, "id=?", new String[]{id});
    }
}
