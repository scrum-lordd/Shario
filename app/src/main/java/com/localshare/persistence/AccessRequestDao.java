package com.localshare.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.localshare.domain.model.AccessRequest;
import com.localshare.security.SecureTokenGenerator;

import java.util.*;

public class AccessRequestDao {
    private final DbHelper h;
    public AccessRequestDao(DbHelper h){ this.h = h; }

    public void insert(AccessRequest ar){
        ContentValues cv = new ContentValues();
        cv.put("id", ar.id); cv.put("user_id", ar.userId); cv.put("username", ar.username);
        cv.put("status", ar.status); cv.put("created_at", ar.createdAt);
        h.getWritableDatabase().insertOrThrow("access_requests", null, cv);
    }

    public List<AccessRequest> listPending(){
        List<AccessRequest> list = new ArrayList<>();
        SQLiteDatabase db = h.getReadableDatabase();
        try(Cursor c = db.query("access_requests", null, "status=?", new String[]{"PENDING"}, null,null,"created_at DESC")){
            while(c.moveToNext()){
                AccessRequest ar = new AccessRequest();
                ar.id = c.getString(c.getColumnIndexOrThrow("id"));
                ar.userId = c.getString(c.getColumnIndexOrThrow("user_id"));
                ar.username = c.getString(c.getColumnIndexOrThrow("username"));
                ar.status = c.getString(c.getColumnIndexOrThrow("status"));
                ar.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                list.add(ar);
            }
        }
        return list;
    }

    public AccessRequest findByUserId(String userId){
        SQLiteDatabase db = h.getReadableDatabase();
        try(Cursor c = db.query("access_requests", null, "user_id=?", new String[]{userId}, null,null,"created_at DESC", "1")){
            if(!c.moveToFirst()) return null;
            AccessRequest ar = new AccessRequest();
            ar.id = c.getString(c.getColumnIndexOrThrow("id"));
            ar.userId = c.getString(c.getColumnIndexOrThrow("user_id"));
            ar.username = c.getString(c.getColumnIndexOrThrow("username"));
            ar.status = c.getString(c.getColumnIndexOrThrow("status"));
            ar.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
            return ar;
        }
    }

    public void upsertPending(String userId, String username){
        AccessRequest existing = findByUserId(userId);
        long now = System.currentTimeMillis();
        SQLiteDatabase db = h.getWritableDatabase();
        if(existing != null){
            ContentValues cv = new ContentValues();
            cv.put("status", "PENDING");
            if(username != null) cv.put("username", username);
            cv.put("created_at", now);
            db.update("access_requests", cv, "id=?", new String[]{existing.id});
        }else{
            ContentValues cv = new ContentValues();
            cv.put("id", SecureTokenGenerator.generateOpaqueId());
            cv.put("user_id", userId);
            cv.put("username", username);
            cv.put("status", "PENDING");
            cv.put("created_at", now);
            db.insertOrThrow("access_requests", null, cv);
        }
    }

    public void updateStatus(String id, String status){
        ContentValues cv = new ContentValues(); cv.put("status", status);
        h.getWritableDatabase().update("access_requests", cv, "id=?", new String[]{id});
    }
}


