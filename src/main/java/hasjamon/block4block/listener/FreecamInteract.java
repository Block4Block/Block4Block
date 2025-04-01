package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Set;

public class FreecamInteract implements Listener {
    private final boolean applyOnlyToLecterns;
    private final Set<Material> breakExceptions = Set.of(
            Material.SCAFFOLDING,
            Material.WHEAT
    );

    public FreecamInteract(Block4Block plugin) {
        applyOnlyToLecterns = plugin.getConfig().getBoolean("disable-freecam-interactions-only-for-lecterns");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();

        if (b != null && e.getAction() == Action.RIGHT_CLICK_BLOCK)
            if (!applyOnlyToLecterns || b.getType() == Material.LECTERN)
                if (!isPlacingBoatOnWater(b, e.getItem()))
                    if (!isLookingAtBlock(b, p))
                        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent e) {
        Block b = e.getBlock();

        if (breakExceptions.contains(b.getType())) return; // Allow breaking exceptions freely

        if (!applyOnlyToLecterns || b.getType() == Material.LECTERN)
            if (b.getType() != Material.ANDESITE && !isLookingAtBlock(b, e.getPlayer()))
                e.setCancelled(true);
    }

    private boolean isLookingAtBlock(Block b, Player p) {
        Location pEyeLoc = p.getEyeLocation();
        Vector direction = pEyeLoc.getDirection();

        RayTraceResult result = p.getWorld().rayTraceBlocks(pEyeLoc, direction, 6.0);

        return result != null && result.getHitBlock() != null && result.getHitBlock().equals(b);
    }

    private boolean isPlacingBoatOnWater(Block b, ItemStack i) {
        return b.getType() == Material.WATER && i != null &&
                (i.getType().name().endsWith("_BOAT") || i.getType().name().endsWith("_RAFT"));
    }
}
