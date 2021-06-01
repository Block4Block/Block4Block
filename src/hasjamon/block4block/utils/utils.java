package hasjamon.block4block.utils;

import hasjamon.block4block.Block4Block;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import java.util.*;

public class utils {
    private static final Block4Block plugin = Block4Block.getInstance();
    private static final Map<Material, Material> specialTypes = new HashMap<>();
    private static final Map<Material, Integer> spawnEggs = new HashMap<>();
    public static final Map<Block, Long> b4bGracePeriods = new LinkedHashMap<>();
    public static final Map<String, Set<Player>> intruders = new HashMap<>();
    public static final Map<IronGolem, String> ironGolems = new HashMap<>();
    public static final Map<Player, Set<String>> playerClaimsIntruded = new HashMap<>();
    public static final Map<Player, Long> lastIntrusionMessageReceived = new HashMap<>();

    static {
        specialTypes.put(Material.REDSTONE_WIRE, Material.REDSTONE);
        specialTypes.put(Material.WALL_TORCH, Material.TORCH);
        specialTypes.put(Material.REDSTONE_WALL_TORCH, Material.REDSTONE_TORCH);
        specialTypes.put(Material.TALL_SEAGRASS, Material.SEAGRASS);
        specialTypes.put(Material.CHORUS_PLANT, Material.CHORUS_FRUIT);
        specialTypes.put(Material.KELP_PLANT, Material.KELP);
        specialTypes.put(Material.BAMBOO_SAPLING, Material.BAMBOO);
        specialTypes.put(Material.WARPED_WALL_SIGN, Material.WARPED_SIGN);
        specialTypes.put(Material.ACACIA_WALL_SIGN, Material.ACACIA_SIGN);
        specialTypes.put(Material.BIRCH_WALL_SIGN, Material.BIRCH_SIGN);
        specialTypes.put(Material.CRIMSON_WALL_SIGN, Material.CRIMSON_SIGN);
        specialTypes.put(Material.JUNGLE_WALL_SIGN, Material.JUNGLE_SIGN);
        specialTypes.put(Material.DARK_OAK_WALL_SIGN, Material.DARK_OAK_SIGN);
        specialTypes.put(Material.OAK_WALL_SIGN, Material.OAK_SIGN);
        specialTypes.put(Material.SPRUCE_WALL_SIGN, Material.SPRUCE_SIGN);
        specialTypes.put(Material.SKELETON_WALL_SKULL, Material.SKELETON_SKULL);
        specialTypes.put(Material.WITHER_SKELETON_WALL_SKULL, Material.WITHER_SKELETON_SKULL);
        specialTypes.put(Material.ZOMBIE_WALL_HEAD, Material.ZOMBIE_HEAD);
        specialTypes.put(Material.PLAYER_WALL_HEAD, Material.PLAYER_HEAD);
        specialTypes.put(Material.CREEPER_WALL_HEAD, Material.CREEPER_HEAD);
        specialTypes.put(Material.DRAGON_WALL_HEAD, Material.DRAGON_HEAD);
        specialTypes.put(Material.BRAIN_CORAL_WALL_FAN, Material.BRAIN_CORAL_FAN);
        specialTypes.put(Material.BUBBLE_CORAL_WALL_FAN, Material.BUBBLE_CORAL_FAN);
        specialTypes.put(Material.DEAD_BRAIN_CORAL_WALL_FAN, Material.DEAD_BRAIN_CORAL_FAN);
        specialTypes.put(Material.DEAD_BUBBLE_CORAL_WALL_FAN, Material.DEAD_BUBBLE_CORAL_FAN);
        specialTypes.put(Material.DEAD_FIRE_CORAL_WALL_FAN, Material.DEAD_FIRE_CORAL_FAN);
        specialTypes.put(Material.DEAD_HORN_CORAL_WALL_FAN, Material.DEAD_HORN_CORAL_FAN);
        specialTypes.put(Material.DEAD_TUBE_CORAL_WALL_FAN, Material.DEAD_TUBE_CORAL_FAN);
        specialTypes.put(Material.FIRE_CORAL_WALL_FAN, Material.FIRE_CORAL_FAN);
        specialTypes.put(Material.HORN_CORAL_WALL_FAN, Material.HORN_CORAL_FAN);
        specialTypes.put(Material.TUBE_CORAL_WALL_FAN, Material.TUBE_CORAL_FAN);
        specialTypes.put(Material.BLACK_WALL_BANNER, Material.BLACK_BANNER);
        specialTypes.put(Material.BLUE_WALL_BANNER, Material.BLUE_BANNER);
        specialTypes.put(Material.BROWN_WALL_BANNER, Material.BROWN_BANNER);
        specialTypes.put(Material.CYAN_WALL_BANNER, Material.CYAN_BANNER);
        specialTypes.put(Material.GRAY_WALL_BANNER, Material.GRAY_BANNER);
        specialTypes.put(Material.GREEN_WALL_BANNER, Material.GREEN_BANNER);
        specialTypes.put(Material.LIGHT_BLUE_WALL_BANNER, Material.LIGHT_BLUE_BANNER);
        specialTypes.put(Material.LIGHT_GRAY_WALL_BANNER, Material.LIGHT_GRAY_BANNER);
        specialTypes.put(Material.LIME_WALL_BANNER, Material.LIME_BANNER);
        specialTypes.put(Material.MAGENTA_WALL_BANNER, Material.MAGENTA_BANNER);
        specialTypes.put(Material.ORANGE_WALL_BANNER, Material.ORANGE_BANNER);
        specialTypes.put(Material.PINK_WALL_BANNER, Material.PINK_BANNER);
        specialTypes.put(Material.PURPLE_WALL_BANNER, Material.PURPLE_BANNER);
        specialTypes.put(Material.RED_WALL_BANNER, Material.RED_BANNER);
        specialTypes.put(Material.WHITE_WALL_BANNER, Material.WHITE_BANNER);
        specialTypes.put(Material.YELLOW_WALL_BANNER, Material.YELLOW_BANNER);
        specialTypes.put(Material.TRAPPED_CHEST, Material.CHEST);
        specialTypes.put(Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES);
        specialTypes.put(Material.INFESTED_STONE, Material.STONE);
        specialTypes.put(Material.INFESTED_COBBLESTONE, Material.COBBLESTONE);
        specialTypes.put(Material.INFESTED_STONE_BRICKS, Material.STONE_BRICKS);
        specialTypes.put(Material.INFESTED_CRACKED_STONE_BRICKS, Material.CRACKED_STONE_BRICKS);
        specialTypes.put(Material.INFESTED_MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICKS);
        specialTypes.put(Material.INFESTED_CHISELED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS);

        spawnEggs.put(Material.TROPICAL_FISH_SPAWN_EGG, 250);
        spawnEggs.put(Material.COW_SPAWN_EGG, 150);
        spawnEggs.put(Material.SHEEP_SPAWN_EGG, 150);
        spawnEggs.put(Material.PIG_SPAWN_EGG, 150);
        spawnEggs.put(Material.HORSE_SPAWN_EGG, 100);
        spawnEggs.put(Material.COD_SPAWN_EGG, 100);
        spawnEggs.put(Material.SALMON_SPAWN_EGG, 100);
        spawnEggs.put(Material.DONKEY_SPAWN_EGG, 100);
        spawnEggs.put(Material.RABBIT_SPAWN_EGG, 100);
        spawnEggs.put(Material.WOLF_SPAWN_EGG, 50);
        spawnEggs.put(Material.BAT_SPAWN_EGG, 50);
        spawnEggs.put(Material.SQUID_SPAWN_EGG, 50);
        spawnEggs.put(Material.BEE_SPAWN_EGG, 50);
        spawnEggs.put(Material.PARROT_SPAWN_EGG, 50);
        spawnEggs.put(Material.LLAMA_SPAWN_EGG, 50);
        spawnEggs.put(Material.CAT_SPAWN_EGG, 50);
        spawnEggs.put(Material.FOX_SPAWN_EGG, 50);
        spawnEggs.put(Material.MULE_SPAWN_EGG, 50);
        spawnEggs.put(Material.TURTLE_SPAWN_EGG, 50);
        spawnEggs.put(Material.PANDA_SPAWN_EGG, 50);
        spawnEggs.put(Material.POLAR_BEAR_SPAWN_EGG, 50);
        spawnEggs.put(Material.DOLPHIN_SPAWN_EGG, 50);
        spawnEggs.put(Material.OCELOT_SPAWN_EGG, 35);
        spawnEggs.put(Material.PUFFERFISH_SPAWN_EGG, 35);
        spawnEggs.put(Material.TRADER_LLAMA_SPAWN_EGG, 35);
        spawnEggs.put(Material.ZOMBIE_SPAWN_EGG, 20);
        spawnEggs.put(Material.SKELETON_SPAWN_EGG, 20);
        spawnEggs.put(Material.SPIDER_SPAWN_EGG, 20);
        spawnEggs.put(Material.CAVE_SPIDER_SPAWN_EGG, 20);
        spawnEggs.put(Material.CREEPER_SPAWN_EGG, 20);
        spawnEggs.put(Material.DROWNED_SPAWN_EGG, 20);
        spawnEggs.put(Material.HUSK_SPAWN_EGG, 20);
        spawnEggs.put(Material.PHANTOM_SPAWN_EGG, 20);
        spawnEggs.put(Material.SILVERFISH_SPAWN_EGG, 20);
        spawnEggs.put(Material.ENDERMITE_SPAWN_EGG, 20);
        spawnEggs.put(Material.PILLAGER_SPAWN_EGG, 20);
        spawnEggs.put(Material.STRIDER_SPAWN_EGG, 15);
        spawnEggs.put(Material.SLIME_SPAWN_EGG, 15);
        spawnEggs.put(Material.WANDERING_TRADER_SPAWN_EGG, 15);
        spawnEggs.put(Material.ZOMBIFIED_PIGLIN_SPAWN_EGG, 10);
        spawnEggs.put(Material.VEX_SPAWN_EGG, 10);
        spawnEggs.put(Material.WITCH_SPAWN_EGG, 10);
        spawnEggs.put(Material.VINDICATOR_SPAWN_EGG, 10);
        spawnEggs.put(Material.STRAY_SPAWN_EGG, 10);
        spawnEggs.put(Material.GUARDIAN_SPAWN_EGG, 10);
        spawnEggs.put(Material.ENDERMAN_SPAWN_EGG, 10);
        spawnEggs.put(Material.PIGLIN_SPAWN_EGG, 10);
        spawnEggs.put(Material.PIGLIN_BRUTE_SPAWN_EGG, 10);
        spawnEggs.put(Material.ZOGLIN_SPAWN_EGG, 10);
        spawnEggs.put(Material.HOGLIN_SPAWN_EGG, 10);
        spawnEggs.put(Material.MAGMA_CUBE_SPAWN_EGG, 10);
        spawnEggs.put(Material.BLAZE_SPAWN_EGG, 7);
        spawnEggs.put(Material.RAVAGER_SPAWN_EGG, 7);
        spawnEggs.put(Material.WITHER_SKELETON_SPAWN_EGG, 5);
        spawnEggs.put(Material.SHULKER_SPAWN_EGG, 5);
        spawnEggs.put(Material.EVOKER_SPAWN_EGG, 5);
        spawnEggs.put(Material.SKELETON_HORSE_SPAWN_EGG, 5);
        spawnEggs.put(Material.VILLAGER_SPAWN_EGG, 5);
        spawnEggs.put(Material.ZOMBIE_VILLAGER_SPAWN_EGG, 5);
        spawnEggs.put(Material.MOOSHROOM_SPAWN_EGG, 3);
        spawnEggs.put(Material.ELDER_GUARDIAN_SPAWN_EGG, 3);
        spawnEggs.put(Material.GHAST_SPAWN_EGG, 3);
        spawnEggs.put(Material.CHICKEN_SPAWN_EGG, 1);
        spawnEggs.put(Material.ZOMBIE_HORSE_SPAWN_EGG, 1);
    }

    public static String chat(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String getChunkID(int blockX, int blockZ, World.Environment environment) {
        return environment.name() + "|" + (blockX >> 4) + "," + (blockZ >> 4);
    }

    public static String getChunkID(Location loc) {
        return getChunkID(loc.getBlockX(), loc.getBlockZ(), loc.getWorld().getEnvironment());
    }

    public static String[] getMembers(Location loc) {
        return getMembers(getChunkID(loc));
    }

    public static String[] getMembers(String chunkID) {
        String members = plugin.cfg.getClaimData().getString(chunkID + ".members");

        if (members != null)
            return members.split("\\n");
        else
            return null;
    }

    // Check if a block is a lectern with a claim book
    public static boolean isClaimBlock(Block b) {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        String cID = getChunkID(b.getLocation());

        if (claimData.get(cID + ".location.X").equals(b.getLocation().getX()))
            if (claimData.get(cID + ".location.Y").equals(b.getLocation().getY()))
                if (claimData.get(cID + ".location.Z").equals(b.getLocation().getZ()))
                    return true;
        return false;
    }

    public static boolean claimChunk(Block block, Player p, ItemStack book) {
        BookMeta bookmeta = (BookMeta) book.getItemMeta();

        if (bookmeta != null) {
            List<String> pages = bookmeta.getPages();
            List<String> members = new ArrayList<>();

            // Collect a list of members
            for (String page : pages) {
                // If it isn't a claim page, stop looking for members
                if (!page.substring(0, 5).equalsIgnoreCase("claim"))
                    break;

                String[] lines = page.split("\\n");

                for (int i = 1; i < lines.length; i++) {
                    String member = lines[i].trim();

                    // If the member name is valid
                    if(!member.contains(" ") && !member.isEmpty() && !members.contains(member))
                        members.add(member);
                }
            }

            // If it's a valid claim book
            if(members.size() > 0) {
                // If the lectern is next to bedrock: Cancel
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (block.getRelative(x, y, z).getType() == Material.BEDROCK) {
                                p.sendMessage(utils.chat("&cYou cannot place a claim next to bedrock"));
                                return false;
                            }
                        }
                    }
                }

                FileConfiguration claimData = plugin.cfg.getClaimData();
                Location blockLoc = block.getLocation();
                String chunkID = utils.getChunkID(blockLoc);
                String membersString = String.join("\n", members);

                claimData.set(chunkID + ".location.X", blockLoc.getX());
                claimData.set(chunkID + ".location.Y", blockLoc.getY());
                claimData.set(chunkID + ".location.Z", blockLoc.getZ());
                claimData.set(chunkID + ".members", membersString);
                plugin.cfg.saveClaimData(); // Save members to claimdata.yml

                OfflinePlayer[] knownPlayers = Bukkit.getServer().getOfflinePlayers();

                // Inform the player of the claim and its members
                p.sendMessage(utils.chat("&aThis chunk has now been claimed!"));
                p.sendMessage(utils.chat("&aMembers who can access this chunk:"));
                for (String member : members)
                    if(Arrays.stream(knownPlayers).anyMatch(kp -> kp.getName() != null && kp.getName().equalsIgnoreCase(member)))
                        p.sendMessage(ChatColor.GRAY + " - " + member);
                    else
                        p.sendMessage(ChatColor.GRAY + " - " + member + ChatColor.RED + " (unknown player)");

                updateClaimCount();

                for(Player player : Bukkit.getOnlinePlayers())
                    if(chunkID.equals(getChunkID(player.getLocation())))
                        if(isIntruder(player, chunkID))
                            onIntruderEnterClaim(player, chunkID);

            }else{
                p.sendMessage(utils.chat("&cHINT: Add \"claim\" at the top of the first page, followed by a list members, to claim this chunk!"));
            }
        }

        return true;
    }

    public static void unclaimChunk(Player p, Block block, Boolean wasExploded) {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        String chunkID = utils.getChunkID(block.getLocation());

        claimData.set(chunkID, null);
        plugin.cfg.saveClaimData();

        if (!wasExploded)
            p.sendMessage(utils.chat("&aYou have removed this claim!"));

        updateClaimCount();

        if(intruders.containsKey(chunkID))
            for(Player intruder : intruders.get(chunkID))
                onIntruderLeaveClaim(intruder, chunkID);
    }

    // Update tablist with current number of claims for each player
    public static void updateClaimCount() {
        HashMap<String, Integer> membersNumClaims = countMemberClaims();

        for(Player p : Bukkit.getOnlinePlayers()) {
            Integer pClaims = membersNumClaims.get(p.getName().toLowerCase());

            if(pClaims == null)
                p.setPlayerListName(p.getName() + utils.chat(" - &c0"));
            else
                p.setPlayerListName(p.getName() + utils.chat(" - &c" + pClaims));
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

            if (specialTypes.containsKey(requiredType))
                requiredType = specialTypes.get(requiredType);

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
                    p.sendMessage(utils.chat("&aYou need &c" + requiredType + " &ain your quick-slots to break this!"));
                    return;
                }
            }
        }

        if(noloot)
            e.setDropItems(false);
    }

    public static Material getRandomSpawnEgg(Map<Character, Integer> letterBonuses){
        Random rand = new Random();
        int totalWeight = calcTotalWeight(letterBonuses);
        int i = rand.nextInt(totalWeight);

        for(Map.Entry<Material, Integer> egg : spawnEggs.entrySet()){
            Character firstLetter = egg.getKey().name().toLowerCase().charAt(0);
            Integer bonus = letterBonuses.get(firstLetter);
            int weight = egg.getValue();

            if(bonus != null)
                weight *= (1 + bonus);
            i -= weight;

            if(i <= 0)
                return egg.getKey();
        }

        // We should never get this far
        return Material.TROPICAL_FISH_SPAWN_EGG;
    }

    private static int calcTotalWeight(Map<Character, Integer> letterBonuses){
        int totalWeight = 0;

        for(Map.Entry<Material, Integer> egg : spawnEggs.entrySet()){
            Character firstLetter = egg.getKey().name().toLowerCase().charAt(0);
            Integer bonus = letterBonuses.get(firstLetter);
            Integer weight = egg.getValue();

            if(bonus != null)
                weight *= (1 + bonus);
            totalWeight += weight;
        }

        return totalWeight;
    }

    public static void onIntruderEnterClaim(Player intruder, String chunkID) {
        if(!intruders.containsKey(chunkID))
            intruders.put(chunkID, new HashSet<>());

        intruders.get(chunkID).add(intruder);

        // Make all iron golems in chunk hostile to the intruder
        for(IronGolem golem : ironGolems.keySet())
            if(chunkID.equals(getChunkID(golem.getLocation())))
                golem.damage(0, intruder);

        String[] members = getMembers(chunkID);

        if(members != null) {
            for (String m : members) {
                Player p = Bukkit.getPlayerExact(m);

                if (p != null) {
                    FileConfiguration claimData = plugin.cfg.getClaimData();
                    long now = System.nanoTime();
                    double x = claimData.getDouble(chunkID + ".location.X");
                    double y = claimData.getDouble(chunkID + ".location.Y");
                    double z = claimData.getDouble(chunkID + ".location.Z");

                    if (!playerClaimsIntruded.containsKey(p))
                        playerClaimsIntruded.put(p, new HashSet<>());
                    playerClaimsIntruded.get(p).add(chunkID);

                    if(now - lastIntrusionMessageReceived.getOrDefault(p, now) > 6e10){
                        p.sendMessage(ChatColor.RED + "An intruder has entered your claim at "+x+", "+y+", "+z);
                        lastIntrusionMessageReceived.put(p, now);
                    }
                }
            }
        }
    }

    public static void onIntruderLeaveClaim(Player intruder, String chunkID) {
        if(intruders.containsKey(chunkID))
            intruders.get(chunkID).remove(intruder);
    }

    public static boolean isIntruder(Player p, String chunkID){
        String[] members = utils.getMembers(chunkID);

        // If the chunk isn't claimed; else if p is a member
        if (members == null)
            return false;
        else
            for (String member : members)
                if (member.equalsIgnoreCase(p.getName()))
                    return false;

        return true;
    }

    public static void updateGolemHostility(){
        for(Map.Entry<IronGolem, String> entry : ironGolems.entrySet()){
            IronGolem golem = entry.getKey();
            String currentChunkID = getChunkID(golem.getLocation());
            String prevChunkID = entry.getValue();

            if(currentChunkID.equals(prevChunkID)){
                entry.setValue(currentChunkID);

                // Make it hostile to all intruders in chunk
                if(utils.intruders.containsKey(currentChunkID))
                    for(Player intruder : utils.intruders.get(currentChunkID))
                        golem.damage(0, intruder);
            }
        }
    }

    public static void populatePlayerClaimsIntruded(Player p){
        // Go through all intruded claims
        for(String chunkID : intruders.keySet()){
            String[] members = getMembers(chunkID);

            if(members != null) {
                for (String m : members) {
                    // If p is a member
                    if (m.equalsIgnoreCase(p.getName())) {
                        if (!playerClaimsIntruded.containsKey(p))
                            playerClaimsIntruded.put(p, new HashSet<>());

                        // Add the chunk as one of p's intruded claims
                        playerClaimsIntruded.get(p).add(chunkID);
                        break;
                    }
                }
            }
        }
    }
}
