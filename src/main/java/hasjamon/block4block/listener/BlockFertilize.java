package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.*;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFertilizeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BlockFertilize implements Listener {

    private final Block4Block plugin;
    private final Random random = new Random();
    private final int MIN_DELAY_TICKS = 5;  // Minimum delay (0.25 seconds)
    private final int MAX_DELAY_TICKS = 20; // Maximum delay (1 second)

    private int MAX_SPREAD_DEPTH_WARPED;   // Depth limit for Warped Nylium
    private int MAX_SPREAD_DEPTH_CRIMSON;  // Depth limit for Crimson Nylium

    private int getRandomDelay() {
        return random.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1) + MIN_DELAY_TICKS;
    }

    // Offsets for all adjacent blocks (cardinal and diagonal)
    private final int[][] OFFSETS = {
            {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1},
            {-1, -1, 0}, {-1, 1, 0}, {1, -1, 0}, {1, 1, 0},
            {-1, 0, -1}, {-1, 0, 1}, {1, 0, -1}, {1, 0, 1},
            {0, -1, -1}, {0, -1, 1}, {0, 1, -1}, {0, 1, 1}
    };

    public BlockFertilize(Block4Block plugin) {
        this.plugin = plugin;
        loadConfigValues();
    }

    // Load config values to set max recursion depth
    private void loadConfigValues() {
        FileConfiguration config = plugin.getConfig();
        MAX_SPREAD_DEPTH_WARPED = config.getInt("max-recursion-depth-warped");
        MAX_SPREAD_DEPTH_CRIMSON = config.getInt("max-recursion-depth-crimson");
        Bukkit.getLogger().info("Max recursion depth (Warped): " + MAX_SPREAD_DEPTH_WARPED);
        Bukkit.getLogger().info("Max recursion depth (Crimson): " + MAX_SPREAD_DEPTH_CRIMSON);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {

        Player player = event.getPlayer();
        Block block = event.getBlock();
        String claimID = utils.getClaimID(block.getLocation());
        String[] members = utils.getMembers(claimID);

        // Claim permission check
        if (claimID != null && members != null && !utils.isMemberOfClaim(members, player)) {
            Bukkit.getLogger().info("Player does not have permission to fertilize here. Cancelling event.");
            event.setCancelled(true);
            return;
        }

        // Check if bonemeal is applied to Netherrack
        if (block.getType() == Material.NETHERRACK) {

            // Determine which nylium type is more common nearby
            Material nyliumType = getDominantNyliumNearby(block);

            // Convert the bonemealed Netherrack to the detected Nylium type
            block.setType(nyliumType);
            spawnDustParticle(block, nyliumType);

            // Begin spreading from this block with depth 0
            scheduleSpreadNylium(block, 0);
        }
    }

    // Retrieve adjacent blocks that are still Netherrack and do not have a blocking block above them
    private List<Block> getNearbyNetherrack(Block block) {
        List<Block> nearby = new ArrayList<>();
        for (int[] offset : OFFSETS) {
            Block relative = block.getRelative(offset[0], offset[1], offset[2]);
            Block blockAbove = relative.getRelative(0, 1, 0);

            if (relative.getType() == Material.NETHERRACK && isNonBlockingAbove(blockAbove)) {
                nearby.add(relative);
            }
        }
        return nearby;
    }

    // Check if the block above is considered non-blocking
    // Check if the block above is considered non-blocking
    private boolean isNonBlockingAbove(Block blockAbove) {
        Material type = blockAbove.getType();

        // If the block is not occluding, it's fine.
        if (!blockAbove.getBlockData().isOccluding()) {
            return true;
        }

        // Check for allowed exceptions based on type name
        if (isAllowedByTypeName(type.toString())) {
            return true;
        }

        // Additional explicit exceptions
        if (isExplicitlyAllowed(type)) {
            return true;
        }

        return false;
    }

    // Check for type name-based exceptions
    private boolean isAllowedByTypeName(String typeName) {
        String[] allowedSuffixes = {"_FENCE", "_GATE", "_WALL", "_PLATE"};
        for (String suffix : allowedSuffixes) {
            if (typeName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    @Deprecated
// Not used currently - Check for explicitly allowed block types
// May be useful if more exceptions need to be handled.
    private boolean isExplicitlyAllowed(Material type) {
        switch (type) {
            case OAK_FENCE:
                return true;
            default:
                return false;
        }
    }

    // Schedule recursive spread with depth check
    private void scheduleSpreadNylium(Block block, int depth) {
        Material nyliumType = block.getType();
        int maxDepth = getMaxDepth(nyliumType);

        if (depth < maxDepth) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> spreadNylium(block, depth + 1), getRandomDelay());
        }
    }

    // Spread nylium with a chance and depth check
    private void spreadNylium(Block block, int depth) {
        Material nyliumType = block.getType();
        double chance = nyliumType == Material.WARPED_NYLIUM ? 0.20 : 0.80;

        List<Block> netherrackBlocks = getNearbyNetherrack(block);

        for (Block netherrack : netherrackBlocks) {
            if (random.nextDouble() < chance) {
                netherrack.setType(nyliumType);
                spawnDustParticle(netherrack, nyliumType);
                scheduleSpreadNylium(netherrack, depth);
            }
        }
    }

    // Get the appropriate max depth for the current nylium type
    private int getMaxDepth(Material nyliumType) {
        if (nyliumType == Material.WARPED_NYLIUM) {
            return MAX_SPREAD_DEPTH_WARPED;
        } else if (nyliumType == Material.CRIMSON_NYLIUM) {
            return MAX_SPREAD_DEPTH_CRIMSON;
        }
        return 0; // Default to 0 if an invalid type is encountered
    }

    // Spawn particle effects to visually represent the spread
    private void spawnDustParticle(Block block, Material nyliumType) {
        // Define colors for Warped Nylium (blue and green) and Crimson Nylium (red)
        Color[] warpedColors = {Color.BLUE, Color.LIME}; // Warped Nylium particles (blue and green mix)
        Color crimsonColor = Color.RED; // Crimson Nylium particles

        // The central location just above the block
        Location center = block.getLocation().add(0.5, 1.0, 0.5);
        World world = block.getWorld();

        // Spawn a cloud of particles with random sizes and movement
        int particleCount = 20; // Number of particles in the burst
        double offsetX = 0.5;
        double offsetY = 0.5;
        double offsetZ = 0.5;

        // Spawn a burst of particles with smaller, varying sizes
        for (int i = 0; i < particleCount; i++) {
            float particleSize = 0.3f + (random.nextFloat() * 0.5f); // Size range: 0.3 - 0.8

            // Alternate between blue and green for Warped Nylium, otherwise use red
            Color particleColor;
            if (nyliumType == Material.WARPED_NYLIUM) {
                particleColor = warpedColors[random.nextInt(warpedColors.length)];
            } else {
                particleColor = crimsonColor;
            }

            DustOptions dustOptions = new DustOptions(particleColor, particleSize);

            // Randomize position and slight velocity
            double randomX = (random.nextDouble() - 0.5) * offsetX * 2;
            double randomY = random.nextDouble() * offsetY * 0.7; // Slightly reduce height spread
            double randomZ = (random.nextDouble() - 0.5) * offsetZ * 2;

            // Spawn the particle with the random position and size
            world.spawnParticle(Particle.DUST, center.clone().add(randomX, randomY, randomZ), 1, 0, 0, 0, 0, dustOptions);
        }

        // Optionally, add a few slightly larger, slower "drifting" particles to give depth
        for (int i = 0; i < 5; i++) {
            float largeParticleSize = 0.6f + (random.nextFloat() * 0.5f); // Size range: 0.6 - 1.1

            // Use the same alternate color logic for the larger particles
            Color largeParticleColor;
            if (nyliumType == Material.WARPED_NYLIUM) {
                largeParticleColor = warpedColors[random.nextInt(warpedColors.length)];
            } else {
                largeParticleColor = crimsonColor;
            }

            DustOptions largeDustOptions = new DustOptions(largeParticleColor, largeParticleSize);

            double driftX = (random.nextDouble() - 0.5) * 0.2;
            double driftY = random.nextDouble() * 0.2;
            double driftZ = (random.nextDouble() - 0.5) * 0.2;

            world.spawnParticle(Particle.DUST, center.clone().add(driftX, driftY, driftZ), 1, 0, 0, 0, 0, largeDustOptions);
        }
    }

    private Material getDominantNyliumNearby(Block block) {
        int warpedCount = 0;
        int crimsonCount = 0;

        for (int[] offset : OFFSETS) {
            Block relative = block.getRelative(offset[0], offset[1], offset[2]);
            if (relative.getType() == Material.WARPED_NYLIUM) {
                warpedCount++;
            } else if (relative.getType() == Material.CRIMSON_NYLIUM) {
                crimsonCount++;
            }
        }

        // Choose the most common nylium type nearby
        if (crimsonCount > warpedCount) {
            return Material.CRIMSON_NYLIUM;
        } else if (warpedCount > crimsonCount) {
            return Material.WARPED_NYLIUM;
        } else {
            // 50/50 chance if counts are equal
            return random.nextBoolean() ? Material.WARPED_NYLIUM : Material.CRIMSON_NYLIUM;
        }
    }
}
