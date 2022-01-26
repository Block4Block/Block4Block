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
import java.util.List;

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
                for (String key : claimData.getKeys(false)) {
                    double x = claimData.getDouble(key + ".location.X");
                    double y = claimData.getDouble(key + ".location.Y");
                    double z = claimData.getDouble(key + ".location.Z");
                    String envName = key.split("\\|")[0];

                    for(World world : Bukkit.getWorlds()){
                        World.Environment env = World.Environment.valueOf(envName);

                        if(world.getEnvironment() == World.Environment.valueOf(envName)){
                            Block block = world.getBlockAt((int) x, (int) y, (int) z);

                            if (block.getType() != Material.LECTERN) {
                                String xyz = x + ", " + y + ", " + z;
                                String[] members = claimData.getString(key + ".members", "").split("\\n");
                                claimData.set(key, null);
                                utils.onChunkUnclaim(key, members, xyz, null);
                                sender.sendMessage("Removed claim " + key + " at (" + xyz + ") in " + utils.getWorldName(env));
                            }
                        }
                    }
                }

                for (String key : masterBooks.getKeys(false)) {
                    if(!key.equalsIgnoreCase("next-id")) {
                        List<String> copies = masterBooks.getStringList(key + ".copies-on-lecterns");
                        List<String> copiesToRemove = new ArrayList<>();

                        for (String copy : copies) {
                            String[] parts = copy.split("!");
                            String chunkID = parts[0];
                            String[] xyz = parts[1].split(",");
                            String environment = chunkID.split("\\|")[0];
                            World.Environment env = World.Environment.valueOf(environment);
                            int x = Integer.parseInt(xyz[0]);
                            int y = Integer.parseInt(xyz[1]);
                            int z = Integer.parseInt(xyz[2]);

                            for(World world : Bukkit.getWorlds()){
                                if(world.getEnvironment() == env){
                                    Block block = world.getBlockAt(x, y, z);

                                    if (block.getType() != Material.LECTERN) {
                                        copiesToRemove.add(copy);
                                        sender.sendMessage("Removed from Master Book #" + key + " copies-on-lecterns: (" + String.join(", ", xyz) + ") in " + utils.getWorldName(env));
                                    }
                                }
                            }
                        }

                        for(String copy : copiesToRemove)
                            copies.remove(copy);

                        masterBooks.set(key + ".copies-on-lecterns", copies);
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
}