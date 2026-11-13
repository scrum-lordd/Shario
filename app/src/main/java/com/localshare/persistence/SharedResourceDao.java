package com.localshare.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.localshare.domain.model.SharedResource;
import com.localshare.security.SecureTokenGenerator;

import java.util.*;

public class SharedResourceDao {
    private final DbHelper h;
    public SharedResourceDao(DbHelper h){ this.h = h; }

    public String insert(String displayName, String safUri, boolean isFolder, String parentId){
        String opaque = SecureTokenGenerator.generateOpaqueId();
        ContentValues cv = new ContentValues();
        cv.put("opaque_id", opaque); 
        cv.put("display_name", displayName);
        cv.put("saf_uri", safUri); 
        cv.put("is_folder", isFolder ? 1 : 0);
        cv.put("parent_opaque_id", parentId); 
        cv.put("added_at", System.currentTimeMillis());
        h.getWritableDatabase().insertOrThrow("shared_resources", null, cv);
        return opaque;
    }

    private SharedResource fromCursor(Cursor c){
        SharedResource r = new SharedResource();
        r.opaqueId = c.getString(c.getColumnIndexOrThrow("opaque_id"));
        r.displayName = c.getString(c.getColumnIndexOrThrow("display_name"));
        r.safUri = c.getString(c.getColumnIndexOrThrow("saf_uri"));
        r.isFolder = c.getInt(c.getColumnIndexOrThrow("is_folder")) == 1;
        r.parentOpaqueId = c.getString(c.getColumnIndexOrThrow("parent_opaque_id"));
        r.addedAt = c.getLong(c.getColumnIndexOrThrow("added_at"));
        return r;
    }

    /**
     * Lists only root shared resources (top-level folders and files added by the user)
     */
    public List<SharedResource> listRoots(){
        List<SharedResource> list = new ArrayList<>();
        try(Cursor c = h.getReadableDatabase().query("shared_resources", null, 
                "(parent_opaque_id IS NULL OR parent_opaque_id='')", null, null, null, "added_at DESC")){
            while(c.moveToNext()){
                list.add(fromCursor(c));
            }
        }
        return list;
    }

    /**
     * Compatibility: returns root items so callers don't accidentally fetch nested children
     */
    public List<SharedResource> listAll(){
        return listRoots();
    }

    public List<SharedResource> listByParent(String parentOpaqueId){
        List<SharedResource> list = new ArrayList<>();
        try(Cursor c = h.getReadableDatabase().query("shared_resources", null, 
                "parent_opaque_id=?", new String[]{parentOpaqueId}, null, null, "is_folder DESC, display_name ASC")){
            while(c.moveToNext()){
                list.add(fromCursor(c));
            }
        }
        return list;
    }

    public SharedResource get(String opaqueId){
        try(Cursor c = h.getReadableDatabase().query("shared_resources", null, 
                "opaque_id=?", new String[]{opaqueId}, null, null, null)){
            if(c.moveToNext()){
                return fromCursor(c);
            }
        }
        return null;
    }

    public void delete(String opaqueId){
        SQLiteDatabase db = h.getWritableDatabase();
        db.delete("shared_resources", "opaque_id=? OR parent_opaque_id=?", new String[]{opaqueId, opaqueId});
        db.delete("permissions", "resource_opaque_id=?", new String[]{opaqueId});
    }

    public void excludeResource(String opaqueId, String safUri){
        SQLiteDatabase db = h.getWritableDatabase();
        if(safUri != null && !safUri.isEmpty()){
            ContentValues cv = new ContentValues();
            cv.put("saf_uri", safUri);
            cv.put("opaque_id", opaqueId);
            cv.put("excluded_at", System.currentTimeMillis());
            db.insertWithOnConflict("excluded_resources", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }
        if(opaqueId != null && !opaqueId.isEmpty()){
            db.delete("shared_resources", "opaque_id=? OR parent_opaque_id=?", new String[]{opaqueId, opaqueId});
            db.delete("permissions", "resource_opaque_id=?", new String[]{opaqueId});
        }
    }

    public boolean isExcluded(String safUri){
        if(safUri == null || safUri.isEmpty()) return false;
        try(Cursor c = h.getReadableDatabase().query("excluded_resources", new String[]{"saf_uri"}, 
                "saf_uri=?", new String[]{safUri}, null, null, null)){
            return c.moveToFirst();
        }catch(Exception e){
            return false;
        }
    }
}
