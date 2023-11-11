package hasjamon.block4block.listener;

import com.google.common.collect.Sets;
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

import java.util.HashSet;

public class FreecamInteract implements Listener {
    private final boolean applyOnlyToLecterns;
    private final HashSet<Material> boatTypes = Sets.newHashSet(
        Material.BIRCH_BOAT,
        Material.ACACIA_BOAT,
        Material.OAK_BOAT,
        Material.JUNGLE_BOAT,
        Material.SPRUCE_BOAT,
        Material.DARK_OAK_BOAT,
        Material.MANGROVE_BOAT,
        Material.CHERRY_BOAT,
        Material.BAMBOO_RAFT,
        Material.BIRCH_CHEST_BOAT,
        Material.ACACIA_CHEST_BOAT,
        Material.OAK_CHEST_BOAT,
        Material.JUNGLE_CHEST_BOAT,
        Material.SPRUCE_CHEST_BOAT,
        Material.DARK_OAK_CHEST_BOAT,
        Material.MANGROVE_CHEST_BOAT,
        Material.CHERRY_CHEST_BOAT,
        Material.BAMBOO_CHEST_RAFT
    );

    public FreecamInteract(Block4Block plugin){
        applyOnlyToLecterns = plugin.getConfig().getBoolean("disable-freecam-interactions-only-for-lecterns");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e){
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();

        if(b != null && e.getAction() == Action.RIGHT_CLICK_BLOCK)
            if(!applyOnlyToLecterns || b.getType() == Material.LECTERN)
                if(!isPlacingBoatOnWater(b, e.getItem()))
                    if(!isLookingAtBlock(b, p))
                        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent e){
        Block b = e.getBlock();

        if(!applyOnlyToLecterns || b.getType() == Material.LECTERN)
            if(b.getType() != Material.ANDESITE && !isLookingAtBlock(b, e.getPlayer()))
                e.setCancelled(true);
    }

    private boolean isLookingAtBlock(Block b, Player p){
        Location pEyeLoc = p.getEyeLocation();
        Vector direction = pEyeLoc.getDirection();

        RayTraceResult result = p.getWorld().rayTraceBlocks(pEyeLoc, direction, 6.0);

        return result != null && result.getHitBlock() != null && result.getHitBlock().equals(b);
    }

    private boolean isPlacingBoatOnWater(Block b, ItemStack i){
        return b.getType() == Material.WATER && boatTypes.contains(i.getType());
    }
}
