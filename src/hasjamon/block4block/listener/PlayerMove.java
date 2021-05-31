package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMove implements Listener {
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e){
        if(e.getTo() != null) {
            Player p = e.getPlayer();
            Chunk prevChunk = e.getFrom().getChunk();
            Chunk currentChunk = e.getTo().getChunk();

            if (prevChunk != currentChunk) {
                // Remove p from the previous chunk's intruder list
                utils.onIntruderLeaveClaim(p, prevChunk);

                // If p is an intruder in the chunk he just entered, register him
                if (utils.isIntruder(p, currentChunk))
                    utils.onIntruderEnterClaim(p, currentChunk);
            }
        }
    }
}
