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
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.map.*;
import org.bukkit.scheduler.BukkitTask;

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
    public static final Map<Player, String> activeDisguises = new HashMap<>();
    public static final Map<Player, Long> lastPlayerMoves = new HashMap<>();
    public static final Set<String> knownPlayers = new HashSet<>();
    public static int minSecBetweenAlerts;
    public static int claimWidth;
    private static boolean masterBookChangeMsgSent = false;
    public static boolean isPaperServer = true;
    public static long lastClaimUpdate = 0;
    public static int gracePeriod = 0;

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

    // Check if a block is a lectern with a claim book
    public static boolean isClaimBlock(Block b) {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        String cID = getClaimID(b.getLocation());
        double lecternX = claimData.getDouble(cID + ".location.X", Double.MAX_VALUE);
        double lecternY = claimData.getDouble(cID + ".location.Y", Double.MAX_VALUE);
        double lecternZ = claimData.getDouble(cID + ".location.Z", Double.MAX_VALUE);

        if(lecternX == Double.MAX_VALUE || lecternY == Double.MAX_VALUE || lecternZ == Double.MAX_VALUE)
            return false;

        return lecternX == b.getLocation().getX() && lecternY == b.getLocation().getY() && lecternZ == b.getLocation().getZ();
    }

    public static boolean claimChunk(Block block, List<String> members, Consumer<String> sendMessage) {
        // If it's a valid claim book
        if(members.size() > 0) {
            // If the lectern is next to bedrock: Cancel
            if(isNextToBedrock(block)){
                sendMessage.accept(chat("&cYou cannot place a claim next to bedrock"));
                return false;
            }

            setChunkClaim(block, members, sendMessage, null);
            updateClaimCount();
            plugin.cfg.saveClaimData();
            plugin.cfg.saveOfflineClaimNotifications();

        }else{
            sendMessage.accept(chat("&cHINT: Add \"claim\" at the top of the first page, followed by a list members, to claim this chunk!"));
        }

        return true;
    }

    public static void claimChunkBulk(Set<Block> blocks, BookMeta meta, String masterBookID) {
        if (meta != null) {
            List<String> members = findMembersInBook(meta);

            // If it's a valid claim book
            if(members.size() > 0) {
                for (Block block : blocks) {
                    // If the lectern is next to bedrock: Cancel
                    if (isNextToBedrock(block))
                        continue;

                    setChunkClaim(block, members, masterBookID);
                }
                updateClaimCount();
                plugin.cfg.saveClaimData();
                plugin.cfg.saveOfflineClaimNotifications();
            }
        }
    }

    private static void setChunkClaim(Block block, List<String> members, String masterBookID){
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

    public static void onChunkClaim(String claimID, List<String> members, @Nullable Consumer<String> sendMessage, String masterBookID){
        if(sendMessage == null)
            sendMessage = (msg) -> {};
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

            if(isOffline){
                String name = knownMember.toLowerCase();
                FileConfiguration offlineClaimNotifications = plugin.cfg.getOfflineClaimNotifications();

                if(masterBookID != null)
                    offlineClaimNotifications.set(name + ".masterbooks." + masterBookID, false);
                else
                    offlineClaimNotifications.set(name + ".chunks." + claimID, null);
            }
        }
        for (String unknownMember : doMembersExist.get(false)) {
            sendMessage.accept(ChatColor.GRAY + " - " + unknownMember + ChatColor.RED + " (unknown player)");
        }

        for (Player player : onlinePlayers)
            if (claimID.equals(getClaimID(player.getLocation())))
                if (isIntruder(player, claimID))
                    onIntruderEnterClaim(player, claimID);

        lastClaimUpdate = System.nanoTime();

        // plugin.pluginManager.callEvent(new ChunkClaimedEvent(doMembersExist.get(true)));
    }

    public static void onChunkUnclaim(String claimID, String[] members, Location lecternLoc, String masterBookID){
        String xyz = lecternLoc.getBlockX() +", "+ lecternLoc.getBlockY() +", "+ lecternLoc.getBlockZ();

        onChunkUnclaim(claimID, members, xyz, masterBookID);
    }

    public static void onChunkUnclaim(String claimID, String[] members, String lecternXYZ, String masterBookID){
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

        if(members != null) {
            for (String member : members) {
                if (knownPlayers.contains(member.toLowerCase())) {
                    boolean isOffline = true;

                    // Notify online members that they have lost the claim
                    for (Player player : onlinePlayers) {
                        if (player.getName().equalsIgnoreCase(member)) {
                            isOffline = false;
                            if(masterBookID != null) {
                                if(!masterBookChangeMsgSent) {
                                    String msg = "Your name has been removed from Master Book #" + masterBookID + " and all related claims!";
                                    player.sendMessage(ChatColor.RED + msg);
                                    masterBookChangeMsgSent = true;
                                }
                            }else {
                                String worldName = getWorldName(World.Environment.valueOf(claimID.split("\\|")[0]));
                                if(utils.showCoordsInMsgs(player)) {
                                    player.sendMessage(ChatColor.RED + "You have lost a claim! Location: " + lecternXYZ + " in " + worldName);
                                }else{
                                    player.sendMessage(ChatColor.RED + "You have lost a claim! Location: [hidden] in " + worldName);
                                }
                            }
                            break;
                        }
                    }

                    if (isOffline) {
                        String name = member.toLowerCase();
                        FileConfiguration offlineClaimNotifications = plugin.cfg.getOfflineClaimNotifications();

                        if(masterBookID != null) {
                            offlineClaimNotifications.set(name + ".masterbooks." + masterBookID, true);
                        }else {
                            offlineClaimNotifications.set(name + ".chunks." + claimID, lecternXYZ);
                        }
                    }
                }
            }
        }

        Map<Player, String> intrudersThatLeft = new HashMap<>();
        if(intruders.containsKey(claimID))
            for(Player intruder : intruders.get(claimID))
                intrudersThatLeft.put(intruder, claimID);

        for(Player intruder : intrudersThatLeft.keySet())
            onIntruderLeaveClaim(intruder, intrudersThatLeft.get(intruder));

        lastClaimUpdate = System.nanoTime();
    }

    public static List<String> findMembersInBook(BookMeta meta) {
        List<String> pages = meta.getPages();

        return findMembersInBook(pages);
    }

    public static List<String> findMembersInBook(List<String> pages){
        List<String> members = new ArrayList<>();

        for (String page : pages) {
            // If it isn't a claim page, stop looking for members
            if (!isClaimPage(page))
                break;

            String[] lines = page.split("\\n");

            for (int i = 1; i < lines.length; i++) {
                String member = lines[i].trim();

                // If the member name is valid
                if(!member.contains(" ") && !member.isEmpty() && !members.contains(member))
                    members.add(member);
            }
        }

        return members;
    }

    private static boolean isNextToBedrock(Block block) {
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

    public static void unclaimChunk(Block block, boolean causedByPlayer, Consumer<String> sendMessage) {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        Location blockLoc = block.getLocation();
        String claimID = getClaimID(blockLoc);
        String[] members = getMembers(claimID);

        // If it's a (copy of a) master book, remove it from the list of copies on lecterns
        if(block.getType() == Material.LECTERN){
            Lectern lectern = (Lectern) block.getState();
            ItemStack book = lectern.getInventory().getItem(0);

            if(book != null){
                BookMeta meta = (BookMeta) book.getItemMeta();

                if(meta != null){
                    List<String> lore = meta.getLore();

                    if(lore != null){
                        FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
                        String bookID = String.join("", lore).substring(17);

                        if(masterBooks.contains(bookID + ".copies-on-lecterns")) {
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

        for(Block b : blocks) {
            Location bLoc = b.getLocation();
            String claimID = getClaimID(bLoc);
            String[] membersBefore = getMembers(claimID);
            List<String> membersAfter = findMembersInBook(meta);
            String[] membersRemoved = null;

            if(membersBefore != null)
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

        for(Player p : Bukkit.getOnlinePlayers()) {
            Integer pClaims = membersNumClaims.get(p.getName().toLowerCase());

            if(pClaims == null) {
                p.setPlayerListName(p.getName() + chat(" - &c0"));
            }else {
                p.setPlayerListName(p.getName() + chat(" - &c" + pClaims));
                plugin.pluginManager.callEvent(new PlayerClaimsCountedEvent(p, pClaims));
            }
        }
    }

    // Returns a HashMap of player name (lowercase) and number of claims
    public static HashMap<String, Integer> countMemberClaims() {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        HashMap<String, Integer> count = new HashMap<>();

        for(String key : claimData.getKeys(false)){
            ConfigurationSection chunk = claimData.getConfigurationSection(key);

            if(chunk != null){
                String currentMembers = chunk.getString("members");
                if(currentMembers != null)
                    for (String cm : currentMembers.toLowerCase().split("\\n"))
                        count.merge(cm, 1, Integer::sum);
            }
        }

        return count;
    }

    public static void b4bCheck(Player p, Block b, BlockBreakEvent e, Boolean noloot, boolean requiresBlock) {
        if(requiresBlock) {
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

                if (!itemInInventory){
                    e.setCancelled(true);
                    p.sendMessage(chat("&aYou need &c" + requiredType + " &ain your hotbar to break this!"));
                    plugin.pluginManager.callEvent(new B4BlockBreakEvent(p, b, false));
                    return;
                }
            }

            plugin.pluginManager.callEvent(new B4BlockBreakEvent(p, b, true));
        }

        if(noloot){
            if(b.getState() instanceof BlockInventoryHolder bInv) {
                Inventory inv = bInv.getInventory();

                for (ItemStack item : inv.getStorageContents()) {
                    if(item != null) {
                        b.getWorld().dropItemNaturally(b.getLocation(), item);
                    }
                }
            }

            e.setDropItems(false);
        }
    }

    // Returns log2(n + 2)
    public static double calcGeneralChickenBonus(double numNamedChickens){
        // log2(x) = log(x) / log(2)
        return Math.log(numNamedChickens + 2) / Math.log(2);
    }

    public static ChickenBonuses calcChickenBonuses(Entity center) {
        int radius = plugin.getConfig().getInt("named-chicken-radius");
        List<Entity> nearbyEntities = center.getNearbyEntities(radius, radius, radius);
        Set<String> namedChickensPos = new HashSet<>();
        Map<Character, Integer> letterBonuses = new HashMap<>();

        for(Entity ne : nearbyEntities){
            if(ne.getType() == EntityType.CHICKEN){
                String chickenName = ne.getCustomName();

                if(chickenName != null) {
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

    public static Material getRandomSpawnEgg(Map<Character, Integer> letterBonuses){
        ConfigurationSection weightConfig = plugin.getConfig().getConfigurationSection("spawn-egg-weights");
        Random rand = new Random();
        int totalWeight = calcTotalWeight(letterBonuses);
        int i = rand.nextInt(totalWeight);

        if(weightConfig != null) {
            for (String eggName : weightConfig.getKeys(false)) {
                Character firstLetter = eggName.toLowerCase().charAt(0);
                Integer bonus = letterBonuses.get(firstLetter);
                int weight = weightConfig.getInt(eggName);

                if (bonus != null)
                    weight *= (1 + bonus);
                i -= weight;

                if (i <= 0)
                    return Material.valueOf(eggName);
            }
        }

        // We should never get this far
        return Material.TROPICAL_FISH_SPAWN_EGG;
    }

    private static int calcTotalWeight(Map<Character, Integer> letterBonuses){
        ConfigurationSection weightConfig = plugin.getConfig().getConfigurationSection("spawn-egg-weights");
        int totalWeight = 0;

        if(weightConfig != null) {
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

    public static void onIntruderEnterClaim(Player intruder, String claimID) {
        if(intruder.getGameMode() != GameMode.SURVIVAL)
            return;

        FileConfiguration claimData = plugin.cfg.getClaimData();
        double x = claimData.getDouble(claimID + ".location.X");
        double y = claimData.getDouble(claimID + ".location.Y");
        double z = claimData.getDouble(claimID + ".location.Z");

        boolean sendLecternMsgs = plugin.getConfig().getBoolean("lectern-message-settings.send-lectern-messages");

        if(sendLecternMsgs && claimWidth > 1) {
            String msg = "Claim lectern is ";

            if(!showCoordsInMsgs(intruder)) {
                msg = "You've intruded on a claim";
            }else{
                boolean showY = plugin.getConfig().getBoolean("lectern-message-settings.show-y");
                boolean showExactCoords = plugin.getConfig().getBoolean("lectern-message-settings.show-exact-coords");

                if (showExactCoords) {
                    if (showY) {
                        msg += "at " + Math.floor(x) + ", " + Math.floor(y) + ", " + Math.floor(z);
                    } else {
                        msg += "near " + Math.floor(x) + ", 0, " + Math.floor(z);
                    }
                } else {
                    double signX = (x == 0) ? 1 : Math.signum(x);
                    double signY = (y == 0) ? 1 : Math.signum(y);
                    double signZ = (z == 0) ? 1 : Math.signum(z);
                    int chunkCenterX = (int) (x - x % 16 + signX * 8);
                    int chunkCenterY = (int) (y - y % 16 + signY * 8);
                    int chunkCenterZ = (int) (z - z % 16 + signZ * 8);

                    msg += "in the chunk near ";
                    if (showY) {
                        msg += chunkCenterX + ", " + chunkCenterY + ", " + chunkCenterZ;
                    } else {
                        msg += chunkCenterX + ", 0, " + chunkCenterZ;
                    }
                }
            }

            intruder.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
        }

        if(!intruders.containsKey(claimID))
            intruders.put(claimID, new HashSet<>());

        intruders.get(claimID).add(intruder);

        String[] members = getMembers(claimID);

        if(members != null) {
            for (String m : members) {
                Player p = Bukkit.getPlayerExact(m);

                if (p != null) {
                    long now = System.nanoTime();

                    if (!playerClaimsIntruded.containsKey(p))
                        playerClaimsIntruded.put(p, new HashSet<>());
                    playerClaimsIntruded.get(p).add(claimID);

                    if(now - lastIntrusionMsgReceived.getOrDefault(p, 0L) >= minSecBetweenAlerts * 1e9){
                        String worldName = getWorldName(World.Environment.valueOf(claimID.split("\\|")[0]));
                        if(showCoordsInMsgs(p)) {
                            p.sendMessage(ChatColor.RED +
                                    "An intruder has entered your claim at " + x + ", " + y + ", " + z + " in " + worldName);
                        }else{
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

    public static boolean showCoordsInMsgs(Player p){
        String playerSetting = plugin.cfg.getCoordsSettings().getString(p.getUniqueId().toString());

        return playerSetting == null || playerSetting.equals("on");
    }

    public static void onIntruderLeaveClaim(Player intruder, String claimID) {
        if(intruders.containsKey(claimID)) {
            intruders.get(claimID).remove(intruder);

            if (intruders.get(claimID).size() == 0)
                intruders.remove(claimID);
        }
    }

    public static boolean isIntruder(Player p, String claimID){
        String[] members = getMembers(claimID);

        // If the chunk isn't claimed or p is a member
        if (members == null || isMemberOfClaim(members, p))
            return false;

        return true;
    }

    public static void updateGolemHostility(){
        for(Map.Entry<IronGolem, String> entry : ironGolems.entrySet()){
            IronGolem golem = entry.getKey();
            String currentChunkID = getChunkID(golem.getLocation());
            String prevChunkID = entry.getValue();

            // If the golem has entered a new chunk
            if(!currentChunkID.equals(prevChunkID)){
                entry.setValue(currentChunkID);
                String claimID = getClaimID(golem.getLocation());

                // Make it hostile to all intruders in the chunk it just entered
                if(intruders.containsKey(claimID))
                    for(Player intruder : intruders.get(claimID))
                        if(currentChunkID.equals(getChunkID(intruder.getLocation())))
                            golem.damage(0, intruder);
            }
        }
    }

    public static void populatePlayerClaimsIntruded(Player p){
        // Go through all intruded claims
        for(String claimID : intruders.keySet()){
            String[] members = getMembers(claimID);

            if(members != null) {
                if(isMemberOfClaim(members, p, false)) {
                    if (!playerClaimsIntruded.containsKey(p))
                        playerClaimsIntruded.put(p, new HashSet<>());

                    // Add the chunk as one of p's intruded claims
                    playerClaimsIntruded.get(p).add(claimID);
                }
            }
        }
    }

    public static boolean isMemberOfClaim(String[] members, OfflinePlayer p) {
        if(members != null && p != null){
            return isMemberOfClaim(members, p, true);
        }
        return false;
    }

    public static boolean isMemberOfClaim(String[] members, OfflinePlayer p, boolean allowDisguise) {
        if(members != null && p != null)
            for (String member : members)
                if (member.equalsIgnoreCase(p.getName()) || (allowDisguise && member.equalsIgnoreCase(activeDisguises.get(p))))
                    return true;

        return false;
    }

    public static void disguisePlayer(Player disguiser, OfflinePlayer disguisee) {
        Collection<Property> textures = getCachedTextures(disguisee);
        disguisePlayer(disguiser, textures);
    }

    public static void disguisePlayer(Player disguiser, Collection<Property> textures) {
        setTextures(disguiser, textures);
        updateTexturesForOthers(disguiser);
        updateTexturesForSelf(disguiser);
    }

    public static Collection<Property> getTextures(OfflinePlayer p){
        if(plugin.canUseReflection) {
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

    public static Collection<Property> getCachedTextures(OfflinePlayer p){
        List<String> strs = plugin.cfg.getPlayerTextures().getStringList(p.getUniqueId().toString());
        Collection<Property> textures = new ArrayList<>();

        if(strs.size() == 3)
            textures.add(new Property(strs.get(0), strs.get(1), strs.get(2)));
        else
            textures.add(new Property(strs.get(0), strs.get(1)));

        return textures;
    }

    public static void setTextures(Player p, Collection<Property> textures){
        if(plugin.canUseReflection) {
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

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                vehicle.addPassenger(disguiser);
            }, 1);
        }

        if(plugin.canUseReflection) {
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
        disguisePlayer(p, getCachedTextures(p));
    }

    public static void onLoseDisguise(Player disguiser) {
        if (activeDisguises.containsKey(disguiser)) {
            activeDisguises.remove(disguiser);
            disguiser.sendMessage("Your disguise has expired!");

            if (undisguiseTasks.containsKey(disguiser)) {
                undisguiseTasks.get(disguiser).cancel();
                undisguiseTasks.remove(disguiser);
            }else {
                String claimID = getClaimID(disguiser.getLocation());
                if (isIntruder(disguiser, claimID))
                    onIntruderEnterClaim(disguiser, claimID);
            }
        }
    }

    public static void replaceInClaimPages(List<String> pages, String search, String replace) {
        for(int i = 0; i < pages.size(); i++){
            String page = pages.get(i);

            if (!isClaimPage(page))
                break;

            String[] membersArray = page.split("\\n");

            for (int j = 1; j < membersArray.length; j++)
                if (membersArray[j].equalsIgnoreCase(search))
                    membersArray[j] = replace;

            pages.set(i, String.join("\n", membersArray));
        }
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

                if(blocksPerPixel == 0)
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
                }else if(lastPlayerMoves.containsKey(p) && lastUpdate < lastPlayerMoves.get(p)){
                    int px = 2 * (p.getLocation().getBlockX() - centerX) / blocksPerPixel;
                    int pz = 2 * (p.getLocation().getBlockZ() - centerZ) / blocksPerPixel;

                    if(px <= 127 && px >= -128 && pz <= 127 && pz >= -128) {
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

        for(int i = 0; i < 128; i += 16 / blocksPerPixel) {
            int z = centerZ - 64 * blocksPerPixel;

            for(int j = 0; j < 128; j += 16 / blocksPerPixel) {
                String chunkID = getChunkID(x, z, env);
                String claimID = getClaimID(x, z, env);
                FileConfiguration claimData = plugin.cfg.getClaimData();

                if(claimData.contains(claimID))
                    claims.put(chunkID, new Coords2D(i, j));

                z += 16;
            }

            x += 16;
        }

        return claims;
    }

    private static void addClaimsToCanvas(MapCanvas canvas, Map<String, Coords2D> claimsBefore, Map<String, Coords2D> claimsNow, OfflinePlayer p, int blocksPerPixel){
        if(claimsBefore != null) {
            claimsBefore.values().removeAll(claimsNow.values());

            for (String chunkID : claimsBefore.keySet()) {
                Coords2D ij = claimsBefore.get(chunkID);
                int i = ij.x;
                int j = ij.z;

                for (int k = 0; k < 16 / blocksPerPixel; k++)
                    for (int l = 0; l < 16 / blocksPerPixel; l++)
                        canvas.setPixel(i + k, j + l, canvas.getBasePixel(i + k, j + l));
            }
        }

        for(String chunkID : claimsNow.keySet()) {
            Coords2D ij = claimsNow.get(chunkID);
            int i = ij.x;
            int j = ij.z;

            String[] members = getMembers(getClaimID(chunkID));
            boolean isMember = members != null && isMemberOfClaim(members, p);
            String configStr = isMember ? "my-claims" : "others-claims";
            int r = plugin.getConfig().getInt("claim-map-colors."+configStr+".r");
            int g = plugin.getConfig().getInt("claim-map-colors."+configStr+".g");
            int b = plugin.getConfig().getInt("claim-map-colors."+configStr+".b");
            byte color = MapPalette.matchColor(r, g, b);

            for (int k = 0; k < 16 / blocksPerPixel; k++)
                for (int l = 0; l < 16 / blocksPerPixel; l++)
                    if (canvas.getBasePixel(i + k, j + l) != MapPalette.TRANSPARENT)
                        canvas.setPixel(i + k, j + l, color);
        }
    }

    public static MapRenderer createIntruderRenderer(OfflinePlayer creator) {
        return new MapRenderer() {
            final OfflinePlayer owner = creator;
            int blocksPerPixel = 0;

            public void render(MapView view, MapCanvas canvas, Player p) {
                if(blocksPerPixel == 0)
                    blocksPerPixel = getBlocksPerPixel(view.getScale());

                if (intruders.size() > 0){
                    int centerX = view.getCenterX();
                    int centerZ = view.getCenterZ();

                    addIntrudersToCanvas(canvas, centerX, centerZ, blocksPerPixel, owner);
                }else{
                    canvas.setCursors(new MapCursorCollection());
                }
            }
        };
    }

    private static void addIntrudersToCanvas(MapCanvas canvas, int centerX, int centerZ, int blocksPerPixel, OfflinePlayer p) {
        MapCursorCollection cursors = new MapCursorCollection();

        for(String claimID : intruders.keySet()) {
            FileConfiguration claimData = plugin.cfg.getClaimData();

            if (claimData.contains(claimID)) {
                String[] members = getMembers(claimID);
                boolean isMember = members != null && isMemberOfClaim(members, p);

                if (isMember) {
                    for (Player intruder : intruders.get(claimID)) {
                        int px = 2 * (intruder.getLocation().getBlockX() - centerX) / blocksPerPixel;
                        int pz = 2 * (intruder.getLocation().getBlockZ() - centerZ) / blocksPerPixel;

                        if (px <= 127 && px >= -128 && pz <= 127 && pz >= -128) {
                            if(canvas.getBasePixel((px + 128) / 2, (pz + 128) / 2) != MapPalette.TRANSPARENT) {
                                double yaw = intruder.getLocation().getYaw();
                                byte direction = (byte) Math.min(15, Math.max(0, (((yaw + 371.25) % 360) / 22.5)));
                                byte mx = (byte) px;
                                byte mz = (byte) pz;
                                MapCursor.Type type = MapCursor.Type.RED_POINTER;
                                String caption = intruder.getName();

                                MapCursor cursor = new MapCursor(mx, mz, direction, type, true, caption);
                                cursors.addCursor(cursor);
                            }
                        }
                    }
                }
            }
        }

        canvas.setCursors(cursors);
    }

    public static String getWorldName(World.Environment env){
        return switch (env) {
            case NORMAL -> "Overworld";
            case NETHER -> "The Nether";
            case THE_END -> "The End";
            default -> "Unknown World";
        };
    }

    public static void removeExpiredB4BGracePeriods() {
        Set<Block> expiredGracePeriods = new HashSet<>();

        // Grace periods count as expired if x seconds have passed or the block's material has changed
        for(Map.Entry<Block, GracePeriod> entry : b4bGracePeriods.entrySet())
            if (System.nanoTime() - entry.getValue().timestamp >= gracePeriod * 1e9
                    || !entry.getValue().type.equals(entry.getKey().getType()))
                expiredGracePeriods.add(entry.getKey());

        for(Block expired : expiredGracePeriods)
            b4bGracePeriods.remove(expired);
    }

    public static void removeExpiredBlockChangeGracePeriods() {
        Set<Location> expiredGracePeriods = new HashSet<>();

        // Grace periods count as expired if x seconds have passed or the block's material has changed
        for(Map.Entry<Location, GracePeriod> entry : blockChangeGracePeriods.entrySet())
            if (System.nanoTime() - entry.getValue().timestamp >= gracePeriod * 1e9)
                expiredGracePeriods.add(entry.getKey());

        for(Location expired : expiredGracePeriods)
            blockChangeGracePeriods.remove(expired);
    }

    public static void sendWelcomeMsg(Player player) {
        List<String> welcomeMessages = plugin.getConfig().getStringList("welcome-messages");

        for (String msg : welcomeMessages) {
            player.sendMessage(utils.chat(msg));
        }

        plugin.pluginManager.callEvent(new WelcomeMsgSentEvent(player));
    }
}
