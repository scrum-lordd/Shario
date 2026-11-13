package com.localshare.domain.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.localshare.domain.model.PermissionType;
import com.localshare.persistence.DbHelper;
import com.localshare.security.SecureTokenGenerator;

import java.util.EnumSet;

public class AccessControlService {
    private final DbHelper dbHelper;
    public AccessControlService(DbHelper h){ dbHelper = h; }

    public boolean hasPermission(String userId, String resourceOpaqueId, PermissionType required){
        if(userId==null || resourceOpaqueId==null) return false;
        if(!isUserApproved(userId)) return false;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // Check if resource exists in shared_resources and is not excluded
        try(Cursor c = db.query("shared_resources", new String[]{"saf_uri"}, "opaque_id=?", new String[]{resourceOpaqueId}, null, null, null)){
            if(!c.moveToFirst()) return false;
            String safUri = c.getString(0);
            try(Cursor ec = db.query("excluded_resources", new String[]{"saf_uri"}, "saf_uri=?", new String[]{safUri}, null, null, null)){
                if(ec.moveToFirst()) return false;
            }
        }

        // Direct explicit permission check if recorded in permissions table
        try(Cursor c = db.rawQuery("SELECT grants FROM permissions WHERE user_id=? AND resource_opaque_id=?", new String[]{userId, resourceOpaqueId})){
            if(c.moveToFirst()){
                String grants = c.getString(0);
                EnumSet<PermissionType> set = parse(grants);
                return set.contains(required);
            }
        }

        // Check parent inheritance if defined in permissions table
        String current = resourceOpaqueId;
        for(int depth=0; depth<20; depth++){
            try(Cursor pc = db.rawQuery("SELECT parent_opaque_id FROM shared_resources WHERE opaque_id=?", new String[]{current})){
                if(!pc.moveToFirst()) break;
                String parent = pc.getString(0);
                if(parent==null || parent.isEmpty()) break;
                try(Cursor gc = db.rawQuery("SELECT grants FROM permissions WHERE user_id=? AND resource_opaque_id=?", new String[]{userId, parent})){
                    if(gc.moveToFirst()){
                        EnumSet<PermissionType> pset = parse(gc.getString(0));
                        if(pset.contains(required) || (required==PermissionType.VIEW && pset.contains(PermissionType.VIEW))) return true;
                        if(required==PermissionType.DOWNLOAD && pset.contains(PermissionType.DOWNLOAD)) return true;
                    }
                }
                current = parent;
            }
        }

        // By default, any approved user has VIEW, DOWNLOAD and UPLOAD access to shared resources
        return true;
    }

    public void grant(String userId, String resourceId, EnumSet<PermissionType> grants){
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.execSQL("INSERT OR REPLACE INTO permissions (id, user_id, resource_opaque_id, grants, created_at) VALUES (?,?,?,?,?)",
                new Object[]{SecureTokenGenerator.generateOpaqueId(), userId, resourceId, serialize(grants), System.currentTimeMillis()});
    }
    public void revoke(String userId, String resourceId){
        dbHelper.getWritableDatabase().delete("permissions","user_id=? AND resource_opaque_id=?", new String[]{userId, resourceId});
    }

    private EnumSet<PermissionType> parse(String s){
        EnumSet<PermissionType> set = EnumSet.noneOf(PermissionType.class);
        if(s==null) return set;
        for(String p: s.split(",")){
            try{ set.add(PermissionType.valueOf(p.trim())); }catch(Exception ignore){}
        }
        return set;
    }
    private String serialize(EnumSet<PermissionType> set){
        StringBuilder sb = new StringBuilder();
        for(PermissionType t: set){ if(sb.length()>0) sb.append(","); sb.append(t.name()); }
        return sb.toString();
    }

    public boolean isUserApproved(String userId){
        if(userId==null) return false;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try(Cursor c = db.query("users", new String[]{"is_approved", "blocked_until"}, "id=?", new String[]{userId}, null,null,null)){
            if(!c.moveToFirst()) return false;
            boolean approved = c.getInt(0) == 1;
            int blockIdx = c.getColumnIndex("blocked_until");
            if(blockIdx >= 0){
                long blockedUntil = c.getLong(blockIdx);
                if(blockedUntil == -1 || (blockedUntil > 0 && blockedUntil > System.currentTimeMillis())){
                    return false; // User is blocked!
                }
            }
            return approved;
        }
    }
}

