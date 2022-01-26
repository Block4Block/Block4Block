package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerRespawn implements Listener {
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e){
        Player p = e.getPlayer();
        String chunkID = utils.getChunkID(e.getRespawnLocation());

        if(utils.isIntruder(p, chunkID))
            utils.onIntruderEnterClaim(p, chunkID);
    }
}
