package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PlayerLavaDamage implements Listener {
    private final Map<Player, Long> lastImmunityMessageReceived = new HashMap<>();
    private final int minSecBetweenMsgs;

    public PlayerLavaDamage(Block4Block plugin){
        minSecBetweenMsgs = plugin.getConfig().getInt("seconds-between-lava-immunity-msgs", 60);
    }

    @EventHandler
    public void onPlayerLavaDamage(EntityDamageByBlockEvent e){
        if(e.getCause() == EntityDamageEvent.DamageCause.LAVA){
            if(e.getEntityType() == EntityType.PLAYER){
                Player p = (Player) e.getEntity();

                // If the player's inventory is empty
                if(Arrays.stream(p.getInventory().getContents()).noneMatch(Objects::nonNull)){
                    long now = System.nanoTime();

                    if(now - lastImmunityMessageReceived.getOrDefault(p, 0L) >= minSecBetweenMsgs * 1e9) {
                        String msg = "The lava wants to burn your items, but you have none, so it will let you go unharmed. This time.";

                        p.sendMessage(ChatColor.YELLOW + msg);
                        lastImmunityMessageReceived.put(p, now);
                    }
                    e.setCancelled(true);
                }
            }
        }
    }
}
