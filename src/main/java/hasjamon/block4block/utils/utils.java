package hasjamon.block4block.utils;

import com.comphenix.protocol.utility.MinecraftReflection;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.B4BlockBreakEvent;
import hasjamon.block4block.events.IntruderEnteredClaimEvent;
import hasjamon.block4block.events.PlayerClaimsCountedEvent;
import hasjamon.block4block.events.WelcomeMsgSentEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang.WordUtils;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.PistonHead;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.block.Chest;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class utils {
    private static final Block4Block plugin = Block4Block.getInstance();
    public static final Map<Block, GracePeriod> b4bGracePeriods = new LinkedHashMap<>();
    public static final Map<Location, GracePeriod> blockChangeGracePeriods = new LinkedHashMap<>();
    public static final Map<String, Set<Player>> intruders = new HashMap<>();
    public static final Map<IronGolem, String> ironGolems = new HashMap<>();
    public static final Map<Player, Set<String>> playerClaimsIntruded = new HashMap<>();
    public static final Map<Player, Long> lastIntrusionMsgReceived = new HashMap<>();
    public static final Map<Player, BukkitTask> undisguiseTasks = new HashMap<>();
    public static final Map<OfflinePlayer, String> activeDisguises = new HashMap<>();
    public static final Map<Player, Long> lastPlayerMoves = new HashMap<>();
    public static final Map<Player, BossBar> bossBars = new HashMap<>();
    public static final Map<Location, Long> claimInvulnerabilityStartTick = new HashMap<>();
    public static final Set<String> knownPlayers = new HashSet<>();
    public static final Set<Material> nonProtectiveBlockTypes = new HashSet<>();
    public static final Set<BlockFace> protectiveBlockFaces = new HashSet<>();
    private static final Set<Material> airTypes = Set.of(
            Material.AIR,
            Material.VOID_AIR,
            Material.CAVE_AIR
    );
    public static int minSecBetweenAlerts;
    public static int claimWidth;
    public static int lavaFlowMaxY;
    private static boolean masterBookChangeMsgSent = false;
    public static boolean isPaperServer = true;
    public static boolean canUseReflection = true;
    public static long lastClaimUpdate = 0;
    public static int gracePeriod = 0;
    public static long currentTick = 0;

    public static String chat(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String getChunkID(int blockX, int blockZ, World.Environment environment) {
        return environment.name() + "|" + (blockX >> 4) + "," + (blockZ >> 4);
    }

    public static String getChunkID(Location loc) {
        return getChunkID(loc.getBlockX(), loc.getBlockZ(), loc.getWorld().getEnvironment());
    }

    public static String getClaimID(int blockX, int blockZ, World.Environment environment) {
        int chunkX = (blockX >> 4);
        int claimX = (chunkX - ((chunkX < 0) ? claimWidth - 1 : 0)) / claimWidth;
        int chunkZ = (blockZ >> 4);
        int claimZ = (chunkZ - ((chunkZ < 0) ? claimWidth - 1 : 0)) / claimWidth;

        return environment.name() + "|" + claimX + "," + claimZ;
    }

    public static String getClaimID(String chunkID) {
        String[] parts = chunkID.split("\\|");
        String envName = parts[0];
        String[] xz = parts[1].split(",");
        int chunkX = Integer.parseInt(xz[0]);
        int chunkZ = Integer.parseInt(xz[1]);
        int claimX = (chunkX - ((chunkX < 0) ? claimWidth - 1 : 0)) / claimWidth;
        int claimZ = (chunkZ - ((chunkZ < 0) ? claimWidth - 1 : 0)) / claimWidth;

        return envName + "|" + claimX + "," + claimZ;
    }

    public static String getClaimID(Location loc) {
        return getClaimID(loc.getBlockX(), loc.getBlockZ(), loc.getWorld().getEnvironment());
    }

    public static String[] getMembers(Location loc) {
        return getMembers(getClaimID(loc));
    }

    public static String[] getMembers(String claimID) {
        String members = plugin.cfg.getClaimData().getString(claimID + ".members");

        if (members != null)
            return members.split("\\n");
        else
            return null;
    }

    public static boolean isProtectedClaimLectern(Block block) {
        return block.getType() == Material.LECTERN && isClaimBlock(block) && (countProtectedSides(block) > 0 || isClaimInvulnerable(block));
    }

    public static boolean isUnprotectedClaimLectern(Block block) {
        return block.getType() == Material.LECTERN && isClaimBlock(block) && countProtectedSides(block) == 0 && !isClaimInvulnerable(block);
    }

    public static long countProtectedSides(Block block) {
        return protectiveBlockFaces.stream().filter(direction -> {
            Block adjacent = block.getRelative(direction);
            boolean isProtectiveNonClaimBlock = !nonProtectiveBlockTypes.contains(adjacent.getType()) && !isClaimBlock(adjacent);
            long numFallingBlocksAbove = adjacent.getWorld()
                    .getNearbyEntities(BoundingBox.of(adjacent).resize(0, 0, 0, 0, 320, 0),
                            entity -> entity.getType() == EntityType.FALLING_BLOCK)
                    .size();
            boolean hasGravityAffectedBlockAbove = adjacent.getRelative(BlockFace.UP).getType().hasGravity();

            return isProtectiveNonClaimBlock ||
                    numFallingBlocksAbove > 0 ||
                    hasGravityAffectedBlockAbove;
        }).count();
    }

    public static boolean isClaimInvulnerable(Block block) {
        return currentTick - claimInvulnerabilityStartTick.getOrDefault(block.getLocation(), 0L) <= 10;
    }

    // Check if a block is a lectern with a claim book
    public static boolean isClaimBlock(Block b) {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        String cID = getClaimID(b.getLocation());
        double lecternX = claimData.getDouble(cID + ".location.X", Double.MAX_VALUE);
        double lecternY = claimData.getDouble(cID + ".location.Y", Double.MAX_VALUE);
        double lecternZ = claimData.getDouble(cID + ".location.Z", Double.MAX_VALUE);

        if (lecternX == Double.MAX_VALUE || lecternY == Double.MAX_VALUE || lecternZ == Double.MAX_VALUE)
            return false;

        return lecternX == b.getLocation().getX() && lecternY == b.getLocation().getY() && lecternZ == b.getLocation().getZ();
    }

    public static boolean isAdjacentToClaimBlock(Block b) {
        for (BlockFace face : plugin.cfg.getProtectiveBlockFaces()) {
            Block adjacent = b.getRelative(face);
            if (isClaimBlock(adjacent)) {
                return true;
            }
        }
        return false;
    }

    public static boolean claimChunk(Block block, List<String> members, Consumer<String> sendMessage) {
        // If it's a valid claim book
        if (!members.isEmpty()) {
            // If the lectern is next to bedrock: Cancel
            if (isTooCloseToBedrock(block)) {
                sendMessage.accept(chat("&cYou cannot place a claim next to bedrock"));
                return false;
            }

            setChunkClaim(block, members, sendMessage, null);
            updateClaimCount();
            plugin.cfg.saveClaimData();
            plugin.cfg.saveOfflineClaimNotifications();

        } else {
            sendMessage.accept(chat("&cHINT: Add \"claim\" at the top of the first page, followed by a list members, to claim this area!"));
        }

        return true;
    }

    public static void claimChunkBulk(Set<Block> blocks, BookMeta meta, String masterBookID) {
        if (meta != null) {
            List<String> members = findMembersInBook(meta);

            // If it's a valid claim book
            if (!members.isEmpty()) {
                for (Block block : blocks) {
                    // If the lectern is next to bedrock: Cancel
                    if (isTooCloseToBedrock(block))
                        continue;

                    setChunkClaim(block, members, masterBookID);
                }
                updateClaimCount();
                plugin.cfg.saveClaimData();
                plugin.cfg.saveOfflineClaimNotifications();
            }
        }
    }

    private static void setChunkClaim(Block block, List<String> members, String masterBookID) {
        setChunkClaim(block, members, null, masterBookID);
    }

    private static void setChunkClaim(Block block, List<String> members, @Nullable Consumer<String> sendMessage, String masterBookID) {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        Location blockLoc = block.getLocation();
        String claimID = getClaimID(blockLoc);
        String membersString = String.join("\n", members);

        claimData.set(claimID + ".location.X", blockLoc.getX());
        claimData.set(claimID + ".location.Y", blockLoc.getY());
        claimData.set(claimID + ".location.Z", blockLoc.getZ());
        claimData.set(claimID + ".members", membersString);

        onChunkClaim(claimID, members, sendMessage, masterBookID);
    }

    public static void onChunkClaim(String claimID, List<String> members, @Nullable Consumer<String> sendMessage, String masterBookID) {
        if (sendMessage == null)
            sendMessage = (msg) -> {
            };
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        Map<Boolean, List<String>> doMembersExist =
                members.stream().collect(
                        Collectors.partitioningBy(m -> knownPlayers.contains(m.toLowerCase())));

        // Inform the player of the claim and its members
        sendMessage.accept(chat("&eThis chunk has now been claimed!"));
        sendMessage.accept(chat("&aMembers who can access this chunk:"));
        for (String knownMember : doMembersExist.get(true)) {
            sendMessage.accept(ChatColor.GRAY + " - " + knownMember);

            boolean isOffline = onlinePlayers.stream().noneMatch(op -> op.getName().equalsIgnoreCase(knownMember));

            if (isOffline) {
                String name = knownMember.toLowerCase();
                FileConfiguration offlineClaimNotifications = plugin.cfg.getOfflineClaimNotifications();

                if (masterBookID != null)
                    offlineClaimNotifications.set(name + ".masterbooks." + masterBookID, false);
                else
                    offlineClaimNotifications.set(name + ".chunks." + claimID, null);
            }
        }
        for (String unknownMember : doMembersExist.get(false)) {
            sendMessage.accept(ChatColor.GRAY + " - " + unknownMember + ChatColor.RED + " (unknown player)");
        }

        for (Player player : onlinePlayers) {
            if (claimID.equals(getClaimID(player.getLocation()))) {
                updateBossBar(player, claimID);
                if (isIntruder(player, claimID))
                    onIntruderEnterClaim(player, claimID);
            }
        }

        lastClaimUpdate = System.nanoTime();

        // plugin.pluginManager.callEvent(new ChunkClaimedEvent(doMembersExist.get(true)));
    }

    public static void onChunkUnclaim(String claimID, String[] members, Location lecternLoc, String masterBookID) {
        String xyz = lecternLoc.getBlockX() + ", " + lecternLoc.getBlockY() + ", " + lecternLoc.getBlockZ();

        onChunkUnclaim(claimID, members, xyz, masterBookID);
    }

    public static void onChunkUnclaim(String claimID, String[] members, String lecternXYZ, String masterBookID) {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

        if (members != null) {
            for (String member : members) {
                if (knownPlayers.contains(member.toLowerCase())) {
                    boolean isOffline = true;

                    // Notify online members that they have lost the claim
                    for (Player player : onlinePlayers) {
                        if (player.getName().equalsIgnoreCase(member)) {
                            isOffline = false;
                            if (masterBookID != null) {
                                if (!masterBookChangeMsgSent) {
                                    String msg = "Your name has been removed from Master Book #" + masterBookID + " and all related claims!";
                                    player.sendMessage(ChatColor.RED + msg);
                                    masterBookChangeMsgSent = true;
                                }
                            } else {
                                String worldName = getWorldName(World.Environment.valueOf(claimID.split("\\|")[0]));
                                if (!plugin.getConfig().getBoolean("hide-coords-globally") && showCoordsInMsgs(player)) {
                                    player.sendMessage(ChatColor.RED + "You have lost a claim! Location: " + lecternXYZ + " in " + worldName);
                                } else {
                                    player.sendMessage(ChatColor.RED + "You have lost a claim! Location: [hidden] in " + worldName);
                                }
                            }
                            break;
                        }
                    }

                    if (isOffline) {
                        String name = member.toLowerCase();
                        FileConfiguration offlineClaimNotifications = plugin.cfg.getOfflineClaimNotifications();

                        if (masterBookID != null) {
                            offlineClaimNotifications.set(name + ".masterbooks." + masterBookID, true);
                        } else {
                            offlineClaimNotifications.set(name + ".chunks." + claimID, lecternXYZ);
                        }
                    }
                }
            }
        }

        for (Player player : onlinePlayers)
            if (claimID.equals(getClaimID(player.getLocation())))
                updateBossBar(player, claimID);

        Map<Player, String> intrudersThatLeft = new HashMap<>();
        if (intruders.containsKey(claimID))
            for (Player intruder : intruders.get(claimID))
                intrudersThatLeft.put(intruder, claimID);

        for (Player intruder : intrudersThatLeft.keySet())
            onIntruderLeaveClaim(intruder, intrudersThatLeft.get(intruder));

        lastClaimUpdate = System.nanoTime();
    }

    public static List<String> findMembersInBook(BookMeta meta) {
        List<String> pages = meta.getPages();

        return findMembersInBook(pages);
    }

    public static List<String> findMembersInBook(List<String> pages) {
        HashMap<String, String> members = new LinkedHashMap<>();

        for (String page : pages) {
            // If it isn't a claim page, stop looking for members
            if (!isClaimPage(page))
                break;

            String[] lines = page.split("\\n");

            for (int i = 1; i < lines.length; i++) {
                String member = lines[i].trim();

                // If the member name is valid
                if (!member.contains(" ") && !member.isEmpty() && !members.containsKey(member.toLowerCase()))
                    members.put(member.toLowerCase(), member);
            }
        }

        return members.values().stream().toList();
    }

    private static boolean isTooCloseToBedrock(Block block) {
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    if (block.getRelative(x, y, z).getType() == Material.BEDROCK)
                        return true;
        return false;
    }

    public static boolean isClaimPage(String page) {
        return page.length() >= 5 && page.substring(0, 5).equalsIgnoreCase("claim");
    }

    public static boolean isClaimBook(BookMeta meta) {
        return meta.hasPages() && isClaimPage(meta.getPage(1));
    }

    public static void unclaimChunk(Block block, boolean causedByPlayer, Consumer<String> sendMessage) {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        Location blockLoc = block.getLocation();
        String claimID = getClaimID(blockLoc);
        String[] members = getMembers(claimID);

        // If it's a (copy of a) master book, remove it from the list of copies on lecterns
        if (block.getType() == Material.LECTERN) {
            Lectern lectern = (Lectern) block.getState();
            ItemStack book = lectern.getInventory().getItem(0);

            if (book != null) {
                BookMeta meta = (BookMeta) book.getItemMeta();

                if (meta != null) {
                    List<String> lore = meta.getLore();

                    if (lore != null) {
                        FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
                        String bookID = String.join("", lore).substring(17);

                        if (masterBooks.contains(bookID + ".copies-on-lecterns")) {
                            List<String> copies = masterBooks.getStringList(bookID + ".copies-on-lecterns");
                            String xyz = blockLoc.getBlockX() + "," + blockLoc.getBlockY() + "," + blockLoc.getBlockZ();

                            copies.remove(claimID + "!" + xyz);

                            masterBooks.set(bookID + ".copies-on-lecterns", copies);
                            plugin.cfg.saveMasterBooks();
                        }
                    }
                }
            }
        }

        claimData.set(claimID, null);
        plugin.cfg.saveClaimData();

        if (causedByPlayer)
            sendMessage.accept(ChatColor.RED + "You have removed this claim!");

        onChunkUnclaim(claimID, members, blockLoc, null);
        plugin.cfg.saveOfflineClaimNotifications();

        plugin.cfg.getClaimTakeovers().set(claimID, null);
        plugin.cfg.saveClaimTakeovers();

        updateClaimCount();
    }

    public static void unclaimChunkBulk(Set<Block> blocks, String masterBookID, BookMeta meta) {
        FileConfiguration claimData = plugin.cfg.getClaimData();

        for (Block b : blocks) {
            Location bLoc = b.getLocation();
            String claimID = getClaimID(bLoc);
            String[] membersBefore = getMembers(claimID);
            List<String> membersAfter = findMembersInBook(meta);
            String[] membersRemoved = null;

            if (membersBefore != null)
                membersRemoved = Arrays.stream(membersBefore).filter(mb -> !membersAfter.contains(mb)).toArray(String[]::new);

            claimData.set(claimID, null);

            onChunkUnclaim(claimID, membersRemoved, bLoc, masterBookID);
        }
        plugin.cfg.saveClaimData();
        plugin.cfg.saveOfflineClaimNotifications();

        masterBookChangeMsgSent = false;
        updateClaimCount();
    }

    // Update tablist with current number of claims for each player
    public static void updateClaimCount() {
        HashMap<String, Integer> membersNumClaims = countMemberClaims();

        for (Player p : Bukkit.getOnlinePlayers()) {
            Integer pClaims = membersNumClaims.get(p.getName().toLowerCase());

            if (pClaims == null) {
                p.setPlayerListName(p.getName() + chat(" - &c0"));
            } else {
                p.setPlayerListName(p.getName() + chat(" - &c" + pClaims));
                plugin.pluginManager.callEvent(new PlayerClaimsCountedEvent(p, pClaims));
            }
        }
    }

    // Returns a HashMap of player name (lowercase) and number of claims
    public static HashMap<String, Integer> countMemberClaims() {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        HashMap<String, Integer> count = new HashMap<>();

        for (String key : claimData.getKeys(false)) {
            ConfigurationSection chunk = claimData.getConfigurationSection(key);

            if (chunk != null) {
                String currentMembers = chunk.getString("members");
                if (currentMembers != null)
                    for (String cm : currentMembers.toLowerCase().split("\\n"))
                        count.merge(cm, 1, Integer::sum);
            }
        }

        return count;
    }

    public static boolean hasClaims(Player p) {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        String playerName = p.getName().toLowerCase();

        for (String key : claimData.getKeys(false)) {
            ConfigurationSection chunk = claimData.getConfigurationSection(key);

            if (chunk != null) {
                String currentMembers = chunk.getString("members");
                if (currentMembers != null)
                    for (String member : currentMembers.toLowerCase().split("\\n"))
                        if (member.equals(playerName))
                            return true;
            }
        }

        return false;
    }

    public static void b4bCheck(Player p, Block b, BlockBreakEvent e, List<?> lootDisabledTypes, boolean requiresBlock, boolean isFreeToBreakInClaim) {
        // Are drops disabled for this block type
        boolean noloot = lootDisabledTypes.contains(b.getType().toString());

        if (requiresBlock) {
            Material requiredType = b.getType();

            ConfigurationSection substitutions = plugin.getConfig().getConfigurationSection("b4b-substitutions");
            if (substitutions != null && substitutions.contains(requiredType.name()))
                requiredType = Material.valueOf(substitutions.getString(requiredType.name()));

            if (p.getInventory().getItemInOffHand().getType() == requiredType) {
                p.getInventory().getItemInOffHand().setAmount(p.getInventory().getItemInOffHand().getAmount() - 1);
            } else {
                boolean itemInInventory = false;
                for (int i = 0; i < 9; i++) {
                    ItemStack item = p.getInventory().getItem(i);
                    if (item != null) {
                        if (item.getType() == requiredType) {
                            item.setAmount(item.getAmount() - 1);
                            itemInInventory = true;
                            break;
                        }
                    }
                }

                if (!itemInInventory) {
                    String message = chat(isFreeToBreakInClaim ?
                            "&cClaim &athe area or spend &c" + requiredType + " &afrom your hotbar to break this!" :
                            "&aSpend &c" + requiredType + " &afrom your hotbar to break this!");
                    e.setCancelled(true);
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                    plugin.pluginManager.callEvent(new B4BlockBreakEvent(p, b, false, isFreeToBreakInClaim));
                    return;
                }
            }

            plugin.pluginManager.callEvent(new B4BlockBreakEvent(p, b, true, isFreeToBreakInClaim));
            getClaimBlocksProtectedBy(b).forEach(claimBlock ->
                    claimInvulnerabilityStartTick.put(claimBlock.getLocation(), currentTick)
            );
        }

        if (noloot) {
            dropInventory(b);

            // It would be great if we could just use `e.setDropItems(false);` instead of the below
            // unfortunately, that would make attached blocks such as buttons disappear
            if (b.getType() == Material.PISTON_HEAD) {
                PistonHead pistonHead = (PistonHead) b.getBlockData();
                Block piston = b.getRelative(pistonHead.getFacing().getOppositeFace());
                piston.setType(Material.AIR);
            }

            b.setType(Material.AIR);
            e.setCancelled(true);
        }
    }

    public static List<Block> getClaimBlocksProtectedBy(Block protectingBlock) {
        List<Block> result = new LinkedList<>();

        for (BlockFace face : protectiveBlockFaces) {
            Block adjacent = protectingBlock.getRelative(face.getOppositeFace());
            if (isClaimBlock(adjacent)) {
                result.add(adjacent);
            }
        }

        return result;
    }

    private static void dropInventory(Block b) {
        if (b.getState() instanceof BlockInventoryHolder bInv) {
            Inventory inv = bInv.getInventory();

            for (ItemStack item : inv.getStorageContents()) {
                if (item != null) {
                    b.getWorld().dropItemNaturally(b.getLocation(), item);
                }
            }
        }
    }

    // Returns log2(n + 2)
    public static double calcGeneralChickenBonus(double numNamedChickens) {
        // log2(x) = log(x) / log(2)
        return Math.log(numNamedChickens + 2) / Math.log(2);
    }

    public static ChickenBonuses calcChickenBonuses(Entity center) {
        double radius = plugin.getConfig().getInt("named-chicken-radius") + 0.5;
        List<Entity> nearbyEntities = center.getNearbyEntities(radius, radius, radius);
        Set<String> namedChickensPos = new HashSet<>();
        Map<Character, Integer> letterBonuses = new HashMap<>();

        for (Entity ne : nearbyEntities) {
            if (ne.getType() == EntityType.CHICKEN) {
                String chickenName = ne.getCustomName();

                if (chickenName != null) {
                    Location loc = ne.getLocation();
                    String pos = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();

                    // If no other named chicken has been found at that location
                    if (!namedChickensPos.contains(pos)) {
                        namedChickensPos.add(pos);
                        letterBonuses.merge(chickenName.toLowerCase().charAt(0), 1, Integer::sum);
                    }
                }
            }
        }

        return new ChickenBonuses(letterBonuses, namedChickensPos.size());
    }

    public static class SpawnEggUtils {

        // Use your plugin instance appropriately here; for example, if you have a static instance reference in your main class:
        private static final NamespacedKey eggKey = new NamespacedKey(Block4Block.getInstance(), "black_bear_spawn_egg");

        public static ItemStack getRandomSpawnEgg(Map<Character, Integer> letterBonuses) {
            ConfigurationSection weightConfig = Block4Block.getInstance().getConfig().getConfigurationSection("spawn-egg-weights");
            Random rand = new Random();
            int totalWeight = calcTotalWeight(letterBonuses);
            int i = rand.nextInt(totalWeight);

            if (weightConfig != null) {
                for (String eggName : weightConfig.getKeys(false)) {
                    Character firstLetter = eggName.toLowerCase().charAt(0);
                    Integer bonus = letterBonuses.get(firstLetter);
                    int weight = weightConfig.getInt(eggName);

                    if (bonus != null)
                        weight *= (1 + bonus);
                    i -= weight;

                    // Handle Black Bear Spawn Egg: return the custom item if selected
                    if (eggName.equalsIgnoreCase("BLACK_BEAR_SPAWN_EGG")) {
                        return createBlackBearEgg();
                    }

                    if (i <= 0) {
                        return new ItemStack(Material.valueOf(eggName));
                    }
                }
            }

            // Default fallback
            return new ItemStack(Material.TROPICAL_FISH_SPAWN_EGG);
        }

        private static int calcTotalWeight(Map<Character, Integer> letterBonuses) {
            ConfigurationSection weightConfig = Block4Block.getInstance().getConfig().getConfigurationSection("spawn-egg-weights");
            int totalWeight = 0;

            if (weightConfig != null) {
                for (String eggName : weightConfig.getKeys(false)) {
                    Character firstLetter = eggName.toLowerCase().charAt(0);
                    Integer bonus = letterBonuses.get(firstLetter);
                    int weight = weightConfig.getInt(eggName);

                    if (bonus != null)
                        weight *= (1 + bonus);
                    totalWeight += weight;
                }
            }
            return totalWeight;
        }

        public static ItemStack createBlackBearEgg() {
            ItemStack egg = new ItemStack(Material.COAL);
            ItemMeta meta = egg.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.WHITE + "Black Bear Spawn Egg");
                meta.getPersistentDataContainer().set(eggKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                egg.setItemMeta(meta);
            }
            return egg;
        }
    }

    public static void onIntruderEnterClaim(Player intruder, String claimID) {
        if (intruder.getGameMode() != GameMode.SURVIVAL)
            return;

        FileConfiguration claimData = plugin.cfg.getClaimData();
        double x = claimData.getDouble(claimID + ".location.X");
        double y = claimData.getDouble(claimID + ".location.Y");
        double z = claimData.getDouble(claimID + ".location.Z");

        if (!intruders.containsKey(claimID))
            intruders.put(claimID, new HashSet<>());

        intruders.get(claimID).add(intruder);

        String[] members = getMembers(claimID);

        if (members != null) {
            for (String m : members) {
                Player p = Bukkit.getPlayerExact(m);

                if (p != null) {
                    long now = System.nanoTime();

                    if (!playerClaimsIntruded.containsKey(p))
                        playerClaimsIntruded.put(p, new HashSet<>());
                    playerClaimsIntruded.get(p).add(claimID);

                    if (now - lastIntrusionMsgReceived.getOrDefault(p, 0L) >= minSecBetweenAlerts * 1e9) {
                        String worldName = getWorldName(World.Environment.valueOf(claimID.split("\\|")[0]));
                        if (!plugin.getConfig().getBoolean("hide-coords-globally") && showCoordsInMsgs(p)) {
                            p.sendMessage(ChatColor.RED +
                                    "An intruder has entered your claim at " + x + ", " + y + ", " + z + " in " + worldName);
                        } else {
                            p.sendMessage(ChatColor.RED +
                                    "An intruder has entered your claim at [hidden] in " + worldName);
                        }
                        lastIntrusionMsgReceived.put(p, now);
                        plugin.pluginManager.callEvent(new IntruderEnteredClaimEvent(p));
                    }
                }
            }
        }
    }

    public static boolean showCoordsInMsgs(Player p) {
        String playerSetting = plugin.cfg.getCoordsSettings().getString(p.getUniqueId().toString());

        return playerSetting == null || playerSetting.equals("on");
    }

    public static void onIntruderLeaveClaim(Player intruder, String claimID) {
        if (intruders.containsKey(claimID)) {
            intruders.get(claimID).remove(intruder);

            if (intruders.get(claimID).isEmpty())
                intruders.remove(claimID);
        }
    }

    public static boolean isIntruder(Player p, String claimID) {
        String[] members = getMembers(claimID);

        return members != null && !isMemberOfClaim(members, p);
    }

    public static void updateGolemHostility() {
        for (Map.Entry<IronGolem, String> entry : ironGolems.entrySet()) {
            IronGolem golem = entry.getKey();
            String currentChunkID = getChunkID(golem.getLocation());
            String prevChunkID = entry.getValue();

            // If the golem has entered a new chunk
            if (!currentChunkID.equals(prevChunkID)) {
                entry.setValue(currentChunkID);
                String claimID = getClaimID(golem.getLocation());

                // Make it hostile to all intruders in the chunk it just entered
                if (intruders.containsKey(claimID))
                    for (Player intruder : intruders.get(claimID))
                        if (currentChunkID.equals(getChunkID(intruder.getLocation())))
                            golem.setTarget(intruder);
            }
        }
    }

    public static void populatePlayerClaimsIntruded(Player p) {
        // Go through all intruded claims
        for (String claimID : intruders.keySet()) {
            String[] members = getMembers(claimID);

            if (members != null) {
                if (isMemberOfClaim(members, p, false)) {
                    if (!playerClaimsIntruded.containsKey(p))
                        playerClaimsIntruded.put(p, new HashSet<>());

                    // Add the chunk as one of p's intruded claims
                    playerClaimsIntruded.get(p).add(claimID);
                }
            }
        }
    }

    public static boolean isMemberOfClaim(String[] members, OfflinePlayer p) {
        if (members != null && p != null) {
            return isMemberOfClaim(members, p, true);
        }
        return false;
    }

    public static boolean isMemberOfClaim(String[] members, OfflinePlayer p, boolean allowDisguise) {
        if (members != null && p != null) {
            for (String member : members) {
                boolean disguisedAsMember = member.equalsIgnoreCase(activeDisguises.getOrDefault(p, ""));
                if (member.equalsIgnoreCase(p.getName()) || allowDisguise && disguisedAsMember)
                    return true;
            }
        }

        return false;
    }

    public static void disguisePlayer(Player disguiser, OfflinePlayer disguisee) {
        Collection<Property> textures = getCachedTextures(disguisee);
        activeDisguises.put(disguiser, disguisee.getName());
        applyDisguise(disguiser, textures);
    }

    private static void applyDisguise(Player disguiser, Collection<Property> textures) {
        String claimID = getClaimID(disguiser.getLocation());

        setTextures(disguiser, textures);
        updateTexturesForOthers(disguiser);
        updateTexturesForSelf(disguiser);
        updateBossBar(disguiser, claimID);
    }

    public static Collection<Property> getTextures(OfflinePlayer p) {
        if (canUseReflection) {
            try {
                Method getProfile = MinecraftReflection.getCraftPlayerClass().getDeclaredMethod("getProfile");
                GameProfile gp = (GameProfile) getProfile.invoke(p);

                return gp.getProperties().get("textures");
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public static Collection<Property> getCachedTextures(OfflinePlayer p) {
        List<String> strs = plugin.cfg.getPlayerTextures().getStringList(p.getUniqueId().toString());
        Collection<Property> textures = new ArrayList<>();

        if (strs.size() == 3)
            textures.add(new Property(strs.get(0), strs.get(1), strs.get(2)));
        else if (strs.size() == 2)
            textures.add(new Property(strs.get(0), strs.get(1)));

        return textures;
    }

    public static void setTextures(Player p, Collection<Property> textures) {
        if (canUseReflection) {
            try {
                Method getProfile = MinecraftReflection.getCraftPlayerClass().getDeclaredMethod("getProfile");
                GameProfile gp = (GameProfile) getProfile.invoke(p);

                gp.getProperties().removeAll("textures");
                gp.getProperties().putAll("textures", textures);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    public static void updateTexturesForOthers(Player disguiser) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hidePlayer(plugin, disguiser);
            p.showPlayer(plugin, disguiser);
        }
    }

    public static void updateTexturesForSelf(Player disguiser) {
        Entity vehicle = disguiser.getVehicle();

        if (vehicle != null) {
            vehicle.removePassenger(disguiser);

            Bukkit.getScheduler().runTaskLater(plugin, () -> vehicle.addPassenger(disguiser), 1);
        }

        if (canUseReflection) {
            try {
                Method refreshPlayerMethod = MinecraftReflection.getCraftPlayerClass().getDeclaredMethod("refreshPlayer");

                refreshPlayerMethod.setAccessible(true);
                refreshPlayerMethod.invoke(disguiser);

                // Fix visual bug that hides level/exp
                disguiser.setExp(disguiser.getExp());
            } catch (InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                isPaperServer = false;
            }
        }
    }

    public static void restorePlayerSkin(Player p) {
        applyDisguise(p, getCachedTextures(p));
    }

    public static void onLoseDisguise(Player disguiser) {
        if (activeDisguises.containsKey(disguiser)) {
            String claimID = getClaimID(disguiser.getLocation());

            activeDisguises.remove(disguiser);
            disguiser.sendMessage("Your disguise has expired!");

            updateBossBar(disguiser, claimID);
            if (isIntruder(disguiser, claimID))
                onIntruderEnterClaim(disguiser, claimID);

            if (undisguiseTasks.containsKey(disguiser)) {
                undisguiseTasks.get(disguiser).cancel();
                undisguiseTasks.remove(disguiser);
            }

        }
    }

    public static boolean replaceInClaimPages(List<String> pages, String search, String replace) {
        for (int i = 0; i < pages.size(); i++) {
            String page = pages.get(i);

            if (!isClaimPage(page))
                break;

            String[] membersArray = page.split("\\n");

            for (int j = 1; j < membersArray.length; j++)
                if (membersArray[j].equalsIgnoreCase(search))
                    membersArray[j] = replace;

            pages.set(i, String.join("\n", membersArray));
        }
        return false;
    }

    private static int getBlocksPerPixel(MapView.Scale scale) {
        int blocksPerPixel = 0;

        switch (scale) {
            case CLOSEST -> blocksPerPixel = 1;
            case CLOSE -> blocksPerPixel = 2;
            case NORMAL -> blocksPerPixel = 4;
            case FAR -> blocksPerPixel = 8;
            case FARTHEST -> blocksPerPixel = 16;
        }

        return blocksPerPixel;
    }

    public static MapRenderer createClaimRenderer(OfflinePlayer creator) {
        return new MapRenderer() {
            final OfflinePlayer owner = creator;
            long lastUpdate = 0;
            Map<String, Coords2D> claims = null;
            int blocksPerPixel = 0;

            public void render(MapView view, MapCanvas canvas, Player p) {
                int centerX = view.getCenterX();
                int centerZ = view.getCenterZ();

                if (blocksPerPixel == 0)
                    blocksPerPixel = getBlocksPerPixel(view.getScale());

                if (lastUpdate <= lastClaimUpdate || claims == null) {
                    World world = view.getWorld();

                    if (world != null) {
                        World.Environment env = world.getEnvironment();
                        Map<String, Coords2D> claimsNow = findClaimsOnCanvas(env, centerX, centerZ, blocksPerPixel);
                        addClaimsToCanvas(canvas, claims, claimsNow, owner, blocksPerPixel);
                        claims = claimsNow;
                        lastUpdate = System.nanoTime();
                    }
                } else if (lastUpdate < lastPlayerMoves.getOrDefault(p, 0L)) {
                    int px = 2 * (p.getLocation().getBlockX() - centerX) / blocksPerPixel;
                    int pz = 2 * (p.getLocation().getBlockZ() - centerZ) / blocksPerPixel;

                    if (px <= 127 && px >= -128 && pz <= 127 && pz >= -128) {
                        addClaimsToCanvas(canvas, null, claims, owner, blocksPerPixel);
                        lastUpdate = System.nanoTime();
                    }
                }
            }
        };
    }

    private static Map<String, Coords2D> findClaimsOnCanvas(World.Environment env, int centerX, int centerZ, int blocksPerPixel) {
        Map<String, Coords2D> claims = new HashMap<>();
        int x = centerX - 64 * blocksPerPixel;

        for (int i = 0; i < 128; i += 16 / blocksPerPixel) {
            int z = centerZ - 64 * blocksPerPixel;

            for (int j = 0; j < 128; j += 16 / blocksPerPixel) {
                String chunkID = getChunkID(x, z, env);
                String claimID = getClaimID(x, z, env);
                FileConfiguration claimData = plugin.cfg.getClaimData();

                if (claimData.contains(claimID))
                    claims.put(chunkID, new Coords2D(i, j));

                z += 16;
            }

            x += 16;
        }

        return claims;
    }

    private static void addClaimsToCanvas(
            MapCanvas canvas,
            Map<String, Coords2D> claimsBefore,
            Map<String, Coords2D> claimsNow,
            OfflinePlayer p,
            int blocksPerPixel
    ) {
        int chunkPixels = 16 / blocksPerPixel;

        // --- 1) CLEAR any chunks that disappeared ---
        if (claimsBefore != null) {
            // remove all still‑present chunks
            claimsBefore.values().removeAll(claimsNow.values());
            for (String chunkID : claimsBefore.keySet()) {
                Coords2D c = claimsBefore.get(chunkID);
                int x0 = c.x, z0 = c.z;
                for (int dx = 0; dx < chunkPixels; dx++)
                    for (int dz = 0; dz < chunkPixels; dz++)
                        canvas.setPixel(x0 + dx, z0 + dz,
                                canvas.getBasePixel(x0 + dx, z0 + dz));
            }
        }

        // --- 2) Build per‑chunk color map for all current chunks ---
        Map<String, Byte> chunkColor = new HashMap<>();
        for (String chunkID : claimsNow.keySet()) {
            Coords2D c = claimsNow.get(chunkID);
            // determine color
            String claimID = getClaimID(chunkID);
            boolean member = isMemberOfClaim(getMembers(claimID), p);
            String cfg = member ? "my-claims" : "others-claims";
            int r = plugin.getConfig().getInt("claim-map-colors." + cfg + ".r");
            int g = plugin.getConfig().getInt("claim-map-colors." + cfg + ".g");
            int b = plugin.getConfig().getInt("claim-map-colors." + cfg + ".b");
            byte color = MapPalette.matchColor(r, g, b);
            chunkColor.put(chunkID, color);
        }

        // --- 3) For each color‑group, draw only external edges ---
        // group chunkIDs by color
        Map<Byte, Set<String>> byColor = new HashMap<>();
        for (var e : chunkColor.entrySet()) {
            byColor
                    .computeIfAbsent(e.getValue(), k -> new HashSet<>())
                    .add(e.getKey());
        }

        // helper to parse chunkID "ENV|X,Z" → int{x,z}
        record ChunkPos(int x, int z) {}
        for (var grp : byColor.entrySet()) {
            byte color = grp.getKey();
            Set<String> chunks = grp.getValue();

            // build quick lookup of chunk positions
            Map<ChunkPos, Coords2D> posMap = new HashMap<>();
            for (String chunkID : chunks) {
                // parse out chunkX,chunkZ
                String[] parts = chunkID.split("\\|")[1].split(",");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                posMap.put(new ChunkPos(cx, cz), claimsNow.get(chunkID));
            }

            // for each chunk, if neighbor missing → draw that side
            for (var entry : posMap.entrySet()) {
                ChunkPos cp = entry.getKey();
                Coords2D pix = entry.getValue();
                int x0 = pix.x, z0 = pix.z;
                int x1 = x0 + chunkPixels - 1;
                int z1 = z0 + chunkPixels - 1;

                // West side?
                if (!posMap.containsKey(new ChunkPos(cp.x - 1, cp.z))) {
                    for (int dz = 0; dz < chunkPixels; dz++)
                        canvas.setPixel(x0, z0 + dz, color);
                }
                // East side?
                if (!posMap.containsKey(new ChunkPos(cp.x + 1, cp.z))) {
                    for (int dz = 0; dz < chunkPixels; dz++)
                        canvas.setPixel(x1, z0 + dz, color);
                }
                // North side?
                if (!posMap.containsKey(new ChunkPos(cp.x, cp.z - 1))) {
                    for (int dx = 0; dx < chunkPixels; dx++)
                        canvas.setPixel(x0 + dx, z0, color);
                }
                // South side?
                if (!posMap.containsKey(new ChunkPos(cp.x, cp.z + 1))) {
                    for (int dx = 0; dx < chunkPixels; dx++)
                        canvas.setPixel(x0 + dx, z1, color);
                }
            }
        }
    }

    public static MapRenderer createIntruderRenderer(OfflinePlayer creator) {
        return new MapRenderer() {
            final OfflinePlayer owner = creator;
            int blocksPerPixel = 0;

            public void render(MapView view, MapCanvas canvas, Player p) {
                if (blocksPerPixel == 0)
                    blocksPerPixel = getBlocksPerPixel(view.getScale());

                if (!intruders.isEmpty()) {
                    int centerX = view.getCenterX();
                    int centerZ = view.getCenterZ();

                    addIntrudersToCanvas(canvas, centerX, centerZ, blocksPerPixel, owner);
                } else {
                    canvas.setCursors(new MapCursorCollection());
                }
            }
        };
    }

    private static void addIntrudersToCanvas(MapCanvas canvas, int centerX, int centerZ, int blocksPerPixel, OfflinePlayer p) {
        MapCursorCollection cursors = new MapCursorCollection();

        for (String claimID : intruders.keySet()) {
            FileConfiguration claimData = plugin.cfg.getClaimData();

            if (claimData.contains(claimID)) {
                String[] members = getMembers(claimID);
                boolean isMember = isMemberOfClaim(members, p);

                if (isMember) {
                    for (Player intruder : intruders.get(claimID)) {
                        int px = 2 * (intruder.getLocation().getBlockX() - centerX) / blocksPerPixel;
                        int pz = 2 * (intruder.getLocation().getBlockZ() - centerZ) / blocksPerPixel;

                        if (px <= 127 && px >= -128 && pz <= 127 && pz >= -128) {
                            if (canvas.getBasePixel((px + 128) / 2, (pz + 128) / 2) != MapPalette.TRANSPARENT) {
                                MapCursor cursor = getMapCursor(intruder, (byte) px, (byte) pz);
                                cursors.addCursor(cursor);
                            }
                        }
                    }
                }
            }
        }

        canvas.setCursors(cursors);
    }

    private static MapCursor getMapCursor(Player intruder, byte px, byte pz) {
        double yaw = intruder.getLocation().getYaw();
        byte direction = (byte) Math.min(15, Math.max(0, (((yaw + 371.25) % 360) / 22.5)));
        MapCursor.Type type = MapCursor.Type.RED_MARKER;
        String caption = intruder.getName();

        return new MapCursor(px, pz, direction, type, true, caption);
    }

    public static String getWorldName(World.Environment env) {
        return switch (env) {
            case NORMAL -> "The Overworld";
            case NETHER -> "The Nether";
            case THE_END -> "The End";
            default -> "Unknown World";
        };
    }

    public static void removeExpiredB4BGracePeriods() {
        Set<Block> expiredGracePeriods = new HashSet<>();

        // Grace periods count as expired if x seconds have passed or the block's material has changed
        for (Map.Entry<Block, GracePeriod> entry : b4bGracePeriods.entrySet())
            if (System.nanoTime() - entry.getValue().timestamp >= gracePeriod * 1e9
                    || !entry.getValue().type.equals(entry.getKey().getType()))
                expiredGracePeriods.add(entry.getKey());

        for (Block expired : expiredGracePeriods)
            b4bGracePeriods.remove(expired);
    }

    public static void removeExpiredBlockChangeGracePeriods() {
        Set<Location> expiredGracePeriods = new HashSet<>();

        // Grace periods count as expired if x seconds have passed or the block's material has changed
        for (Map.Entry<Location, GracePeriod> entry : blockChangeGracePeriods.entrySet())
            if (System.nanoTime() - entry.getValue().timestamp >= gracePeriod * 1e9)
                expiredGracePeriods.add(entry.getKey());

        for (Location expired : expiredGracePeriods)
            blockChangeGracePeriods.remove(expired);
    }

    public static void sendWelcomeMsg(Player player) {
        List<String> welcomeMessages = plugin.getConfig().getStringList("welcome-messages");

        for (String msg : welcomeMessages) {
            player.sendMessage(chat(msg));
        }

        plugin.pluginManager.callEvent(new WelcomeMsgSentEvent(player));
    }

    public static void updateBossBar(Player p, String currentClaimID) {
        // Check if the player has a boss bar, if not, add it
        if (!bossBars.containsKey(p)) {
            addBossBar(p);
        }

        // Use Bukkit scheduler to delay the update by a small number of ticks (e.g., 20 ticks = 1 second)
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                BossBar bossBar = bossBars.get(p);
                FileConfiguration claimData = plugin.cfg.getClaimData();
                boolean isClaimed = claimData.contains(currentClaimID);

                // If the claim is not claimed
                if (!isClaimed) {
                    bossBar.setColor(BarColor.WHITE);
                    bossBar.setTitle(hasClaims(p) ? "Unclaimed" : "Unclaimed (place book on lectern to claim)");
                    bossBar.setProgress(0.0);  // No progress if no claim
                    return;
                }

                // If the claim is claimed, get the coordinates string
                String coordsStr = getCoordsString(p, currentClaimID);

                // Check if the player is an intruder
                if (isIntruder(p, currentClaimID)) {
                    // If the player is an intruder, set the boss bar to red
                    bossBar.setColor(BarColor.RED);
                    bossBar.setTitle("Claimed at " + coordsStr + " (Intruding)");
                } else {
                    // If the player is not an intruder, set the boss bar to green
                    bossBar.setColor(BarColor.GREEN);
                    bossBar.setTitle("Claimed at " + coordsStr);
                }

                // Get the list of protected block faces from the custom config
                Set<BlockFace> protectedBlockFaces = utils.protectiveBlockFaces;

                // Retrieve the claim's block location (e.g., the lectern's position) from the claim data
                String claimID = getClaimID(p.getLocation());  // Use player's location or another relevant location
                double lecternX = claimData.getDouble(claimID + ".location.X", Double.MAX_VALUE);
                double lecternY = claimData.getDouble(claimID + ".location.Y", Double.MAX_VALUE);
                double lecternZ = claimData.getDouble(claimID + ".location.Z", Double.MAX_VALUE);

                // If the lectern location is invalid, log the warning and exit early
                if (lecternX == Double.MAX_VALUE || lecternY == Double.MAX_VALUE || lecternZ == Double.MAX_VALUE) {
                    plugin.getLogger().warning("No valid lectern found for claim: " + claimID);
                    return;  // Exit the method early
                }

                // Create the Location object and get the corresponding block
                Location lecternLocation = new Location(p.getWorld(), lecternX, lecternY, lecternZ);
                Block claimBlock = lecternLocation.getBlock();

                // Count how many sides are protected using the countProtectedSides method
                long protectedCount = countProtectedSides(claimBlock);

                // Calculate the total sides based on the configuration
                int totalSides = protectedBlockFaces.size();

                // Prevent NaN error by ensuring totalSides is not 0
                if (totalSides == 0) {
                    bossBar.setProgress(0.0);  // Or set it to 1.0 or another default value, depending on your desired behavior
                    return;  // Exit the method early if there are no sides to protect
                }

                // Normalize the progress to be between 0.0 and 1.0
                double progress = (double) protectedCount / totalSides;

                // Update the progress bar to reflect the number of protected faces (out of totalSides)
                bossBar.setProgress(progress);
            }
        }, 2L);  // 2L means 2 ticks (0.1 second) delay
    }





    private static void addBossBar(Player p) {
        boolean sendLecternMsgs = plugin.getConfig().getBoolean("lectern-message-settings.send-lectern-messages");
        BossBar bossBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
        if (sendLecternMsgs)
            bossBar.addPlayer(p);
        bossBars.put(p, bossBar);
    }

    private static String getCoordsString(Player p, String claimID) {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        double x = claimData.getDouble(claimID + ".location.X");
        double y = claimData.getDouble(claimID + ".location.Y");
        double z = claimData.getDouble(claimID + ".location.Z");
        boolean hideCoords = plugin.getConfig().getBoolean("hide-coords-globally") || !showCoordsInMsgs(p);
        boolean showExactCoords = plugin.getConfig().getBoolean("lectern-message-settings.show-exact-coords");
        boolean showY = plugin.getConfig().getBoolean("lectern-message-settings.show-y");
        String result;

        if (hideCoords) {
            result = "[hidden]";
        } else if (showExactCoords) {
            result = "(" + (int) x + ", " + (showY ? (int) y : "??") + ", " + (int) z + ")";
        } else {
            double signX = (x == 0) ? 1 : Math.signum(x);
            double signY = (y == 0) ? 1 : Math.signum(y);
            double signZ = (z == 0) ? 1 : Math.signum(z);
            int chunkCenterX = (int) (x - x % 16 + signX * 8);
            int chunkCenterY = (int) (y - y % 16 + signY * 8);
            int chunkCenterZ = (int) (z - z % 16 + signZ * 8);

            result = "(" + chunkCenterX + ", " + (showY ? chunkCenterY : "??") + ", " + chunkCenterZ + ")";
        }

        return result;
    }

    public static boolean isAir(Material type) {
        return airTypes.contains(type);
    }

    public static void updateCurrentTick() {
        currentTick++;
    }

    public static <E extends Enum<E>> String prettifyEnumName(E theEnum) {
        return WordUtils.capitalizeFully(theEnum.name().replaceAll("_", " "));
    }

    public static boolean willLavaFlowAt(int blockY, World.Environment dimension) {
        return blockY <= lavaFlowMaxY || dimension == World.Environment.NETHER;
    }

    public static boolean isPhantomElytra(ItemStack itemStack) {
        if (itemStack != null && itemStack.getType() == Material.ELYTRA) {
            ItemMeta meta = itemStack.getItemMeta();

            if (meta != null && meta.getLore() != null)
                return meta.getLore().get(0).equals("An inferior version that can only glide for a second");
        }

        return false;
    }

    public static boolean canOpenChest(Block block, Player player) {
        // Ensure the block is a valid chest container
        if (!(block.getState() instanceof org.bukkit.block.Chest)) {
            return false;
        }

        org.bukkit.block.Chest chestState = (org.bukkit.block.Chest) block.getState();
        List<Block> chestBlocks = new ArrayList<>();

        // Check if this is a double chest
        if (chestState.getInventory() instanceof org.bukkit.inventory.DoubleChestInventory) {
            org.bukkit.inventory.DoubleChestInventory doubleInv =
                    (org.bukkit.inventory.DoubleChestInventory) chestState.getInventory();
            if (doubleInv.getHolder() instanceof org.bukkit.block.DoubleChest) {
                org.bukkit.block.DoubleChest doubleChest =
                        (org.bukkit.block.DoubleChest) doubleInv.getHolder();

                // Get the left/right chest
                Chest leftChest = (Chest) doubleChest.getLeftSide();
                Chest rightChest = (Chest) doubleChest.getRightSide();

                // Convert each Chest to a Block
                Block leftBlock = leftChest.getLocation().getBlock();
                Block rightBlock = rightChest.getLocation().getBlock();

                chestBlocks.add(leftBlock);
                chestBlocks.add(rightBlock);
            } else {
                // Not actually a double chest, so just add the single block
                chestBlocks.add(block);
            }
        } else {
            // Single chest
            chestBlocks.add(block);
        }

        // Check for blocks above any part of the chest
        for (Block chestBlock : chestBlocks) {
            Block above = chestBlock.getRelative(BlockFace.UP);
            if (above.getType().isOccluding()) {
                return false; // A block above prevents opening
            }
        }

        return true;
    }


    // Determine which direction the chest is connected to
    private static BlockFace getConnectedFace(org.bukkit.block.data.type.Chest chestData) {
        switch (chestData.getFacing()) {
            case NORTH:
                return chestData.getType() == org.bukkit.block.data.type.Chest.Type.LEFT ? BlockFace.WEST : BlockFace.EAST;
            case SOUTH:
                return chestData.getType() == org.bukkit.block.data.type.Chest.Type.LEFT ? BlockFace.EAST : BlockFace.WEST;
            case EAST:
                return chestData.getType() == org.bukkit.block.data.type.Chest.Type.LEFT ? BlockFace.NORTH : BlockFace.SOUTH;
            case WEST:
                return chestData.getType() == org.bukkit.block.data.type.Chest.Type.LEFT ? BlockFace.SOUTH : BlockFace.NORTH;
            default:
                return BlockFace.SELF;
        }
    }

    // Helper method to check if two chests are part of a double chest
    private static boolean isDoubleChest(Block chest1, Block chest2) {
        // Check if both blocks are chests before proceeding
        if (chest1.getType() != Material.CHEST || chest2.getType() != Material.CHEST) {
            return false;
        }

        // Correctly cast the chest states
        org.bukkit.block.Chest chestState1 = (org.bukkit.block.Chest) chest1.getState();
        org.bukkit.block.Chest chestState2 = (org.bukkit.block.Chest) chest2.getState();

        // Compare inventory holders to determine if they share the same inventory
        return chestState1.getInventory().getHolder() == chestState2.getInventory().getHolder();
    }
}
