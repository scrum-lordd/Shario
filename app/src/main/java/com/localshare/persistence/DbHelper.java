package com.localshare.persistence;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/* JADX INFO: loaded from: classes3.dex */
public class DbHelper extends SQLiteOpenHelper {
    public static final String DB = "localshare.db";
    public static final int VER = 3;

    public DbHelper(Context ctx) {
        super(ctx, DB, (SQLiteDatabase.CursorFactory) null, 3);
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (id TEXT PRIMARY KEY, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, created_at INTEGER NOT NULL, is_approved INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, created_at INTEGER, expires_at INTEGER, last_seen INTEGER, ip TEXT, FOREIGN KEY(user_id) REFERENCES users(id))");
        db.execSQL("CREATE TABLE access_requests (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, username TEXT, status TEXT NOT NULL, created_at INTEGER)");
        db.execSQL("CREATE TABLE shared_resources (opaque_id TEXT PRIMARY KEY, display_name TEXT NOT NULL, saf_uri TEXT NOT NULL, is_folder INTEGER NOT NULL, parent_opaque_id TEXT, added_at INTEGER)");
        db.execSQL("CREATE TABLE permissions (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, resource_opaque_id TEXT NOT NULL, grants TEXT NOT NULL, created_at INTEGER, UNIQUE(user_id, resource_opaque_id))");
        db.execSQL("CREATE TABLE transfer_logs (id TEXT PRIMARY KEY, user_id TEXT, resource_opaque_id TEXT, type TEXT, bytes INTEGER, started_at INTEGER, ended_at INTEGER, status TEXT)");
        db.execSQL("CREATE INDEX idx_sessions_user ON sessions(user_id)");
        db.execSQL("CREATE INDEX idx_permissions_user ON permissions(user_id)");
    }

    
}
