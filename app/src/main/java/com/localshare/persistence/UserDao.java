package com.localshare.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.localshare.domain.model.User;

/* JADX INFO: loaded from: classes3.dex */
public class UserDao {
    private final DbHelper helper;

    public UserDao(DbHelper h) {
        this.helper = h;
    }

    public void insert(User user) {
        SQLiteDatabase writableDatabase = this.helper.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("id", user.id);
        contentValues.put("username", user.username);
        contentValues.put("password_hash", user.passwordHash);
        contentValues.put("created_at", Long.valueOf(user.createdAt));
        contentValues.put("is_approved", Integer.valueOf(user.isApproved ? 1 : 0));
        writableDatabase.insertOrThrow("users", null, contentValues);
    }

    public User findByUsername(String username) {
        SQLiteDatabase db = this.helper.getReadableDatabase();
        Cursor c = db.query("users", null, "username=?", new String[]{username}, null, null, null);
        try {
            if (c.moveToFirst()) {
                User userFromCursor = fromCursor(c);
                if (c != null) {
                    c.close();
                }
                return userFromCursor;
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

    public User findById(String id) {
        SQLiteDatabase db = this.helper.getReadableDatabase();
        Cursor c = db.query("users", null, "id=?", new String[]{id}, null, null, null);
        try {
            if (c.moveToFirst()) {
                User userFromCursor = fromCursor(c);
                if (c != null) {
                    c.close();
                }
                return userFromCursor;
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

    private User fromCursor(Cursor c) {
        User u = new User();
        u.id = c.getString(c.getColumnIndexOrThrow("id"));
        u.username = c.getString(c.getColumnIndexOrThrow("username"));
        u.passwordHash = c.getString(c.getColumnIndexOrThrow("password_hash"));
        u.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        u.isApproved = c.getInt(c.getColumnIndexOrThrow("is_approved")) == 1;
        return u;
    }

    public void setApproved(String str, boolean z) {
        SQLiteDatabase writableDatabase = this.helper.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("is_approved", Integer.valueOf(z ? 1 : 0));
        writableDatabase.update("users", contentValues, "id=?", new String[]{str});
    }
}
