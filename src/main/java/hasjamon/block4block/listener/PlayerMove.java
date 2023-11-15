package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.GameMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMove implements Listener {
    private final Block4Block plugin;

    public PlayerMove(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e){
        Player p = e.getPlayer();

        if(p.getGameMode() == GameMode.SURVIVAL && e.getTo() != null) {
            String prevChunkID = utils.getChunkID(e.getFrom());
            String currentChunkID = utils.getChunkID(e.getTo());

            // If p has entered a new chunk
            if (!prevChunkID.equals(currentChunkID)) {
                String prevClaimID = utils.getClaimID(e.getFrom());
                String currentClaimID = utils.getClaimID(e.getTo());
                boolean isIntruder = utils.isIntruder(p, currentClaimID);

                // If p has entered a new claim
                if (!prevClaimID.equals(currentClaimID)) {
                    // Remove p from the previous chunk's intruder list
                    utils.onIntruderLeaveClaim(p, prevClaimID);

                    if (isIntruder) {
                        utils.onIntruderEnterClaim(p, currentClaimID);
                    }
                }

                // If p is currently an intruder
                if (isIntruder) {
                    // Make all iron golems in chunk hostile to the intruder
                    if(plugin.getConfig().getBoolean("golems-guard-claims"))
                        for(IronGolem golem : utils.ironGolems.keySet())
                            if(currentChunkID.equals(utils.getChunkID(golem.getLocation())))
                                golem.damage(0, p);
                }

                utils.updateBossBar(p, currentClaimID);
            }

            utils.lastPlayerMoves.put(p, System.nanoTime());
        }
    }
}
