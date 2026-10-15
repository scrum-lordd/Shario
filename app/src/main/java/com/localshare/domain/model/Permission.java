package com.localshare.domain.model;

import java.util.EnumSet;

/* JADX INFO: loaded from: classes10.dex */
public class Permission {
    public long createdAt;
    public EnumSet<PermissionType> grants = EnumSet.noneOf(PermissionType.class);
    public String id;
    public String resourceOpaqueId;
    public String userId;
}
