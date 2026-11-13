package com.localshare.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.localshare.domain.model.SharedResource;
import com.localshare.security.SecureTokenGenerator;

import java.util.ArrayList;
import java.util.List;

public class SharedResourceDao {
    private final DbHelper h;

    public SharedResourceDao(DbHelper h) {
        this.h = h;
    }

    public String insert(String displayName, String safUri, boolean isFolder, String parentOpaqueId) {
        String opaqueId = SecureTokenGenerator.generateOpaqueId();
        ContentValues cv = new ContentValues();
        cv.put("opaque_id", opaqueId);
        cv.put("display_name", displayName);
        cv.put("saf_uri", safUri);
        cv.put("is_folder", isFolder ? 1 : 0);
        cv.put("parent_opaque_id", parentOpaqueId);
        cv.put("added_at", System.currentTimeMillis());
        this.h.getWritableDatabase().insertOrThrow("shared_resources", null, cv);
        return opaqueId;
    }

    public List<SharedResource> listAll() {
        List<SharedResource> list = new ArrayList<>();
        SQLiteDatabase db = this.h.getReadableDatabase();
        try (Cursor c = db.query("shared_resources", null, null, null, null, null, "added_at DESC")) {
            while (c.moveToNext()) {
                SharedResource r = new SharedResource();
                r.opaqueId = c.getString(c.getColumnIndexOrThrow("opaque_id"));
                r.displayName = c.getString(c.getColumnIndexOrThrow("display_name"));
                r.safUri = c.getString(c.getColumnIndexOrThrow("saf_uri"));
                r.isFolder = c.getInt(c.getColumnIndexOrThrow("is_folder")) == 1;
                r.parentOpaqueId = c.getString(c.getColumnIndexOrThrow("parent_opaque_id"));
                r.addedAt = c.getLong(c.getColumnIndexOrThrow("added_at"));
                list.add(r);
            }
        }
        return list;
    }

    public List<SharedResource> listRoots() {
        List<SharedResource> list = new ArrayList<>();
        SQLiteDatabase db = this.h.getReadableDatabase();
        try (Cursor c = db.query("shared_resources", null, "parent_opaque_id IS NULL OR parent_opaque_id=''", null, null, null, "added_at DESC")) {
            while (c.moveToNext()) {
                SharedResource r = new SharedResource();
                r.opaqueId = c.getString(c.getColumnIndexOrThrow("opaque_id"));
                r.displayName = c.getString(c.getColumnIndexOrThrow("display_name"));
                r.safUri = c.getString(c.getColumnIndexOrThrow("saf_uri"));
                r.isFolder = c.getInt(c.getColumnIndexOrThrow("is_folder")) == 1;
                r.parentOpaqueId = c.getString(c.getColumnIndexOrThrow("parent_opaque_id"));
                r.addedAt = c.getLong(c.getColumnIndexOrThrow("added_at"));
                list.add(r);
            }
        }
        return list;
    }

    public void delete(String opaqueId) {
        SQLiteDatabase db = this.h.getWritableDatabase();
        db.delete("shared_resources", "opaque_id=?", new String[]{opaqueId});
        db.delete("permissions", "resource_opaque_id=?", new String[]{opaqueId});
    }

    public void excludeResource(String opaqueId, String safUri) {
        SQLiteDatabase db = this.h.getWritableDatabase();
        if (safUri != null && !safUri.isEmpty()) {
            ContentValues cv = new ContentValues();
            cv.put("saf_uri", safUri);
            cv.put("opaque_id", opaqueId);
            cv.put("excluded_at", System.currentTimeMillis());
            db.insertWithOnConflict("excluded_resources", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }
        if (opaqueId != null && !opaqueId.isEmpty()) {
            db.delete("shared_resources", "opaque_id=?", new String[]{opaqueId});
            db.delete("permissions", "resource_opaque_id=?", new String[]{opaqueId});
        }
    }
}
