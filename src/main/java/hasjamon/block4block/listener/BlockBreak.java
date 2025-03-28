package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.B4BlockBreakWithinGracePeriodEvent;
import hasjamon.block4block.events.BlockBreakInClaimEvent;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class BlockBreak implements Listener {
    private final Block4Block plugin;
    private long andesiteLatestBreak = 0;

    public BlockBreak(Block4Block plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        FileConfiguration cfg = plugin.getConfig();
        boolean isInsideClaim = plugin.cfg.getClaimData().contains(utils.getClaimID(b.getLocation()));

        // Prevent breaking chests in someone else's claim
        if (isInsideClaim && b.getType() == Material.CHEST) {
            String[] members = utils.getMembers(b.getLocation());
            boolean isMember = utils.isMemberOfClaim(members, p);
            boolean canOpen = utils.canOpenChest(b, p);

            if (!isMember && !canOpen) {
                e.setCancelled(true);
                String message = utils.chat("&cThe chest is shielded by the blocks above it.");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                return;
            }
        }

        // Lecterns are exempt from B4B rules
        if (b.getType() == Material.LECTERN || p.getGameMode() == GameMode.CREATIVE) return;

        List<?> claimBlacklist = cfg.getList("blacklisted-claim-blocks");
        if (isInsideClaim) {
            if (utils.isAdjacentToClaimBlock(b)) {
                String claimID = utils.getClaimID(b.getLocation());
                utils.updateBossBar(p, claimID);
            }

            if (!utils.isClaimBlock(b)) {
                String[] members = utils.getMembers(b.getLocation());
                if (members != null) {
                    if (utils.isMemberOfClaim(members, p)) {
                        if (cfg.getBoolean("only-apply-b4b-to-intruders") ||
                                (claimBlacklist != null && claimBlacklist.contains(b.getType().toString()))) {
                            plugin.pluginManager.callEvent(new BlockBreakInClaimEvent(p, b, true));
                            return;
                        }
                    } else if (!cfg.getBoolean("can-break-in-others-claims")) {
                        e.setCancelled(true);
                        p.sendMessage(utils.chat("&cYou cannot break blocks in this claim"));
                        plugin.pluginManager.callEvent(new BlockBreakInClaimEvent(p, b, false));
                        return;
                    }
                }
            }
        }

        if (plugin.getConfig().getBoolean("andesite-splash-on") && b.getType() == Material.ANDESITE) {
            if (System.nanoTime() - andesiteLatestBreak > 1E8) {
                andesiteLatestBreak = System.nanoTime();
                for (int x = -1; x <= 1; x++)
                    for (int y = -1; y <= 1; y++)
                        for (int z = -1; z <= 1; z++)
                            if (!(x == 0 && y == 0 && z == 0))
                                if (b.getRelative(x, y, z).getType() == Material.ANDESITE)
                                    if (plugin.getConfig().getBoolean("andesite-splash-reduce-durability"))
                                        p.breakBlock(b.getRelative(x, y, z));
                                    else
                                        b.getRelative(x, y, z).breakNaturally(p.getInventory().getItemInMainHand());
            }
            return;
        }

        if (!isInsideClaim && plugin.getConfig().getBoolean("only-apply-b4b-to-intruders")) return;

        List<?> blacklistedBlocks = cfg.getList("blacklisted-blocks");
        List<?> lootDisabledTypes = cfg.getList("no-loot-on-break");

        if (blacklistedBlocks != null && lootDisabledTypes != null) {
            boolean requiresBlock = !blacklistedBlocks.contains(b.getType().toString());
            boolean isFreeToBreakInClaim = claimBlacklist.contains(b.getType().toString());

            utils.removeExpiredB4BGracePeriods();
            if (utils.b4bGracePeriods.containsKey(b)) {
                plugin.pluginManager.callEvent(new B4BlockBreakWithinGracePeriodEvent(p, b, requiresBlock));
                return;
            }
            utils.b4bCheck(p, b, e, lootDisabledTypes, requiresBlock, isFreeToBreakInClaim);
        }

        // Ignore lecterns and creative mode
        if (b.getType() == Material.LECTERN || p.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Apply lore to natural block drops
        applyLoreToBlockDrops(b, p, e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();

        if (b.getType() == Material.SPAWNER) {
            CreatureSpawner spawner = (CreatureSpawner) b.getState();
            EntityType spawnType = spawner.getSpawnedType();
            String expectedSpawnerType = spawnType.name();
            boolean isInsideClaim = plugin.cfg.getClaimData().contains(utils.getClaimID(b.getLocation()));

            // Check if breaking inside a claim
            if (isInsideClaim) {
                String[] members = utils.getMembers(b.getLocation());
                boolean isMember = utils.isMemberOfClaim(members, p);

                // If not a member, check for valid spawner item
                if (!isMember && !hasValidSpawnerItem(p, expectedSpawnerType)) {
                    e.setCancelled(true);
                    String message = utils.chat("&aSpend &c" + expectedSpawnerType + "_SPAWNER &afrom your hotbar to break this!");
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                    return;
                }

                // Deduct the spawner item if valid
                deductSpawnerItem(p, expectedSpawnerType);
            }

            // Drop spawner with metadata and lore
            dropSpawnerWithMetadata(b, spawnType);
        }
    }

    // ----- Apply Lore to Block Drops -----
    private void applyLoreToBlockDrops(Block b, Player p, BlockBreakEvent e) {
        // Cancel the default drop to prevent double drops
        e.setDropItems(false);
        String loreLabel = getBlockMarkerLabel(p, b);

        // Check if the block is a container (chest, barrel, etc.)
        BlockState state = b.getState();
        if (state instanceof Container) {
            Container container = (Container) state;

            // Iterate over the container's inventory and drop items with lore
            for (ItemStack drop : container.getInventory().getContents()) {
                if (drop != null && drop.getType() != Material.AIR) {
                    cleanLore(drop);
                    applyLoreMark(drop, loreLabel);
                    b.getWorld().dropItemNaturally(b.getLocation(), drop);
                }
            }

            // Clear container inventory to avoid duplicate drops
            container.getInventory().clear();
            return; // Skip further processing since we handled container drops
        }

        // Handle normal block drops for non-container blocks
        Collection<ItemStack> drops = b.getDrops(p.getInventory().getItemInMainHand());

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR) {
                continue; // Skip invalid drops
            }

            // Clean old lore and apply new lore
            cleanLore(drop);
            applyLoreMark(drop, loreLabel);

            // Drop the modified item naturally
            b.getWorld().dropItemNaturally(b.getLocation(), drop);
        }
    }



    // ----- Drop Correct Item with Metadata -----
    private void dropItemNaturally(Location loc, ItemStack item) {
        if (loc == null || item == null || item.getType() == Material.AIR) {
            return;
        }
        loc.getWorld().dropItemNaturally(loc, item);
    }

    // ----- Clean Lore from Item -----
    private void cleanLore(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        List<String> lore = meta.getLore();
        if (lore == null) return;

        lore.removeIf(line -> {
            String clean = normalizeLore(line);
            return clean.equals("free to break") ||
                    clean.equals("free in claim") ||
                    clean.equals("block for block") ||
                    clean.equals("f2b") ||
                    clean.equals("finc") ||
                    clean.equals("b4b");
        });

        meta.setLore(lore);
        stack.setItemMeta(meta);
    }


    // ----- Apply Lore Label to Item -----
    private void applyLoreMark(ItemStack stack, String label) {
        if (stack == null || !isPlaceable(stack)) return;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        String normalizedLabel = "§7" + label;

        for (String line : lore) {
            if (normalizeLore(line).equals(normalizeLore(label))) {
                return;
            }
        }

        lore.add(normalizedLabel);
        meta.setLore(lore);
        stack.setItemMeta(meta);
    }

    // ----- Normalize Lore String -----
    private String normalizeLore(String loreLine) {
        return loreLine.replace("§7", "").trim().toLowerCase();
    }


    // ----- Determine Lore Label for Block -----
    private String getBlockMarkerLabel(Player p, Block b) {
        FileConfiguration cfg = plugin.getConfig();
        boolean useLong = cfg.getBoolean("use-long-form");
        List<?> freeInClaim = cfg.getList("blacklisted-claim-blocks");
        List<?> freeBlocks = cfg.getList("blacklisted-blocks");
        boolean isInClaim = plugin.cfg.getClaimData().contains(utils.getClaimID(b.getLocation()));

        if (isInClaim && freeInClaim != null && freeInClaim.contains(b.getType().toString())) {
            return useLong ? "Free in Claim" : "FINC";
        } else if (freeBlocks != null && freeBlocks.contains(b.getType().toString())) {
            return useLong ? "Free to Break" : "F2B";
        } else {
            return useLong ? "Block for Block" : "B4B";
        }
    }


    // ----- Drop Spawner with Metadata and Lore -----
    private void dropSpawnerWithMetadata(Block b, EntityType spawnType) {
        ItemStack spawnerItem = new ItemStack(Material.SPAWNER);
        ItemMeta itemMeta = spawnerItem.getItemMeta();

        if (itemMeta != null) {
            itemMeta.setDisplayName(utils.prettifyEnumName(spawnType) + " Spawner");
            itemMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING, spawnType.name());
            spawnerItem.setItemMeta(itemMeta);

            // Drop the spawner with metadata
            b.getWorld().dropItemNaturally(b.getLocation(), spawnerItem);
        }
    }

    // ----- Check if Item is Placeable -----
    private boolean isPlaceable(ItemStack stack) {
        if (stack == null) return false;
        Material mat = stack.getType();
        return mat.isBlock() ||
                mat == Material.PAINTING ||
                mat == Material.ITEM_FRAME ||
                mat == Material.GLOW_ITEM_FRAME;
    }

    // ----- Check for Valid Spawner Item -----
    private boolean hasValidSpawnerItem(Player p, String expectedSpawnerType) {
        // Check main hand
        if (isValidSpawnerItem(p.getInventory().getItemInMainHand(), expectedSpawnerType)) {
            return true;
        }

        // Check offhand
        if (isValidSpawnerItem(p.getInventory().getItemInOffHand(), expectedSpawnerType)) {
            return true;
        }

        // Check hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (isValidSpawnerItem(item, expectedSpawnerType)) {
                return true;
            }
        }

        return false;
    }

    // ----- Deduct Correct Spawner Item -----
    private void deductSpawnerItem(Player p, String expectedSpawnerType) {
        ItemStack mainHand = p.getInventory().getItemInMainHand();
        if (isValidSpawnerItem(mainHand, expectedSpawnerType)) {
            decrementItem(mainHand, p.getInventory().getHeldItemSlot(), p);
            return;
        }

        ItemStack offHand = p.getInventory().getItemInOffHand();
        if (isValidSpawnerItem(offHand, expectedSpawnerType)) {
            decrementItem(offHand, -2, p);
            return;
        }

        // Check and deduct from hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (isValidSpawnerItem(item, expectedSpawnerType)) {
                decrementItem(item, i, p);
                return;
            }
        }
    }

    // ----- Check if Item is a Valid Spawner -----
    private boolean isValidSpawnerItem(ItemStack item, String expectedSpawnerType) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING)) {
                String storedType = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING);
                return storedType != null && storedType.equals(expectedSpawnerType);
            }
        }
        return false;
    }

    // ----- Decrease Item Amount or Remove -----
    private void decrementItem(ItemStack item, int slot, Player p) {
        item.setAmount(item.getAmount() - 1);
        if (item.getAmount() <= 0) {
            if (slot == -2) {
                p.getInventory().setItemInOffHand(null);
            } else {
                p.getInventory().setItem(slot, null);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        String claimID = utils.getClaimID(b.getLocation());

        if (utils.isAir(b.getType())) return;

        if (plugin.cfg.getClaimData().contains(claimID)) {
            String[] members = utils.getMembers(b.getLocation());

            if (members != null && !utils.isMemberOfClaim(members, p)) {
                p.sendMessage(utils.chat("&cYou cannot fill buckets in this claim"));
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        updateInventoryMarks(p.getInventory());
        plugin.getLogger().info("DEBUG: Updated inventory marks for player " + p.getName() + " on login.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        Inventory inv = e.getInventory();
        if (inv != null) {
            updateInventoryMarks(inv);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getCurrentItem() != null && isPlaceable(e.getCurrentItem())) {
            updateItemMarker(e.getCurrentItem());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(PlayerPickupItemEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem().getItemStack();

        if (item != null && isPlaceable(item)) {
            updateItemMarker(item);
        }
    }

    private void removeMarkersFromItem(ItemStack stack) {
        if (stack == null || !isPlaceable(stack)) return;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        List<String> lore = meta.getLore();
        if (lore == null) return;

        Iterator<String> iter = lore.iterator();
        while (iter.hasNext()) {
            String line = iter.next();
            String clean = line.replace("§7", "").trim();
            if (clean.equalsIgnoreCase("Free to Break") ||
                    clean.equalsIgnoreCase("Free in Claim") ||
                    clean.equalsIgnoreCase("Block for Block") ||
                    clean.equalsIgnoreCase("F2B") ||
                    clean.equalsIgnoreCase("FINC") ||
                    clean.equalsIgnoreCase("B4B")) {
                iter.remove();
                plugin.getLogger().info("DEBUG: Removed lore marker from " + stack.getType());
            }
        }

        if (lore.isEmpty()) {
            meta.setLore(null); // Clear lore if empty
        } else {
            meta.setLore(lore);
        }
        stack.setItemMeta(meta);
    }

    private String getInventoryMarkerLabel(ItemStack stack) {
        if (!isPlaceable(stack)) {
            return "SKIP-LABEL"; // Skip if the item is not placeable
        }

        FileConfiguration cfg = plugin.getConfig();
        boolean useLong = cfg.getBoolean("use-long-form");
        List<?> freeInClaim = cfg.getList("blacklisted-claim-blocks");
        List<?> freeBlocks = cfg.getList("blacklisted-blocks");

        // Check if the item is in the blacklisted-claim-blocks list
        if (freeInClaim != null && freeInClaim.contains(stack.getType().toString())) {
            return useLong ? "Free in Claim" : "FINC";
        }
        // Check if the item is in the blacklisted-blocks list
        else if (freeBlocks != null && freeBlocks.contains(stack.getType().toString())) {
            return useLong ? "Free to Break" : "F2B";
        }
        // Default to Block for Block
        return useLong ? "Block for Block" : "B4B";
    }


    private void updateItemMarker(ItemStack stack) {
        if (stack == null) return;
        if (!isPlaceable(stack)) {
            plugin.getLogger().info("DEBUG: Skipping " + stack.getType() + " in updateItemMarker as it's not considered placeable.");
            return;
        }
        String method = plugin.getConfig().getString("marking-method");
        if (method.equals("none")) {
            removeMarkersFromItem(stack);
            return;
        }
        String expectedLabel = getInventoryMarkerLabel(stack);
        if (expectedLabel.equals("SKIP-LABEL")) {
            plugin.getLogger().info("DEBUG: getInventoryMarkerLabel returned 'SKIP-LABEL' for " + stack.getType());
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        Iterator<String> iter = lore.iterator();
        while (iter.hasNext()) {
            String line = iter.next();
            String clean = line.replace("§7", "").trim();
            if (clean.equalsIgnoreCase("Free to Break") ||
                    clean.equalsIgnoreCase("Free in Claim") ||
                    clean.equalsIgnoreCase("Block for Block") ||
                    clean.equalsIgnoreCase("F2B") ||
                    clean.equalsIgnoreCase("FINC") ||
                    clean.equalsIgnoreCase("B4B")) {
                iter.remove();
            }
        }
        lore.add("§7" + expectedLabel);
        meta.setLore(lore);
        stack.setItemMeta(meta);
    }

    public void updateInventoryMarks(Inventory inv) {
        if (inv == null) return;
        for (ItemStack item : inv.getContents()) {
            if (item != null) {
                updateItemMarker(item);
            }
        }
        plugin.getLogger().info("DEBUG: Updated inventory marks.");
    }

    // ----- Helper method to check if a player can open a chest -----
    private boolean canOpenChest(Block chestBlock, Player player) {
        Location aboveLocation = chestBlock.getLocation().add(0, 1, 0);
        Block aboveBlock = aboveLocation.getBlock();
        return aboveBlock.getType() != Material.AIR;
    }
}