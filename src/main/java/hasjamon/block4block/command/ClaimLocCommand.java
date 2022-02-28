package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ClaimLocCommand implements CommandExecutor {
    private final Block4Block plugin;

    public ClaimLocCommand(Block4Block plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(sender instanceof Player p){
            String claimID = utils.getClaimID(p.getLocation());
            FileConfiguration claimData = plugin.cfg.getClaimData();

            if(claimData.contains(claimID)){
                double x = claimData.getDouble(claimID + ".location.X");
                double y = claimData.getDouble(claimID + ".location.Y");
                double z = claimData.getDouble(claimID + ".location.Z");

                p.sendMessage("Lectern is located at " + x + ", " + y + ", " + z);
            }else{
                p.sendMessage("This chunk isn't claimed.");
            }
            return true;
        }
        return false;
    }
}
