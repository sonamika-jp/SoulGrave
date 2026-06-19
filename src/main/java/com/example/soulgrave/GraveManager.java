package com.example.soulgrave;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GraveManager {

    private final SoulGravePlugin plugin;
    private final Map<UUID, GraveData> graves = new HashMap<>();
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(ZoneId.of("Asia/Tokyo"));
    private static final Random RANDOM = new Random();

    public GraveManager(SoulGravePlugin plugin) {
        this.plugin = plugin;
    }

    public void createGrave(UUID ownerUUID, String ownerName, Location deathLoc, List<ItemStack> items, int exp) {
        long now = System.currentTimeMillis();
        UUID graveId = UUID.randomUUID();

        Location graveLoc = findSafeLocation(deathLoc);
        boolean isEmergency = false;

        if (graveLoc == null) {
            World overworld = plugin.getServer().getWorlds().get(0);
            int rx = RANDOM.nextInt(2000) - 1000;
            int rz = RANDOM.nextInt(2000) - 1000;
            int ry = overworld.getHighestBlockYAt(rx, rz) + 1;
            graveLoc = new Location(overworld, rx, ry, rz);
            isEmergency = true;

            final Location finalLoc = graveLoc;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                var owner = plugin.getServer().getPlayer(ownerUUID);
                if (owner != null) {
                    owner.sendMessage("§c安全な場所が見つからなかったため、通常ワールドにランダム生成されました。");
                    owner.sendMessage("§e座標: " + finalLoc.getBlockX() + ", " + finalLoc.getBlockY() + ", " + finalLoc.getBlockZ());
                    owner.sendMessage("§c1時間後に消滅します！");
                }
            }, 20L);
        }

        GraveData data = new GraveData(graveId, ownerUUID, ownerName, graveLoc, items, exp, now, isEmergency);
        graves.put(graveId, data);

        placeGraveBlocks(graveLoc);
        spawnHologram(graveId, graveLoc, ownerName, now, isEmergency);
        saveGrave(data);
    }

    private void placeGraveBlocks(Location loc) {
        World world = loc.getWorld();
        world.getBlockAt(loc).setType(Material.SOUL_SAND);
        world.getBlockAt(loc.clone().add(-1, 0, 0)).setType(Material.SOUL_SAND);
        world.getBlockAt(loc.clone().add(0, 1, 0)).setType(Material.QUARTZ_BLOCK);
        world.getBlockAt(loc.clone().add(0, 2, 0)).setType(Material.WITHER_SKELETON_SKULL);
    }

    private void spawnHologram(UUID graveId, Location loc, String ownerName, long createdAt, boolean isEmergency) {
        Location hologramLoc = loc.clone().add(0.5, 3.5, 0.5);
        World world = loc.getWorld();

        TextDisplay display = world.spawn(hologramLoc, TextDisplay.class, td -> {
            String time = FORMATTER.format(Instant.ofEpochMilli(createdAt));
            String expireLabel = isEmergency ? "§c1時間後に消滅" : "§724時間後に消滅";
            td.setText("§e" + ownerName + " の墓\n§7" + time + "\n" + expireLabel);
            td.setBillboard(Display.Billboard.CENTER);
            td.setBackgroundColor(org.bukkit.Color.fromARGB(80, 0, 0, 0));
        });

        plugin.getHologramMap().put(graveId, display.getUniqueId());
    }

    public void removeGrave(UUID graveId) {
        GraveData data = graves.get(graveId);
        if (data == null) return;

        Location loc = data.getLocation();
        World world = loc.getWorld();

        world.getBlockAt(loc).setType(Material.AIR);
        world.getBlockAt(loc.clone().add(-1, 0, 0)).setType(Material.AIR);
        world.getBlockAt(loc.clone().add(0, 1, 0)).setType(Material.AIR);
        world.getBlockAt(loc.clone().add(0, 2, 0)).setType(Material.AIR);

        UUID hologramId = plugin.getHologramMap().remove(graveId);
        if (hologramId != null) {
            world.getEntities().stream()
                    .filter(e -> e.getUniqueId().equals(hologramId))
                    .findFirst()
                    .ifPresent(org.bukkit.entity.Entity::remove);
        }

        graves.remove(graveId);
        deleteGraveFile(graveId);
    }

    public void checkExpired() {
        new ArrayList<>(graves.keySet()).forEach(id -> {
            if (graves.get(id).isExpired()) removeGrave(id);
        });
    }

    private Location findSafeLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return null;

        Location check = loc.clone();
        for (int i = 0; i < 64; i++) {
            Material below = world.getBlockAt(check.clone().add(0, -1, 0)).getType();
            Material current = world.getBlockAt(check).getType();
            Material above = world.getBlockAt(check.clone().add(0, 1, 0)).getType();

            if (!below.isAir() && below != Material.LAVA
                    && current.isAir() && above.isAir()) {
                return check;
            }
            check.add(0, 1, 0);

            if (check.getBlockY() > world.getMaxHeight()) return null;
        }
        return null;
    }

    private void saveGrave(GraveData data) {
        File file = new File(plugin.getDataFolder(), data.getGraveId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("graveId", data.getGraveId().toString());
        config.set("ownerUUID", data.getOwnerUUID().toString());
        config.set("ownerName", data.getOwnerName());
        config.set("world", data.getLocation().getWorld().getName());
        config.set("x", data.getLocation().getBlockX());
        config.set("y", data.getLocation().getBlockY());
        config.set("z", data.getLocation().getBlockZ());
        config.set("createdAt", data.getCreatedAt());
        config.set("isEmergency", data.isEmergency());
        config.set("exp", data.getExp());

        List<ItemStack> items = data.getItems();
        for (int i = 0; i < items.size(); i++) {
            config.set("items." + i, items.get(i));
        }

        UUID hologramId = plugin.getHologramMap().get(data.getGraveId());
        if (hologramId != null) config.set("hologramId", hologramId.toString());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("墓データの保存に失敗: " + e.getMessage());
        }
    }

    public void loadAll() {
        File folder = plugin.getDataFolder();
        if (!folder.exists()) return;
        File[] files = folder.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            try {
                UUID graveId = UUID.fromString(config.getString("graveId"));
                UUID ownerUUID = UUID.fromString(config.getString("ownerUUID"));
                String ownerName = config.getString("ownerName");
                World world = plugin.getServer().getWorld(config.getString("world"));
                if (world == null) continue;

                Location loc = new Location(world,
                        config.getInt("x"), config.getInt("y"), config.getInt("z"));
                long createdAt = config.getLong("createdAt");
                boolean isEmergency = config.getBoolean("isEmergency", false);
                int exp = config.getInt("exp", 0);

                List<ItemStack> items = new ArrayList<>();
                if (config.isConfigurationSection("items")) {
                    for (String key : config.getConfigurationSection("items").getKeys(false)) {
                        ItemStack item = config.getItemStack("items." + key);
                        if (item != null) items.add(item);
                    }
                }

                GraveData data = new GraveData(graveId, ownerUUID, ownerName, loc, items, exp, createdAt, isEmergency);

                if (data.isExpired()) {
                    removeGrave(graveId);
                    continue;
                }

                graves.put(graveId, data);

                if (config.contains("hologramId")) {
                    plugin.getHologramMap().put(graveId, UUID.fromString(config.getString("hologramId")));
                }

            } catch (Exception e) {
                plugin.getLogger().warning("墓データの読み込みに失敗: " + file.getName());
            }
        }
    }

    private void deleteGraveFile(UUID graveId) {
        File file = new File(plugin.getDataFolder(), graveId + ".yml");
        if (file.exists()) file.delete();
    }

    public Map<UUID, GraveData> getGraves() { return graves; }

    public GraveData getGraveAt(Location loc) {
        for (GraveData data : graves.values()) {
            Location gLoc = data.getLocation();
            if (!gLoc.getWorld().equals(loc.getWorld())) continue;
            if ((gLoc.getBlockX() == loc.getBlockX() && gLoc.getBlockY() == loc.getBlockY() && gLoc.getBlockZ() == loc.getBlockZ()) ||
                    (gLoc.getBlockX() - 1 == loc.getBlockX() && gLoc.getBlockY() == loc.getBlockY() && gLoc.getBlockZ() == loc.getBlockZ()) ||
                    (gLoc.getBlockX() == loc.getBlockX() && gLoc.getBlockY() + 1 == loc.getBlockY() && gLoc.getBlockZ() == loc.getBlockZ()) ||
                    (gLoc.getBlockX() == loc.getBlockX() && gLoc.getBlockY() + 2 == loc.getBlockY() && gLoc.getBlockZ() == loc.getBlockZ())) {
                return data;
            }
        }
        return null;
    }
}