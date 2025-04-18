package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.B4BlockBreakWithinGracePeriodEvent;
import hasjamon.block4block.events.BlockBreakInClaimEvent;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.potion.PotionType;

import java.util.List;

import static hasjamon.block4block.utils.utils.canOpenChest;

public class BlockBreak implements Listener {
    private final Block4Block plugin;
    private long andesiteLatestBreak = 0;

    public BlockBreak(Block4Block plugin) {
        this.plugin = plugin;
    }

    // This Class is for the block break event (This runs every time a player breaks a block)
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

            // Check if the chest can be opened by the player
            boolean canOpen = canOpenChest(b, p);

            // If the player is not a member and cannot open the chest
            if (!isMember && !canOpen) {
                e.setCancelled(true);
                String message = utils.chat("&cThe chest is shielded by the blocks above it.");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                return;
            }
        }

        // Special handling for double chests
        if (b.getType() == Material.CHEST) {
            Chest chestState = (Chest) b.getState();

            // Check if it's a double chest
            if (chestState.getInventory().getHolder() instanceof DoubleChest) {
                DoubleChest doubleChest = (DoubleChest) chestState.getInventory().getHolder();
                Chest leftSide = (Chest) doubleChest.getLeftSide();
                Chest rightSide = (Chest) doubleChest.getRightSide();

                // Determine which side is being broken
                boolean isLeftSide = leftSide.getLocation().equals(b.getLocation());

                // Check if player is in their own claim
                boolean inOwnClaim = false;
                if (isInsideClaim) {
                    String[] members = utils.getMembers(b.getLocation());
                    inOwnClaim = utils.isMemberOfClaim(members, p);
                }

                // Only require a chest if not in own claim
                if (!inOwnClaim) {
                    // Check if player has a chest in inventory
                    boolean hasChest = false;

                    // Check offhand first
                    if (p.getInventory().getItemInOffHand().getType() == Material.CHEST) {
                        p.getInventory().getItemInOffHand().setAmount(p.getInventory().getItemInOffHand().getAmount() - 1);
                        hasChest = true;
                    } else {
                        // Check hotbar (slots 0-8)
                        for (int i = 0; i < 9; i++) {
                            ItemStack item = p.getInventory().getItem(i);
                            if (item != null && item.getType() == Material.CHEST) {
                                item.setAmount(item.getAmount() - 1);
                                hasChest = true;
                                break;
                            }
                        }
                    }

                    // If player doesn't have a chest, prevent breaking
                    if (!hasChest) {
                        e.setCancelled(true);
                        String message = utils.chat("&aYou need a &cCHEST &ain your hotbar to break this!");
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                        return;
                    }
                }

                // Cancel the event to handle drops manually
                e.setCancelled(true);

                // Get the chest content - simplify by extracting only the relevant half
                ItemStack[] allContents = chestState.getInventory().getContents();
                int startSlot = isLeftSide ? 0 : 27;
                int endSlot = isLeftSide ? 26 : 53;

                // Drop only the contents of the broken side
                for (int i = startSlot; i <= endSlot; i++) {
                    if (i < allContents.length && allContents[i] != null && allContents[i].getType() != Material.AIR) {
                        b.getWorld().dropItemNaturally(b.getLocation(), allContents[i]);
                        chestState.getInventory().setItem(i, null); // Clear this slot
                    }
                }

                // Break the chest block (without dropping the chest itself)
                b.setType(Material.AIR);

                // Drop a chest if in own claim (free breaking)
                if (inOwnClaim) {
                    b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.CHEST, 1));
                }

                return; // Skip the rest of the onBreak logic
            }
        }

        // Lecterns are exempt from B4B rules. Changing this would require refactoring of LecternBreak's onBreak.
        if (b.getType() == Material.LECTERN) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;

        List<?> claimBlacklist = cfg.getList("blacklisted-claim-blocks");

        if (isInsideClaim) {
            // Check if the block is next to a claim block in a protected direction
            if (utils.isAdjacentToClaimBlock(b)) {
                String claimID = utils.getClaimID(b.getLocation());  // Get the claim ID based on the block location
                utils.updateBossBar(p, claimID);  // Update the boss bar using the claim ID
            }

            if (!utils.isClaimBlock(b)) {
                String[] members = utils.getMembers(b.getLocation());

                if (members != null) {
                    // If the player is a member of the claim
                    if (utils.isMemberOfClaim(members, p)) {
                        // If B4B should only apply to intruders: Don't apply B4B rules
                        if (plugin.getConfig().getBoolean("only-apply-b4b-to-intruders")) {
                            plugin.pluginManager.callEvent(new BlockBreakInClaimEvent(p, b, true));
                            return;
                        }

                        // If the block is claim-blacklisted: Don't apply B4B rules
                        if (claimBlacklist != null && claimBlacklist.contains(b.getType().toString())) {
                            plugin.pluginManager.callEvent(new BlockBreakInClaimEvent(p, b, true));
                            return;
                        }
                    } else if (!cfg.getBoolean("can-break-in-others-claims")) {
                        // Prevent non-members from breaking blocks
                        e.setCancelled(true);
                        p.sendMessage(utils.chat("&cYou cannot break blocks in this claim"));
                        plugin.pluginManager.callEvent(new BlockBreakInClaimEvent(p, b, false));
                        return;
                    }
                }
            }
        }

        if (plugin.getConfig().getBoolean("andesite-splash-on")) {
            if (b.getType() == Material.ANDESITE) {
                // Add splash if it's been at least 0.1 second since the last time andesite was broken (to avoid chain reaction)
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
        }

        // If the block isn't inside a claim and B4B should only apply to intruders: Do not apply B4B
        if (!isInsideClaim && plugin.getConfig().getBoolean("only-apply-b4b-to-intruders")) {
            return;
        }

        List<?> blacklistedBlocks = cfg.getList("blacklisted-blocks");
        List<?> lootDisabledTypes = cfg.getList("no-loot-on-break");

        if (blacklistedBlocks != null && lootDisabledTypes != null) {
            // Does Block4Block apply, i.e., has the block type not been exempted from Block4Block through the blacklist
            boolean requiresBlock = !blacklistedBlocks.contains(b.getType().toString());
            boolean isFreeToBreakInClaim = claimBlacklist != null && claimBlacklist.contains(b.getType().toString());

            utils.removeExpiredB4BGracePeriods();

            // If the block is still covered by the grace period, do not apply B4B rules
            if (utils.b4bGracePeriods.containsKey(b)) {
                plugin.pluginManager.callEvent(new B4BlockBreakWithinGracePeriodEvent(p, b, requiresBlock));
                return;
            }

            utils.b4bCheck(p, b, e, lootDisabledTypes, requiresBlock, isFreeToBreakInClaim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();

        if (b.getType() == Material.SPAWNER) {
            CreatureSpawner spawner = (CreatureSpawner) b.getState();
            NamespacedKey placedKey = new NamespacedKey(plugin, "playerPlaced");
            if (spawner.getPersistentDataContainer().has(placedKey, PersistentDataType.BYTE)) {
                // If it was placed by a player, do not drop experience.
                e.setExpToDrop(0);
            }
            EntityType spawnType = spawner.getSpawnedType();
            if (spawnType == null) {
                // Skip if spawnType is null to prevent NullPointerException
                return;
            }
            String expectedSpawnerType = spawnType.name();
            ItemStack spawnerItem = new ItemStack(Material.SPAWNER);
            ItemMeta itemMeta = spawnerItem.getItemMeta();
            if (spawner.getPersistentDataContainer().has(placedKey, PersistentDataType.BYTE)) {
                // Check if the spawner's custom data is correctly set
                Byte isPlayerPlaced = spawner.getPersistentDataContainer().get(placedKey, PersistentDataType.BYTE);
                if (isPlayerPlaced == null || isPlayerPlaced != 1) {
                    return; // Skip invalid spawners
                }
            }

            // Check if the spawner is inside a claim
            boolean isInsideClaim = plugin.cfg.getClaimData().contains(utils.getClaimID(b.getLocation()));

            if (isInsideClaim) {
                String[] members = utils.getMembers(b.getLocation());
                boolean isMember = utils.isMemberOfClaim(members, p);

                // Prevent breaking if player is not a member of the claim
                if (!isMember) {
                    boolean isCorrectItem = false;
                    int itemSlot = -1;

                    // Check item in main hand
                    if (p.getInventory().getItemInMainHand().hasItemMeta()) {
                        ItemMeta mainHandMeta = p.getInventory().getItemInMainHand().getItemMeta();
                        if (mainHandMeta != null && mainHandMeta.getPersistentDataContainer().has(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING)) {
                            String storedType = mainHandMeta.getPersistentDataContainer().get(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING);
                            if (storedType != null && storedType.equals(expectedSpawnerType)) {
                                isCorrectItem = true;
                                itemSlot = p.getInventory().getHeldItemSlot(); // Main hand slot
                            }
                        }
                    }

                    // Check item in offhand
                    if (!isCorrectItem && p.getInventory().getItemInOffHand().hasItemMeta()) {
                        ItemMeta offHandMeta = p.getInventory().getItemInOffHand().getItemMeta();
                        if (offHandMeta != null && offHandMeta.getPersistentDataContainer().has(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING)) {
                            String storedType = offHandMeta.getPersistentDataContainer().get(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING);
                            if (storedType != null && storedType.equals(expectedSpawnerType)) {
                                isCorrectItem = true;
                                itemSlot = -2; // Special case for offhand
                            }
                        }
                    }

                    // Check item in hotbar
                    if (!isCorrectItem) {
                        for (int i = 0; i < 9; i++) {
                            ItemStack item = p.getInventory().getItem(i);
                            if (item != null && item.hasItemMeta()) {
                                ItemMeta hotbarMeta = item.getItemMeta();
                                if (hotbarMeta != null && hotbarMeta.getPersistentDataContainer().has(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING)) {
                                    String storedType = hotbarMeta.getPersistentDataContainer().get(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING);
                                    if (storedType != null && storedType.equals(expectedSpawnerType)) {
                                        isCorrectItem = true;
                                        itemSlot = i; // Store hotbar slot
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    // Cancel breaking if none of the items match the expected spawner name
                    if (!isCorrectItem) {
                        e.setCancelled(true);
                        String message = utils.chat("&aSpend &c" + expectedSpawnerType + "_SPAWNER &afrom your hotbar to break this!");
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                        return;
                    }

                    // Remove 1 item from the appropriate slot if correct item was found
                    if (itemSlot == -2) {
                        // Remove from offhand
                        ItemStack offhandItem = p.getInventory().getItemInOffHand();
                        offhandItem.setAmount(offhandItem.getAmount() - 1);
                        p.getInventory().setItemInOffHand(offhandItem.getAmount() > 0 ? offhandItem : null);
                    } else if (itemSlot >= 0) {
                        // Remove from hotbar or main hand
                        ItemStack itemToRemove = p.getInventory().getItem(itemSlot);
                        itemToRemove.setAmount(itemToRemove.getAmount() - 1);
                        p.getInventory().setItem(itemSlot, itemToRemove.getAmount() > 0 ? itemToRemove : null);
                    }
                } else if (itemMeta != null) {
                    itemMeta.setDisplayName(utils.prettifyEnumName(spawnType) + " Spawner");
                    itemMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP); // Hides the "Interact with spawn egg..." text
                    itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING, spawnType.name());
                    spawnerItem.setItemMeta(itemMeta);
                    b.getWorld().dropItemNaturally(b.getLocation(), spawnerItem);
                }
            } else if (itemMeta != null && spawnType != null) {
                itemMeta.setDisplayName(utils.prettifyEnumName(spawnType) + " Spawner");
                itemMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP); // Hides the "Interact with spawn egg..." text
                itemMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "spawnType"), PersistentDataType.STRING, spawnType.name());
                spawnerItem.setItemMeta(itemMeta);
                b.getWorld().dropItemNaturally(b.getLocation(), spawnerItem);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        String claimID = utils.getClaimID(b.getLocation());

        // Allow milking
        if (utils.isAir(b.getType()))
            return;

        // Disallow filling buckets with anything other than milk
        if (plugin.cfg.getClaimData().contains(claimID)) {
            String[] members = utils.getMembers(b.getLocation());

            if (members != null) {
                if (utils.isMemberOfClaim(members, p))
                    return;

                String message = utils.chat("&cYou cannot fill buckets in others claim");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                e.setCancelled(true);
            }
        }
    }

    // For preventing dirt to mud conversion with water bottles
    @EventHandler(ignoreCancelled = true)
    public void onInteractWithWaterBottle(PlayerInteractEvent e) {
        // Check if player is right-clicking a block
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }

        Player p = e.getPlayer();
        Block b = e.getClickedBlock();

        // Check if block is dirt (to prevent mud conversion)
        if (b.getType() != Material.DIRT) {
            return;
        }

        // Get item from the hand that triggered the event
        ItemStack item = null;
        if (e.getHand() == EquipmentSlot.HAND) {
            item = p.getInventory().getItemInMainHand();
        } else if (e.getHand() == EquipmentSlot.OFF_HAND) {
            item = p.getInventory().getItemInOffHand();
        }

        // Check if player is using a water bottle
        if (item == null || item.getType() != Material.POTION) {
            return;
        }

        // Check if it's a water bottle specifically
        if (!isWaterBottle(item)) {
            return;
        }

        // Check if in claim
        String claimID = utils.getClaimID(b.getLocation());
        if (plugin.cfg.getClaimData().contains(claimID)) {
            String[] members = utils.getMembers(b.getLocation());

            if (members != null) {
                if (utils.isMemberOfClaim(members, p)) {
                    return; // Allow for claim members
                }

                String message = utils.chat("&cYou cannot convert dirt to mud in others claim");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                e.setCancelled(true);
            }
        }
    }

    // Helper method to check if an ItemStack is a water bottle
    private boolean isWaterBottle(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) {
            return false;
        }

        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta == null) {
            return false;
        }

        try {
            // Modern method (1.16+)
            return meta.getBasePotionType() == PotionType.WATER;
        } catch (NoSuchMethodError e) {
            try {
                // Legacy method
                return meta.getBasePotionData().getType() == PotionType.WATER;
            } catch (Exception ignored) {
                // Fallback for very old versions - water bottles have no effects
                return !meta.hasCustomEffects() && meta.getCustomEffects().isEmpty();
            }
        }
    }

    // For preventing copper scrapping with axe
    @EventHandler(ignoreCancelled = true)
    public void onCopperScrape(PlayerInteractEvent e) {
        // Check if player is right-clicking with axe
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }

        Player p = e.getPlayer();
        Block b = e.getClickedBlock();

        // Check if player is using an axe
        ItemStack item = e.getItem();
        if (item == null || !item.getType().name().endsWith("_AXE")) {
            return;
        }

        // Check if block is a copper block (any type of copper block)
        if (!b.getType().name().contains("COPPER")) {
            return;
        }

        // Check if in claim
        String claimID = utils.getClaimID(b.getLocation());
        if (plugin.cfg.getClaimData().contains(claimID)) {
            String[] members = utils.getMembers(b.getLocation());

            if (members != null) {
                if (utils.isMemberOfClaim(members, p)) {
                    return; // Allow for claim members
                }

                String message = utils.chat("&cYou cannot scrape copper blocks in others claim");
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                e.setCancelled(true);
            }
        }
    }
}