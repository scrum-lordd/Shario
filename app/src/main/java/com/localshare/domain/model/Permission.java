package com.localshare.domain.model;

import java.util.EnumSet;

public class Permission {
    public long createdAt;
    public EnumSet<PermissionType> grants = EnumSet.noneOf(PermissionType.class);
    public String id;
    public String resourceOpaqueId;
    public String userId;
}
