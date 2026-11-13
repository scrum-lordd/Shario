package com.localshare.persistence;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DbHelper extends SQLiteOpenHelper {
    public static final String DB = "localshare.db";
    public static final int VER = 4;

    public DbHelper(Context ctx){ super(ctx, DB, null, VER); }

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE users (id TEXT PRIMARY KEY, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, plain_password TEXT, created_at INTEGER NOT NULL, is_approved INTEGER NOT NULL DEFAULT 0, blocked_until INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, created_at INTEGER, expires_at INTEGER, last_seen INTEGER, ip TEXT, user_agent TEXT, FOREIGN KEY(user_id) REFERENCES users(id))");
        db.execSQL("CREATE TABLE access_requests (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, username TEXT, status TEXT NOT NULL, created_at INTEGER)");
        db.execSQL("CREATE TABLE shared_resources (opaque_id TEXT PRIMARY KEY, display_name TEXT NOT NULL, saf_uri TEXT NOT NULL, is_folder INTEGER NOT NULL, parent_opaque_id TEXT, added_at INTEGER)");
        db.execSQL("CREATE TABLE permissions (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, resource_opaque_id TEXT NOT NULL, grants TEXT NOT NULL, created_at INTEGER, UNIQUE(user_id, resource_opaque_id))");
        db.execSQL("CREATE TABLE transfer_logs (id TEXT PRIMARY KEY, user_id TEXT, resource_opaque_id TEXT, type TEXT, bytes INTEGER, started_at INTEGER, ended_at INTEGER, status TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS excluded_resources (saf_uri TEXT PRIMARY KEY, opaque_id TEXT, excluded_at INTEGER)");
        db.execSQL("CREATE INDEX idx_sessions_user ON sessions(user_id)");
        db.execSQL("CREATE INDEX idx_permissions_user ON permissions(user_id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV){
        if(oldV<3){ db.execSQL("DROP TABLE IF EXISTS transfer_logs"); db.execSQL("CREATE TABLE transfer_logs (id TEXT PRIMARY KEY, user_id TEXT, resource_opaque_id TEXT, type TEXT, bytes INTEGER, started_at INTEGER, ended_at INTEGER, status TEXT)"); }
        if(oldV<4){ db.execSQL("CREATE TABLE IF NOT EXISTS excluded_resources (saf_uri TEXT PRIMARY KEY, opaque_id TEXT, excluded_at INTEGER)"); }
    }
    @Override public void onOpen(SQLiteDatabase db){
        super.onOpen(db);
        db.execSQL("CREATE TABLE IF NOT EXISTS excluded_resources (saf_uri TEXT PRIMARY KEY, opaque_id TEXT, excluded_at INTEGER)");
        try { db.execSQL("ALTER TABLE users ADD COLUMN plain_password TEXT"); } catch (Exception ignore) {}
        try { db.execSQL("ALTER TABLE users ADD COLUMN blocked_until INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignore) {}
        try { db.execSQL("ALTER TABLE sessions ADD COLUMN user_agent TEXT"); } catch (Exception ignore) {}
    }
}
