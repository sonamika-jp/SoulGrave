package com.example.soulgrave;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class GraveData {

    private final UUID graveId;
    private final UUID ownerUUID;
    private final String ownerName;
    private final Location location;
    private final List<ItemStack> items;
    private final int exp;
    private final long createdAt;
    private final boolean isEmergency; // ランダム座標生成フラグ

    public GraveData(UUID graveId, UUID ownerUUID, String ownerName, Location location,
                     List<ItemStack> items, int exp, long createdAt, boolean isEmergency) {
        this.graveId = graveId;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.location = location;
        this.items = items;
        this.exp = exp;
        this.createdAt = createdAt;
        this.isEmergency = isEmergency;
    }

    public UUID getGraveId() { return graveId; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public Location getLocation() { return location; }
    public List<ItemStack> getItems() { return items; }
    public int getExp() { return exp; }
    public long getCreatedAt() { return createdAt; }
    public boolean isEmergency() { return isEmergency; }

    public boolean isExpired() {
        long limit = isEmergency ? 60 * 60 * 1000L : 24 * 60 * 60 * 1000L;
        return System.currentTimeMillis() - createdAt > limit;
    }
}