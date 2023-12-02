package hasjamon.block4block.listener;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.BlockPlaceInClaimEvent;
import hasjamon.block4block.utils.GracePeriod;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

public class BlockPlace implements Listener {
    private final Block4Block plugin;
    private final HashMap<EntityType, EntityType> netherSpawnTypeTransformations = Maps.newHashMap();
    private final HashMap<EntityType, EntityType> nonNetherSpawnTypeTransformations = Maps.newHashMap();
    private final HashSet<EntityType> disallowedSpawnersOutsideNether = Sets.newHashSet(
            EntityType.BLAZE
    );

    public BlockPlace(Block4Block plugin) {
        netherSpawnTypeTransformations.put(EntityType.SLIME, EntityType.MAGMA_CUBE);
        nonNetherSpawnTypeTransformations.put(EntityType.MAGMA_CUBE, EntityType.SLIME);

        this.plugin = plugin;
    }

    // Prevent blocks from being placed in someone else's claim
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        Material blockType = block.getType();
        ItemMeta itemMeta = event.getItemInHand().getItemMeta();

        if (player.getGameMode() == GameMode.CREATIVE) return;

        // If the block was placed in a claimed chunk
        if (plugin.cfg.getClaimData().contains(utils.getClaimID(event.getBlockPlaced().getLocation()))) {
            String[] members = utils.getMembers(block.getLocation());

            // If the block isn't the lectern claiming the chunk and the player isn't placing an easily breakable crop
            if (!utils.isClaimBlock(block) && !isEasilyBreakableCrop(block.getType())) {
                // If the player placing the block isn't a member: Prevent block placement
                if (!utils.isMemberOfClaim(members, player)) {
                    plugin.pluginManager.callEvent(new BlockPlaceInClaimEvent(player, block, false));
                    event.setCancelled(true);
                    player.sendMessage(utils.chat("&cYou cannot place blocks in this claim"));
                    return;
                }
            }
        }

        if (block.getWorld().getEnvironment() == World.Environment.NETHER) {
            if (blockType == Material.SPAWNER) {
                CreatureSpawner spawner = (CreatureSpawner) block.getState();
                Optional<EntityType> spawnTypeOpt = getSpawnerType(itemMeta);

                spawnTypeOpt.ifPresent(spawnType -> transformSpawnerType(spawner, spawnType, netherSpawnTypeTransformations));
            }
        } else {
            switch (blockType) {
                case CRIMSON_NYLIUM, WARPED_NYLIUM:
                    event.setCancelled(true);
                    player.sendMessage(utils.chat("&cYou cannot place " + blockType.name() + " outside the Nether"));
                    return;

                case SPAWNER:
                    CreatureSpawner spawner = (CreatureSpawner) block.getState();
                    Optional<EntityType> spawnTypeOpt = getSpawnerType(itemMeta);

                    if (spawnTypeOpt.isPresent()) {
                        EntityType spawnType = spawnTypeOpt.get();

                        if (disallowedSpawnersOutsideNether.contains(spawnType)) {
                            event.setCancelled(true);
                            player.sendMessage(utils.chat("&cYou cannot place " + utils.prettifyEnumName(spawnType) + " Spawner outside the Nether"));
                            return;
                        } else {
                            transformSpawnerType(spawner, spawnType, nonNetherSpawnTypeTransformations);
                        }
                    }
            }
        }

        switch (event.getBlockReplacedState().getType()) {
            case AIR, CAVE_AIR, VOID_AIR, WATER, LAVA, GRASS, TALL_GRASS:
                utils.b4bGracePeriods.put(block, new GracePeriod(System.nanoTime(), block.getType()));
        }
    }

    private void transformSpawnerType(CreatureSpawner spawner, EntityType spawnType, HashMap<EntityType, EntityType> transforms) {
        spawner.setSpawnedType(transforms.getOrDefault(spawnType, spawnType));
        spawner.update();
    }

    private Optional<EntityType> getSpawnerType(ItemMeta itemMeta) {
        if (itemMeta != null) {
            PersistentDataContainer container = itemMeta.getPersistentDataContainer();
            if (container.has(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING)) {
                String spawnTypeName = container.get(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING);
                return Optional.of(EntityType.valueOf(spawnTypeName));
            }
        }

        return Optional.empty();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        String claimID = utils.getClaimID(block.getLocation());

        if (plugin.cfg.getClaimData().contains(claimID)) {
            String[] members = utils.getMembers(block.getLocation());

            if (members != null) {
                if (!utils.isMemberOfClaim(members, player)) {
                    plugin.pluginManager.callEvent(new BlockPlaceInClaimEvent(player, block, false));
                    event.setCancelled(true);
                    player.sendMessage(utils.chat("&cYou cannot empty buckets in this claim"));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmptyMonitor(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        Material bucket = event.getBucket();
        Player player = event.getPlayer();
        int blockY = block.getLocation().getBlockY();
        World.Environment dimension = block.getWorld().getEnvironment();

        if (bucket == Material.LAVA_BUCKET && !utils.willLavaFlowAt(blockY, dimension)) {
            String msg = utils.chat("Lava stops flowing at heights above " + utils.lavaFlowMaxY);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
        }
    }

    private boolean isEasilyBreakableCrop(Material blockType) {
        switch (blockType) {
            case WHEAT, POTATOES, CARROTS, BEETROOTS, SUGAR_CANE, COCOA, NETHER_WART -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}

