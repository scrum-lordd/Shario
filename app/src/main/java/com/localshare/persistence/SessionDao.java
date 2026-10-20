package com.localshare.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.localshare.domain.model.Session;

/* JADX INFO: loaded from: classes3.dex */
public class SessionDao {
    private final DbHelper helper;

    public SessionDao(DbHelper h) {
        this.helper = h;
    }

    public void insert(Session s) {
        SQLiteDatabase db = this.helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", s.id);
        cv.put("user_id", s.userId);
        cv.put("created_at", Long.valueOf(s.createdAt));
        cv.put("expires_at", Long.valueOf(s.expiresAt));
        cv.put("last_seen", Long.valueOf(s.lastSeen));
        cv.put("ip", s.ip);
        db.insertOrThrow("sessions", null, cv);
    }

    public Session findById(String idHash) {
        SQLiteDatabase db = this.helper.getReadableDatabase();
        Cursor c = db.query("sessions", null, "id=?", new String[]{idHash}, null, null, null);
        try {
            if (c.moveToFirst()) {
                Session s = new Session();
                s.id = c.getString(c.getColumnIndexOrThrow("id"));
                s.userId = c.getString(c.getColumnIndexOrThrow("user_id"));
                s.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                s.expiresAt = c.getLong(c.getColumnIndexOrThrow("expires_at"));
                s.lastSeen = c.getLong(c.getColumnIndexOrThrow("last_seen"));
                s.ip = c.getString(c.getColumnIndexOrThrow("ip"));
                if (c != null) {
                    c.close();
                }
                return s;
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

    public void delete(String idHash) {
        this.helper.getWritableDatabase().delete("sessions", "id=?", new String[]{idHash});
    }

    public void updateLastSeen(String idHash, long now) {
        ContentValues cv = new ContentValues();
        cv.put("last_seen", Long.valueOf(now));
        this.helper.getWritableDatabase().update("sessions", cv, "id=?", new String[]{idHash});
    }

    public void deleteExpired(long now) {
        this.helper.getWritableDatabase().delete("sessions", "expires_at<?", new String[]{String.valueOf(now)});
    }
}
