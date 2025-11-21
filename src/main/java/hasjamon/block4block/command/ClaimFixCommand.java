package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ClaimFixCommand implements CommandExecutor {
    private final Block4Block plugin;

    public ClaimFixCommand(Block4Block plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        // === NEW: Debug command ===
        if (args.length > 0 && args[0].equalsIgnoreCase("debug")) {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "No permission!");
                return true;
            }

            FileConfiguration claimData = plugin.cfg.getClaimData();
            sender.sendMessage(ChatColor.GOLD + "=== Claim Data Debug ===");

            Set<String> allClaimIDs = claimData.getKeys(false);
            int validClaimCount = 0;
            int corruptedClaimCount = 0;

            for (String claimId : allClaimIDs) {
                String members = claimData.getString(claimId + ".members");
                sender.sendMessage(ChatColor.YELLOW + "Claim: " + ChatColor.WHITE + claimId);

                if (members != null && !members.isEmpty()) {
                    String[] memberArray = members.split("\\n");
                    sender.sendMessage(ChatColor.GRAY + "  Members (" + memberArray.length + "):");
                    for (String member : memberArray) {
                        String trimmed = member.trim();
                        if (!trimmed.isEmpty()) {
                            sender.sendMessage(ChatColor.GRAY + "    - " + trimmed);
                        }
                    }
                    validClaimCount++;
                } else {
                    sender.sendMessage(ChatColor.RED + "  NO MEMBERS! (Corrupted)");
                    corruptedClaimCount++;
                }

                // Show location info
                double x = claimData.getDouble(claimId + ".location.X");
                double y = claimData.getDouble(claimId + ".location.Y");
                double z = claimData.getDouble(claimId + ".location.Z");
                sender.sendMessage(ChatColor.GRAY + "  Location: " + x + ", " + y + ", " + z);
            }

            sender.sendMessage(ChatColor.GOLD + "=== Summary ===");
            sender.sendMessage(ChatColor.GREEN + "Valid claims: " + validClaimCount);
            if (corruptedClaimCount > 0) {
                sender.sendMessage(ChatColor.RED + "Corrupted claims: " + corruptedClaimCount);
            }
            sender.sendMessage(ChatColor.YELLOW + "Total: " + allClaimIDs.size() + " claims");

            sender.sendMessage(ChatColor.GREEN + "Forcing save and reload...");
            plugin.cfg.saveClaimData();
            plugin.cfg.reloadClaimData();
            sender.sendMessage(ChatColor.GREEN + "Done!");

            return true;
        }

        // === Original claimfix confirm command ===
        if(args.length > 0 && args[0].equalsIgnoreCase("confirm")){
            FileConfiguration claimData = plugin.cfg.getClaimData();
            FileConfiguration masterBooks = plugin.cfg.getMasterBooks();

            if(plugin.cfg.backupClaimData() && plugin.cfg.backupOfflineClaimNotifications() && plugin.cfg.backupMasterBooks()) {
                Set<String> allClaimIDs = claimData.getKeys(false);

                for (String cID : allClaimIDs) {
                    double x = claimData.getDouble(cID + ".location.X");
                    double y = claimData.getDouble(cID + ".location.Y");
                    double z = claimData.getDouble(cID + ".location.Z");
                    String envName = cID.split("\\|")[0];

                    for(World world : Bukkit.getWorlds()){
                        World.Environment env = World.Environment.valueOf(envName);

                        if(env == world.getEnvironment()){
                            Block block = world.getBlockAt((int) x, (int) y, (int) z);
                            String xyz = x + ", " + y + ", " + z;

                            if (block.getType() != Material.LECTERN) {
                                removeClaim(claimData, cID, xyz);
                                sender.sendMessage("Removed claim " + cID + " at (" + xyz + ") in " + utils.getWorldName(env));
                            }else{
                                String claimID = utils.getClaimID(block.getLocation());

                                // This should only be true if the chunk-width has been changed
                                if(!claimID.equals(cID)){
                                    String[] members = claimData.getString(cID + ".members", "").split("\\n");

                                    removeClaim(claimData, cID, xyz);
                                    sender.sendMessage("Lectern outside claim: Removed claim " + cID + " at (" + xyz + ") in " + utils.getWorldName(env));

                                    if(!allClaimIDs.contains(claimID)) {
                                        utils.claimChunk(block, Arrays.stream(members).toList(), null);
                                        sender.sendMessage("Lectern outside claim: Added claim " + claimID + " at (" + xyz + ") in " + utils.getWorldName(env));
                                    }
                                }
                            }
                        }
                    }
                }

                for (String mbook : masterBooks.getKeys(false)) {
                    if(!mbook.equalsIgnoreCase("next-id")) {
                        List<String> copies = masterBooks.getStringList(mbook + ".copies-on-lecterns");
                        List<String> copiesToRemove = new ArrayList<>();

                        for (String copy : copies) {
                            String[] parts = copy.split("!");
                            String claimID = parts[0];
                            String[] xyz = parts[1].split(",");
                            String environment = claimID.split("\\|")[0];
                            World.Environment env = World.Environment.valueOf(environment);
                            int x = Integer.parseInt(xyz[0]);
                            int y = Integer.parseInt(xyz[1]);
                            int z = Integer.parseInt(xyz[2]);

                            for(World world : Bukkit.getWorlds()){
                                if(world.getEnvironment() == env){
                                    Block block = world.getBlockAt(x, y, z);

                                    if (block.getType() != Material.LECTERN) {
                                        copiesToRemove.add(copy);
                                        sender.sendMessage("Removed from Master Book #" + mbook + " copies-on-lecterns: (" + String.join(", ", xyz) + ") in " + utils.getWorldName(env));
                                    }
                                }
                            }
                        }

                        for(String copy : copiesToRemove)
                            copies.remove(copy);

                        masterBooks.set(mbook + ".copies-on-lecterns", copies);
                    }
                }

                plugin.cfg.saveClaimData();
                plugin.cfg.saveOfflineClaimNotifications();
                plugin.cfg.saveMasterBooks();
                utils.updateClaimCount();
                return true;
            }
        }

        // Show usage if no valid args
        sender.sendMessage(ChatColor.YELLOW + "Usage:");
        sender.sendMessage(ChatColor.GRAY + "  /claimfix confirm " + ChatColor.WHITE + "- Fix corrupted claims");
        sender.sendMessage(ChatColor.GRAY + "  /claimfix debug " + ChatColor.WHITE + "- Debug claim data");
        return false;
    }

    private void removeClaim(FileConfiguration claimData, String cID, String xyz){
        String[] members = claimData.getString(cID + ".members", "").split("\\n");
        claimData.set(cID, null);
        utils.onChunkUnclaim(cID, members, xyz, null);
    }
}