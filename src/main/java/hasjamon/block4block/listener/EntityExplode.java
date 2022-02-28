package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

public class EntityExplode implements Listener {
    private final Block4Block plugin;

    public EntityExplode(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityExplode(EntityExplodeEvent e){
        World.Environment environment = e.getEntity().getWorld().getEnvironment();
        String dimension = switch(environment){
            case NORMAL -> "overworld";
            case NETHER -> "nether";
            case THE_END -> "end";
            case CUSTOM -> "custom";
        };

        List<?> claimImmunity = plugin.getConfig().getList("claim-explosion-immunity." + dimension);

        // If claims in the current dimension are immune to explosions from the entity
        if(claimImmunity != null && claimImmunity.contains(e.getEntityType().toString())){
            FileConfiguration claimData = plugin.cfg.getClaimData();

            // Remove blocks from the to-be-exploded list if they're inside a claim
            e.blockList().removeIf(b -> claimData.contains(utils.getClaimID(b.getLocation())));
        }

        if(e.getEntityType() == EntityType.PRIMED_TNT || e.getEntityType() == EntityType.MINECART_TNT) {
            List<String> tntDropsEnabled = plugin.getConfig().getStringList("tnt-drops-enabled");

            for (Block block : e.blockList()) {
                if(!tntDropsEnabled.contains(block.getType().toString())){
                    block.setType(Material.AIR);
                }
            }
        }
    }
}
