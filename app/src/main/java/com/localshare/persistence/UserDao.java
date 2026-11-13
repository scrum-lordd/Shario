package com.localshare.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.localshare.domain.model.User;

import java.util.ArrayList;
import java.util.List;

public class UserDao {
    private final DbHelper helper;
    public UserDao(DbHelper h){ helper = h; }

    public void insert(User u){
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", u.id); 
        cv.put("username", u.username); 
        cv.put("password_hash", u.passwordHash);
        cv.put("plain_password", u.plainPassword);
        cv.put("created_at", u.createdAt); 
        cv.put("is_approved", u.isApproved ? 1 : 0);
        cv.put("blocked_until", u.blockedUntil);
        db.insertOrThrow("users", null, cv);
    }

    public List<User> listAll(){
        List<User> list = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT u.*, MAX(s.created_at) AS last_login FROM users u LEFT JOIN sessions s ON u.id = s.user_id GROUP BY u.id ORDER BY u.created_at DESC";
        try(Cursor c = db.rawQuery(sql, null)){
            while(c.moveToNext()){
                User u = fromCursor(c);
                int lastLoginIdx = c.getColumnIndex("last_login");
                if(lastLoginIdx >= 0 && !c.isNull(lastLoginIdx)){
                    u.lastLogin = c.getLong(lastLoginIdx);
                }
                list.add(u);
            }
        }
        return list;
    }

    public User findByUsername(String username){
        SQLiteDatabase db = helper.getReadableDatabase();
        try(Cursor c = db.query("users", null, "username=?", new String[]{username}, null, null, null)){
            if(!c.moveToFirst()) return null;
            return fromCursor(c);
        }
    }

    public User findById(String id){
        SQLiteDatabase db = helper.getReadableDatabase();
        try(Cursor c = db.query("users", null, "id=?", new String[]{id}, null, null, null)){
            if(!c.moveToFirst()) return null;
            return fromCursor(c);
        }
    }

    private User fromCursor(Cursor c){
        User u = new User();
        u.id = c.getString(c.getColumnIndexOrThrow("id"));
        u.username = c.getString(c.getColumnIndexOrThrow("username"));
        u.passwordHash = c.getString(c.getColumnIndexOrThrow("password_hash"));
        
        int plainIdx = c.getColumnIndex("plain_password");
        if(plainIdx >= 0){
            u.plainPassword = c.getString(plainIdx);
        }

        u.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        u.isApproved = c.getInt(c.getColumnIndexOrThrow("is_approved")) == 1;

        int blockedIdx = c.getColumnIndex("blocked_until");
        if(blockedIdx >= 0){
            u.blockedUntil = c.getLong(blockedIdx);
        }
        return u;
    }

    public void setApproved(String id, boolean approved){
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues(); 
        cv.put("is_approved", approved ? 1 : 0);
        db.update("users", cv, "id=?", new String[]{id});
    }

    public void setBlockedUntil(String id, long blockedUntil){
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues(); 
        cv.put("blocked_until", blockedUntil);
        db.update("users", cv, "id=?", new String[]{id});
        if(blockedUntil != 0){
            db.delete("sessions", "user_id=?", new String[]{id});
        }
    }

    public void delete(String id){
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete("users", "id=?", new String[]{id});
        db.delete("sessions", "user_id=?", new String[]{id});
        db.delete("access_requests", "user_id=?", new String[]{id});
        db.delete("permissions", "user_id=?", new String[]{id});
    }
}
