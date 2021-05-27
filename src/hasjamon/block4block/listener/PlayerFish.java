package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public class PlayerFish implements Listener {
    private final Block4Block plugin;

    public PlayerFish(Block4Block plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent e){
        Player p = e.getPlayer();

        if(e.getState() == PlayerFishEvent.State.CAUGHT_FISH){
            if(e.getCaught().getType() == EntityType.DROPPED_ITEM){
                if (((Item) e.getCaught()).getItemStack().getType() == Material.NAME_TAG){
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        p.getInventory().remove(p.getInventory().getItemInMainHand());
                    }, 1);
                    p.sendMessage(utils.chat("&7Wow, you got a &6Name Tag!&7 Too bad your fishing rod broke..."));
                }
            }
        }
    }
}
