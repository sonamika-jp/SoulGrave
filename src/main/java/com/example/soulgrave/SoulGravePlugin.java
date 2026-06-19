package com.example.soulgrave;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SoulGravePlugin extends JavaPlugin {

    private GraveManager graveManager;
    private final Map<UUID, UUID> hologramMap = new HashMap<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        graveManager = new GraveManager(this);
        graveManager.loadAll();

        getServer().getPluginManager().registerEvents(new GraveListener(this, graveManager), this);

        // 5分ごとに期限切れチェック
        new BukkitRunnable() {
            @Override
            public void run() {
                graveManager.checkExpired();
            }
        }.runTaskTimer(this, 20L * 60 * 5, 20L * 60 * 5);

        getLogger().info("SoulGrave が有効になりました！");
    }

    @Override
    public void onDisable() {
        getLogger().info("SoulGrave が無効になりました。");
    }

    public GraveManager getGraveManager() { return graveManager; }
    public Map<UUID, UUID> getHologramMap() { return hologramMap; }
}