package com.localshare.domain.model;

public class SharedResource {
    public String opaqueId;
    public String displayName;
    public String safUri; // persisted SAF URI string
    public boolean isFolder;
    public String parentOpaqueId; // null for root selected
    public long addedAt;
    public long size;
}

