package com.localshare.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.localshare.domain.model.Session;

public class SessionDao {
    private final DbHelper helper;
    public SessionDao(DbHelper h){ helper = h; }
    public void insert(Session s){
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", s.id); cv.put("user_id", s.userId); cv.put("created_at", s.createdAt);
        cv.put("expires_at", s.expiresAt); cv.put("last_seen", s.lastSeen); cv.put("ip", s.ip);
        db.insertOrThrow("sessions", null, cv);
    }
    public Session findById(String idHash){
        SQLiteDatabase db = helper.getReadableDatabase();
        try(Cursor c = db.query("sessions", null, "id=?", new String[]{idHash}, null,null,null)){
            if(!c.moveToFirst()) return null;
            Session s = new Session();
            s.id = c.getString(c.getColumnIndexOrThrow("id"));
            s.userId = c.getString(c.getColumnIndexOrThrow("user_id"));
            s.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
            s.expiresAt = c.getLong(c.getColumnIndexOrThrow("expires_at"));
            s.lastSeen = c.getLong(c.getColumnIndexOrThrow("last_seen"));
            s.ip = c.getString(c.getColumnIndexOrThrow("ip"));
            return s;
        }
    }
    public void delete(String idHash){ helper.getWritableDatabase().delete("sessions","id=?", new String[]{idHash}); }
    public void deleteByUserId(String userId){ helper.getWritableDatabase().delete("sessions","user_id=?", new String[]{userId}); }
    public void updateLastSeen(String idHash, long now){ ContentValues cv = new ContentValues(); cv.put("last_seen", now); helper.getWritableDatabase().update("sessions", cv, "id=?", new String[]{idHash}); }
    public void deleteExpired(long now){ helper.getWritableDatabase().delete("sessions","expires_at<?", new String[]{String.valueOf(now)}); }
}

