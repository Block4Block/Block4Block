package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

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
        EntityType entityType = e.getEntityType();

        // If claims in the current dimension are immune to explosions from the entity
        if(claimImmunity != null && claimImmunity.contains(entityType.toString())){
            FileConfiguration claimData = plugin.cfg.getClaimData();

            // Remove blocks from the to-be-exploded list if they're inside a claim
            e.blockList().removeIf(b -> claimData.contains(utils.getClaimID(b.getLocation())));
        }

        e.blockList().removeIf(b -> !utils.getClaimBlocksProtectedBy(b).isEmpty());

        if(entityType == EntityType.PRIMED_TNT || entityType == EntityType.MINECART_TNT) {
            List<String> tntDropsEnabled = plugin.getConfig().getStringList("tnt-drops-enabled");

            for (Block block : e.blockList()) {
                if(!tntDropsEnabled.contains(block.getType().toString())){
                    block.setType(Material.AIR);
                }
            }
        }else if(entityType == EntityType.CREEPER){
            Location location = e.getLocation();
            Optional<World> worldOpt = Optional.ofNullable(location.getWorld());

            worldOpt.ifPresent(world ->
                    world.dropItemNaturally(location, new ItemStack(Material.GUNPOWDER))
            );
        }
    }
}
