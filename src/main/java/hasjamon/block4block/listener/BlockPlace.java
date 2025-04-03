package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.BlockPlaceInClaimEvent;
import hasjamon.block4block.utils.GracePeriod;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Optional;

public class BlockPlace implements Listener {
    private final Block4Block plugin;
    private final HashMap<EntityType, EntityType> netherSpawnTypeTransformations = new HashMap<>();
    private final HashMap<EntityType, EntityType> nonNetherSpawnTypeTransformations = new HashMap<>();

    public BlockPlace(Block4Block plugin) {
        netherSpawnTypeTransformations.put(EntityType.SLIME, EntityType.MAGMA_CUBE);
        nonNetherSpawnTypeTransformations.put(EntityType.MAGMA_CUBE, EntityType.SLIME);
        netherSpawnTypeTransformations.put(EntityType.BREEZE, EntityType.BLAZE);
        nonNetherSpawnTypeTransformations.put(EntityType.BLAZE, EntityType.BREEZE);

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
                boolean isMember = utils.isMemberOfClaim(members, player);
                plugin.pluginManager.callEvent(new BlockPlaceInClaimEvent(player, block, isMember));
                // If the player placing the block isn't a member: Prevent block placement
                if (!isMember) {
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

                spawnTypeOpt.ifPresent(spawnType -> {
                    transformSpawnerType(spawner, spawnType, netherSpawnTypeTransformations);
                    // Mark spawner as player placed
                    NamespacedKey placedKey = new NamespacedKey(plugin, "playerPlaced");
                    spawner.getPersistentDataContainer().set(placedKey, PersistentDataType.BYTE, (byte) 1);
                    spawner.update();
                });
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
                        transformSpawnerType(spawner, spawnType, nonNetherSpawnTypeTransformations);
                        // Mark spawner as player placed
                        NamespacedKey placedKey = new NamespacedKey(plugin, "playerPlaced");
                        spawner.getPersistentDataContainer().set(placedKey, PersistentDataType.BYTE, (byte) 1);
                        spawner.update();
                    }
            }
        }

        switch (event.getBlockReplacedState().getType()) {
            case AIR, CAVE_AIR, VOID_AIR, WATER, LAVA, SHORT_GRASS, TALL_GRASS:
                utils.b4bGracePeriods.put(block, new GracePeriod(System.nanoTime(), block.getType()));
        }

        // Check if the block is inside a claim and if it is adjacent to a claim block
        boolean isInsideClaim = plugin.cfg.getClaimData().contains(utils.getClaimID(block.getLocation()));
        if (isInsideClaim) {
            // Check if the block is next to a claim block in a protected direction
            if (utils.isAdjacentToClaimBlock(block)) {
                String claimID = utils.getClaimID(block.getLocation());  // Get the claim ID based on the block location
                utils.updateBossBar(player, claimID);  // Update the boss bar using the claim ID
            }
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
                boolean isMember = utils.isMemberOfClaim(members, player);
                plugin.pluginManager.callEvent(new BlockPlaceInClaimEvent(player, block, isMember));
                if (!isMember) {
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
        // Additional logic for monitoring bucket emptying can go here
    }

    private boolean isEasilyBreakableCrop(Material material) {
        // Implement logic for crops or blocks that can be easily broken and don't require special rules
        return false; // Just a placeholder for now
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location location = block.getLocation();
        Material signType = block.getType();

        // Check if the sign is in a claimed chunk
        if (plugin.cfg.getClaimData().contains(utils.getClaimID(location))) {
            String[] members = utils.getMembers(location);
            boolean isMember = utils.isMemberOfClaim(members, player);

            // If the player is not a member of the claim
            if (!isMember) {
                Material requiredSign = getSubstitutionFromConfig(signType);

                if (requiredSign == null) {
                    // Handle no valid substitution
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(utils.chat("&cNo valid substitution found for this sign!")));
                    event.setCancelled(true);
                    return;
                }

                // Check if the required sign is in the hotbar or offhand
                int itemSlot = findItemSlot(player, requiredSign);

                if (itemSlot != -1) {
                    // Remove one sign from the correct slot
                    removeItem(player, itemSlot);
                } else {
                    // Cancel modification if the player doesn't have the required sign
                    String message = utils.chat("&cIntruding on Claim &aunclaim the area or spend &c" + requiredSign + " &afrom your hotbar to break this!");
                    event.setCancelled(true);
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                }
            }
        }
    }

    // Get required sign from config (substitute wall signs with regular signs)
    private Material getSubstitutionFromConfig(Material signType) {
        String materialName = signType.name();
        String substituteName = plugin.getConfig().getString("b4b-substitutions." + materialName);

        if (substituteName != null) {
            try {
                return Material.valueOf(substituteName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in b4b-substitutions for: " + materialName);
            }
        }
        return null; // Return null if no valid substitution
    }

    // Find item slot in the hotbar or offhand
    private int findItemSlot(Player player, Material material) {
        // Check the hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == material && item.getAmount() > 0) {
                return i;
            }
        }
        // Check offhand (-2 if found in offhand)
        ItemStack offhandItem = player.getInventory().getItemInOffHand();
        if (offhandItem != null && offhandItem.getType() == material && offhandItem.getAmount() > 0) {
            return -2;
        }
        return -1; // Item not found
    }

    // Remove 1 item from the correct slot
    private void removeItem(Player player, int itemSlot) {
        if (itemSlot == -2) {
            // Remove from offhand
            ItemStack offhandItem = player.getInventory().getItemInOffHand();
            offhandItem.setAmount(offhandItem.getAmount() - 1);
            player.getInventory().setItemInOffHand(offhandItem.getAmount() > 0 ? offhandItem : null);
        } else if (itemSlot >= 0) {
            // Remove from hotbar or main hand
            ItemStack itemToRemove = player.getInventory().getItem(itemSlot);
            itemToRemove.setAmount(itemToRemove.getAmount() - 1);
            player.getInventory().setItem(itemSlot, itemToRemove.getAmount() > 0 ? itemToRemove : null);
        }
    }

}
