package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuit implements Listener {
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e){
        Player p = e.getPlayer();
        Chunk chunk = p.getLocation().getChunk();

        if(utils.intruders.containsKey(chunk))
            utils.intruders.get(chunk).remove(p);
    }
}