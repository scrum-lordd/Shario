package com.localshare.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import com.localshare.domain.model.SharedResource;
import com.localshare.security.SecureTokenGenerator;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes3.dex */
public class SharedResourceDao {
    private final DbHelper h;

   public SharedResourceDao(DbHelper h) {
        this.h = h;
    }
        public String insert(String str, String str2, boolean z, String str3) {
        String strGenerateOpaqueId = SecureTokenGenerator.generateOpaqueId();
        ContentValues contentValues = new ContentValues();
        contentValues.put("opaque_id", strGenerateOpaqueId);
        contentValues.put("display_name", str);
        contentValues.put("saf_uri", str2);
        contentValues.put("is_folder", Integer.valueOf(z ? 1 : 0));
        contentValues.put("parent_opaque_id", str3);
        contentValues.put("added_at", Long.valueOf(System.currentTimeMillis()));
        this.h.getWritableDatabase().insertOrThrow("shared_resources", null, contentValues);
        return strGenerateOpaqueId;
    }
     public List<SharedResource> listAll() {
        List<SharedResource> list = new ArrayList<>();
        Cursor c = this.h.getReadableDatabase().query("shared_resources", null, null, null, null, null, "added_at DESC");
        while (c.moveToNext()) {
            try {
                SharedResource r = new SharedResource();
                r.opaqueId = c.getString(c.getColumnIndexOrThrow("opaque_id"));
                r.displayName = c.getString(c.getColumnIndexOrThrow("display_name"));
                r.safUri = c.getString(c.getColumnIndexOrThrow("saf_uri"));
                boolean z = true;
                if (c.getInt(c.getColumnIndexOrThrow("is_folder")) != 1) {
                    z = false;
                }
                r.isFolder = z;
                r.parentOpaqueId = c.getString(c.getColumnIndexOrThrow("parent_opaque_id"));
                r.addedAt = c.getLong(c.getColumnIndexOrThrow("added_at"));
                list.add(r);
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
        if (c != null) {
            c.close();
        }
        return list;
    }

}
