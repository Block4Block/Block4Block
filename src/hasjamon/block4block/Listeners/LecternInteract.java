package hasjamon.block4block.Listeners;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class LecternInteract implements Listener {
    Block4Block plugin = Block4Block.getInstance();

    // If the player interacts (clicks)
    @EventHandler
    public void onInteract(PlayerInteractEvent e){
        Player p = e.getPlayer();

        if(e.getAction().equals(Action.RIGHT_CLICK_AIR)){ // If player right clicks air
            if(e.getItem().getType() == Material.WRITABLE_BOOK){ // If player is holding a book and quill
                // Send the player instruction on what to do with the book
                e.getPlayer().sendMessage(utils.chat("&aWrite a player's ign on each line to add a member!"));
                if(!(plugin.notify.containsKey(p.getUniqueId()))){
                    p.sendMessage(utils.chat("&cNOTE: &7To make a claim book, type&a \"claim\" &7At the top of your book!"));
                    plugin.notify.put(p.getUniqueId(), true);
                }
            }
        }
    }
}
