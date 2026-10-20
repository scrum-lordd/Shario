package com.localshare.domain.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.localshare.domain.model.PermissionType;
import com.localshare.persistence.DbHelper;
import com.localshare.security.SecureTokenGenerator;
import java.util.EnumSet;

/* JADX INFO: loaded from: classes7.dex */
public class AccessControlService {
    private final DbHelper dbHelper;

    public AccessControlService(DbHelper h) {
        this.dbHelper = h;
    }

    public boolean hasPermission(String userId, String resourceOpaqueId, PermissionType required) {
        if (userId == null || resourceOpaqueId == null) {
            return false;
        }
        SQLiteDatabase db = this.dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT grants FROM permissions WHERE user_id=? AND resource_opaque_id=?", new String[]{userId, resourceOpaqueId});
        try {
            if (c.moveToFirst()) {
                String grants = c.getString(0);
                EnumSet<PermissionType> set = parse(grants);
                if (set.contains(required)) {
                    if (c != null) {
                        c.close();
                    }
                    return true;
                }
            }
            if (c != null) {
                c.close();
            }
            String current = resourceOpaqueId;
            for (int depth = 0; depth < 20; depth++) {
                Cursor pc = db.rawQuery("SELECT parent_opaque_id FROM shared_resources WHERE opaque_id=?", new String[]{current});
                try {
                    if (!pc.moveToFirst()) {
                        if (pc == null) {
                            break;
                        }
                        pc.close();
                        break;
                    }
                    String parent = pc.getString(0);
                    if (parent == null) {
                        if (pc == null) {
                            break;
                        }
                        pc.close();
                        break;
                    }
                    Cursor gc = db.rawQuery("SELECT grants FROM permissions WHERE user_id=? AND resource_opaque_id=?", new String[]{userId, parent});
                    try {
                        if (gc.moveToFirst()) {
                            EnumSet<PermissionType> pset = parse(gc.getString(0));
                            if (!pset.contains(required) && (required != PermissionType.VIEW || !pset.contains(PermissionType.VIEW))) {
                                if (required == PermissionType.DOWNLOAD && pset.contains(PermissionType.DOWNLOAD)) {
                                    if (gc != null) {
                                        gc.close();
                                    }
                                    if (pc != null) {
                                        pc.close();
                                    }
                                    return true;
                                }
                            }
                            if (gc != null) {
                                gc.close();
                            }
                            if (pc != null) {
                                pc.close();
                            }
                            return true;
                        }
                        if (gc != null) {
                            gc.close();
                        }
                        current = parent;
                        if (pc != null) {
                            pc.close();
                        }
                    } catch (Throwable th) {
                        if (gc != null) {
                            try {
                                gc.close();
                            } catch (Throwable th2) {
                                th.addSuppressed(th2);
                            }
                        }
                        throw th;
                    }
                } catch (Throwable th3) {
                    if (pc != null) {
                        try {
                            pc.close();
                        } catch (Throwable th4) {
                            th3.addSuppressed(th4);
                        }
                    }
                    throw th3;
                }
            }
            return false;
        } catch (Throwable th5) {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable th6) {
                    th5.addSuppressed(th6);
                }
            }
            throw th5;
        }
    }

    public void grant(String userId, String resourceId, EnumSet<PermissionType> grants) {
        SQLiteDatabase db = this.dbHelper.getWritableDatabase();
        db.execSQL("INSERT OR REPLACE INTO permissions (id, user_id, resource_opaque_id, grants, created_at) VALUES (?,?,?,?,?)", new Object[]{SecureTokenGenerator.generateOpaqueId(), userId, resourceId, serialize(grants), Long.valueOf(System.currentTimeMillis())});
    }

    public void revoke(String userId, String resourceId) {
        this.dbHelper.getWritableDatabase().delete("permissions", "user_id=? AND resource_opaque_id=?", new String[]{userId, resourceId});
    }

    private EnumSet<PermissionType> parse(String s) {
        EnumSet<PermissionType> set = EnumSet.noneOf(PermissionType.class);
        if (s == null) {
            return set;
        }
        for (String p : s.split(",")) {
            try {
                set.add(PermissionType.valueOf(p.trim()));
            } catch (Exception e) {
            }
        }
        return set;
    }

    private String serialize(EnumSet<PermissionType> set) {
        StringBuilder sb = new StringBuilder();
        for (PermissionType t : set) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(t.name());
        }
        return sb.toString();
    }

    public boolean isUserApproved(String userId) {
        SQLiteDatabase db = this.dbHelper.getReadableDatabase();
        Cursor c = db.query("users", new String[]{"is_approved"}, "id=?", new String[]{userId}, null, null, null);
        try {
            if (c.moveToFirst()) {
                boolean z = c.getInt(0) == 1;
                if (c != null) {
                    c.close();
                }
                return z;
            }
            if (c != null) {
                c.close();
            }
            return false;
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
}
