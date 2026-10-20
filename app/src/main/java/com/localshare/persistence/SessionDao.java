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

   
}
