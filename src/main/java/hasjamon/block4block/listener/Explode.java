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
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

public class Explode implements Listener {
    private final Block4Block plugin;

    public Explode(Block4Block plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        World.Environment environment = event.getEntity().getWorld().getEnvironment();
        String dimension = switch (environment) {
            case NORMAL -> "overworld";
            case NETHER -> "nether";
            case THE_END -> "end";
            case CUSTOM -> "custom";
        };

        List<String> claimImmunity = plugin.getConfig().getStringList("claim-explosion-immunity." + dimension);
        EntityType entityType = event.getEntityType();

        // If claims in the current dimension are immune to explosions from the entity
        if (claimImmunity.contains(entityType.toString())) {
            FileConfiguration claimData = plugin.cfg.getClaimData();

            // Remove blocks from the to-be-exploded list if they're inside a claim
            event.blockList().removeIf(block -> claimData.contains(utils.getClaimID(block.getLocation())));
        }

        event.blockList().removeIf(block -> !utils.getClaimBlocksProtectedBy(block).isEmpty());

        if (entityType == EntityType.PRIMED_TNT || entityType == EntityType.MINECART_TNT) {
            List<String> tntDropsEnabled = plugin.getConfig().getStringList("tnt-drops-enabled");

            for (Block block : event.blockList()) {
                if (!tntDropsEnabled.contains(block.getType().toString())) {
                    block.setType(Material.AIR);
                }
            }
        } else if (entityType == EntityType.CREEPER) {
            Location location = event.getLocation();
            Optional<World> worldOpt = Optional.ofNullable(location.getWorld());

            worldOpt.ifPresent(world ->
                    world.dropItemNaturally(location, new ItemStack(Material.GUNPOWDER))
            );
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplodeMonitor(BlockExplodeEvent event) {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        List<Material> explodableBlockTypes = plugin.getConfig().getStringList("explodable-by-intended-game-mechanics")
                .stream()
                .map(Material::valueOf)
                .toList();

        // Don't blow up blocks that...
        // (1) Aren't on the relevant whitelist, or
        // (2) Are inside a claim, or
        // (3) Are next to a claim lectern
        event.blockList().removeIf(block ->
                !explodableBlockTypes.contains(block.getType()) ||
                        claimData.contains(utils.getClaimID(block.getLocation())) ||
                        !utils.getClaimBlocksProtectedBy(block).isEmpty());
    }
}
