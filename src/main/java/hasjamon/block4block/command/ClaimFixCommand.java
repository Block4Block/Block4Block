package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
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
        if(args.length > 0 && args[0].equalsIgnoreCase("confirm")){
            FileConfiguration claimData = plugin.cfg.getClaimData();
            FileConfiguration masterBooks = plugin.cfg.getMasterBooks();

            if(plugin.cfg.backupClaimData() && plugin.cfg.backupOfflineClaimNotifications() && plugin.cfg.backupMasterBooks()) {
                Set<String> allScIDs = claimData.getKeys(false);

                for (String scID : allScIDs) {
                    double x = claimData.getDouble(scID + ".location.X");
                    double y = claimData.getDouble(scID + ".location.Y");
                    double z = claimData.getDouble(scID + ".location.Z");
                    String envName = scID.split("\\|")[0];

                    for(World world : Bukkit.getWorlds()){
                        World.Environment env = World.Environment.valueOf(envName);

                        if(env == world.getEnvironment()){
                            Block block = world.getBlockAt((int) x, (int) y, (int) z);
                            String xyz = x + ", " + y + ", " + z;

                            if (block.getType() != Material.LECTERN) {
                                removeClaim(claimData, scID, xyz);
                                sender.sendMessage("Removed claim " + scID + " at (" + xyz + ") in " + utils.getWorldName(env));
                            }else{
                                String claimID = utils.getClaimID(block.getLocation());

                                // This should only be true if the chunk-width has been changed
                                if(!claimID.equals(scID)){
                                    String[] members = claimData.getString(scID + ".members", "").split("\\n");

                                    removeClaim(claimData, scID, xyz);
                                    sender.sendMessage("Lectern outside claim: Removed claim " + scID + " at (" + xyz + ") in " + utils.getWorldName(env));

                                    if(!allScIDs.contains(claimID)) {
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

        return false;
    }

    private void removeClaim(FileConfiguration claimData, String scID, String xyz){
        String[] members = claimData.getString(scID + ".members", "").split("\\n");
        claimData.set(scID, null);
        utils.onChunkUnclaim(scID, members, xyz, null);
    }
}