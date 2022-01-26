package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Set;

public class PlayerChat implements Listener {
    private final Block4Block plugin;

    public PlayerChat(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        Player sender = e.getPlayer();
        FileConfiguration ignoreLists = plugin.cfg.getIgnoreLists();
        Set<Player> recipients = e.getRecipients();
        long now = System.nanoTime();

        recipients.removeIf(r -> now < ignoreLists.getLong(sender.getUniqueId() + "." + r.getUniqueId(), 0));
    }
}
