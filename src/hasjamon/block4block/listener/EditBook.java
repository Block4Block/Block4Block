package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class EditBook implements Listener {
    @EventHandler
    public void onInteract(PlayerInteractEvent e){
        Player p = e.getPlayer();

        // If player right clicks air
        if(e.getAction().equals(Action.RIGHT_CLICK_AIR)){
            ItemStack item = e.getItem();

            // If player is holding a book and quill
            if(item != null && item.getType() == Material.WRITABLE_BOOK){
                // Send the player instruction on what to do with the book
                p.sendMessage(utils.chat("&aWrite a player's ign on each line to add a member!"));
                p.sendMessage(utils.chat("&cNOTE: &7To make a claim book, type&a \"claim\" &7at the top of your book!"));
            }
        }
    }
}
