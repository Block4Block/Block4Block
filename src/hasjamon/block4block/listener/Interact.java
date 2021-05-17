package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class Interact implements Listener {
    @EventHandler
    public void onInteract(PlayerInteractEvent e){
        if(e.getAction() == Action.LEFT_CLICK_BLOCK){
            Block b = e.getClickedBlock();

            if(b != null && b.getType() == Material.ANDESITE) {
                Player p = e.getPlayer();
                Block4Block.instance.pluginManager.callEvent(new BlockBreakEvent(b, p));
            }
        }
    }
}
