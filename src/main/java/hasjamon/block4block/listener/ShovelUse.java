package hasjamon.block4block.listener;

import hasjamon.block4block.utils.GracePeriod;
import hasjamon.block4block.utils.utils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class ShovelUse implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onShovelUse(PlayerInteractEvent e){
        if(e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block b = e.getClickedBlock();
            Material itemType = e.getMaterial();

            if (isShovel(itemType) && b != null) {
                if (b.getType() == Material.DIRT_PATH) {
                    utils.removeExpiredBlockChangeGracePeriods();

                    // If the block is still covered by the grace period, change it back to grass_block
                    if (utils.blockChangeGracePeriods.containsKey(b.getLocation())) {
                        Material oldType = utils.blockChangeGracePeriods.get(b.getLocation()).type;

                        b.setType(isExpensiveShovel(itemType) ? Material.DIRT : oldType);
                        e.setCancelled(true);
                    }
                } else if (b.getType() == Material.GRASS_BLOCK || b.getType() == Material.PODZOL) {
                    GracePeriod gracePeriod = new GracePeriod(System.nanoTime(), b.getType());

                    utils.blockChangeGracePeriods.put(b.getLocation(), gracePeriod);
                }
            }
        }
    }

    private boolean isExpensiveShovel(Material mat){
        switch (mat){
            case NETHERITE_SHOVEL, DIAMOND_SHOVEL, GOLDEN_SHOVEL -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean isShovel(Material mat){
        switch (mat){
            case NETHERITE_SHOVEL, DIAMOND_SHOVEL, GOLDEN_SHOVEL, IRON_SHOVEL, STONE_SHOVEL, WOODEN_SHOVEL -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
