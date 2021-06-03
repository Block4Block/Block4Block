package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public class PlayerJoin implements Listener {
    private final Block4Block plugin;

    public PlayerJoin(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        List<String> welcomeMessages = plugin.getConfig().getStringList("welcome-messages");

        if(!p.hasPlayedBefore())
            for(String msg : welcomeMessages)
                p.sendMessage(utils.chat(msg));
        else
            utils.populatePlayerClaimsIntruded(p);

        utils.updateClaimCount();

        String chunkID = utils.getChunkID(p.getLocation());
        if(utils.isIntruder(p, chunkID))
            utils.onIntruderEnterClaim(p, chunkID);
    }
}
