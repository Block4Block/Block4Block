package hasjamon.block4block.listener;

import com.comphenix.protocol.utility.MinecraftReflection;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import hasjamon.block4block.Block4Block;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

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

                disguisePlayer((Player) whoClicked, disguisee);
                whoClicked.sendMessage("You're now disguised as " + disguisee.getName() + " for " + (duration/1000) + " seconds");

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // TODO: Remove disguise
                }, 20 * duration / 1000);

                itemOnCursor.setAmount(itemOnCursor.getAmount() - 1);
                return true;
            }
        }

        /*
        if (meta != null && meta.getOwningPlayer() != null) {
            String disguiseeName = meta.getOwningPlayer().getName();

            if(disguiseeName != null) {
                PlayerDisguise disguise = new PlayerDisguise(disguiseeName);
                long duration = plugin.getConfig().getLong("disguise-duration");

                disguise.setExpires(System.currentTimeMillis() + duration);
                DisguiseAPI.disguiseToAll(whoClicked, disguise);
                whoClicked.sendMessage("You're now disguised as " + disguiseeName + " for " + (duration/1000) + " seconds");

                itemOnCursor.setAmount(itemOnCursor.getAmount() - 1);
            }
        }
        */

        return false;
    }

    public void disguisePlayer(Player disguiser, OfflinePlayer disguisee) {
        Collection<Property> textures;
        Method getProfile;

        try{
            getProfile = MinecraftReflection.getCraftPlayerClass().getDeclaredMethod("getProfile");

            GameProfile gpA = (GameProfile) getProfile.invoke(disguisee);
            textures = gpA.getProperties().get("textures");
            //GameProfileBuilder.fetch(disguisee.getUniqueId()).getProperties().get("textures");

            GameProfile gpB = (GameProfile) getProfile.invoke(disguiser);
            gpB.getProperties().removeAll("textures");
            gpB.getProperties().putAll("textures", textures);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hidePlayer(plugin, disguiser);
            p.showPlayer(plugin, disguiser);
        }
    }
}
