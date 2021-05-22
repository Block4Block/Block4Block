package hasjamon.block4block.listener;

import hasjamon.block4block.utils.utils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoin implements Listener {
    @EventHandler
    public void LogOnGMC(PlayerJoinEvent e) {
        utils.updateClaimCount();
    }
}
