package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

public class EquipPlayerHead implements Listener {
    private final Block4Block plugin;

    public EquipPlayerHead(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent e) {
        ItemStack itemOnCursor = e.getCursor();
        Inventory inv = e.getClickedInventory();

        if(itemOnCursor != null && inv != null && inv.getType() == InventoryType.PLAYER)
            if (itemOnCursor.getType() == Material.PLAYER_HEAD && e.getSlot() == 39)
                if(activateDisguise(e.getWhoClicked(), itemOnCursor))
                    e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        ItemStack itemOnCursor = e.getCursor();
        Inventory inv = e.getInventory();

        if(itemOnCursor != null && inv.getType() == InventoryType.PLAYER)
            if (itemOnCursor.getType() == Material.PLAYER_HEAD && e.getInventorySlots().contains(39))
                if(activateDisguise(e.getWhoClicked(), itemOnCursor))
                    e.setCancelled(true);
    }

    private boolean activateDisguise(HumanEntity whoClicked, ItemStack itemOnCursor){
        SkullMeta meta = (SkullMeta) itemOnCursor.getItemMeta();

        if (meta != null && meta.getOwningPlayer() != null) {
            OfflinePlayer disguisee = meta.getOwningPlayer();

            if(disguisee != null && disguisee.getFirstPlayed() > 0) {
                long duration = plugin.getConfig().getLong("disguise-duration");
                Player disguiser = (Player) whoClicked;
                String disguiseMsg = "You're now disguised as " + disguisee.getName() + " for " + (duration / 1000) + " seconds";

                utils.onLoseDisguise(disguiser);
                utils.disguisePlayer(disguiser, disguisee);
                utils.activeDisguises.put(disguiser, disguisee.getName());

                BukkitTask undisguiseTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    utils.undisguiseTasks.remove(disguiser);
                    utils.restorePlayerSkin(disguiser);
                    utils.onLoseDisguise(disguiser);
                }, 20 * duration / 1000);
                utils.undisguiseTasks.put(disguiser, undisguiseTask);

                if(!utils.isPaperServer)
                    disguiseMsg += " (only visible to other players)";
                disguiser.sendMessage(disguiseMsg);

                itemOnCursor.setAmount(itemOnCursor.getAmount() - 1);
                return true;
            }
        }

        return false;
    }
}
