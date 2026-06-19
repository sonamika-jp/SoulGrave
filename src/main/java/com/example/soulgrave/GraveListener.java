package com.example.soulgrave;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GraveListener implements Listener {

    private final SoulGravePlugin plugin;
    private final GraveManager manager;

    public GraveListener(SoulGravePlugin plugin, GraveManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player player = e.getEntity();
        List<ItemStack> drops = new ArrayList<>(e.getDrops());
        int exp = e.getDroppedExp();

        if (drops.isEmpty() && exp == 0) return;

        e.getDrops().clear();
        e.setDroppedExp(0);

        manager.createGrave(
                player.getUniqueId(),
                player.getName(),
                player.getLocation(),
                drops,
                exp
        );

        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        player.sendMessage("§7あなたの墓が §e" + player.getWorld().getName() + " §7(" + x + ", " + y + ", " + z + ") §7に生成されました。");
        player.sendMessage("§724時間後に消滅します。");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player player = e.getPlayer();
        GraveData data = manager.getGraveAt(e.getBlock().getLocation());
        if (data == null) return;

        e.setCancelled(true);

        // アイテムを返却
        for (ItemStack item : data.getItems()) {
            if (item != null) {
                player.getInventory().addItem(item).values()
                        .forEach(dropped -> player.getWorld().dropItemNaturally(player.getLocation(), dropped));
            }
        }

        // 経験値を返却
        if (data.getExp() > 0) {
            player.giveExp(data.getExp());
        }

        // 墓の所有者に通知（本人以外が壊した場合）
        if (!player.getUniqueId().equals(data.getOwnerUUID())) {
            Player owner = plugin.getServer().getPlayer(data.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage("§c" + player.getName() + " があなたの墓を壊しました！");
            }
            player.sendMessage("§e" + data.getOwnerName() + " §7の墓を壊してアイテムを回収しました。");
        } else {
            player.sendMessage("§a墓からアイテムを回収しました。");
        }

        manager.removeGrave(data.getGraveId());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        Player player = e.getPlayer();
        GraveData data = manager.getGraveAt(e.getClickedBlock().getLocation());
        if (data == null) return;

        if (!player.getUniqueId().equals(data.getOwnerUUID()) && !player.isOp()) {
            player.sendMessage("§cこれはあなたの墓ではありません。");
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        for (ItemStack item : data.getItems()) {
            if (item != null) {
                player.getInventory().addItem(item).values()
                        .forEach(dropped -> player.getWorld().dropItemNaturally(player.getLocation(), dropped));
            }
        }

        if (data.getExp() > 0) {
            player.giveExp(data.getExp());
        }

        player.sendMessage("§a墓からアイテムを回収しました。");
        manager.removeGrave(data.getGraveId());
    }
}