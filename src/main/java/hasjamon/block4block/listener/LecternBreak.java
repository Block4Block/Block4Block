package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.ClaimRemovedEvent;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class LecternBreak implements Listener {
    private final Block4Block plugin;

    public LecternBreak(Block4Block plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (block.getType() == Material.LECTERN) {
            String claimID = utils.getClaimID(block.getLocation());

            if (plugin.cfg.getClaimData().contains(claimID) && utils.isClaimBlock(block)) {
                Player player = event.getPlayer();
                boolean isMember = utils.isMemberOfClaim(utils.getMembers(claimID), player);
                long numProtectedSides = utils.countProtectedSides(block);
                boolean isInvulnerable = utils.isClaimInvulnerable(block);

                if (isMember || numProtectedSides == 0 && !isInvulnerable) {
                    utils.unclaimChunk(block, true, player::sendMessage);
                    plugin.pluginManager.callEvent(new ClaimRemovedEvent(player, block, isMember));
                } else {
                    if (!isInvulnerable) {
                        String msg = utils.chat("&aLectern is still protected from &c" + numProtectedSides + " &asides");
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
                    }
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent e) {
        e.blockList().removeIf(utils::isProtectedClaimLectern);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        Block block = e.getBlock();

        if (utils.isProtectedClaimLectern(block))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakMonitor(BlockBreakEvent e) {
        Block b = e.getBlock();

        if (b.getType() == Material.LECTERN) {
            String claimID = utils.getClaimID(b.getLocation());

            if (plugin.cfg.getClaimData().contains(claimID) && utils.isClaimBlock(b)) {
                Player p = e.getPlayer();
                boolean isMember = utils.isMemberOfClaim(utils.getMembers(claimID), p);

                utils.unclaimChunk(b, true, p::sendMessage);
                plugin.pluginManager.callEvent(new ClaimRemovedEvent(p, b, isMember));
            }
        }
    }

    // If a claim lectern is destroyed by an exploding entity, unclaim the chunk
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplodeMonitor(EntityExplodeEvent event) {
        for (Block block : event.blockList())
            if (utils.isUnprotectedClaimLectern(block))
                utils.unclaimChunk(block, false, (msg) -> {
                });
    }

    // If a claim lectern is destroyed by an exploding block, unclaim the chunk
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplodeMonitor(BlockExplodeEvent event) {
        for (Block block : event.blockList())
            if (utils.isUnprotectedClaimLectern(block))
                utils.unclaimChunk(block, false, (msg) -> {
                });
    }

    // If a claim lectern is destroyed in a fire, unclaim the chunk
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurnMonitor(BlockBurnEvent e) {
        Block block = e.getBlock();

        if (utils.isUnprotectedClaimLectern(block))
            utils.unclaimChunk(block, false, (msg) -> {
            });
    }
}
