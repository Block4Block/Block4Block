package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class HintsCommand implements CommandExecutor {
    private final Block4Block plugin;

    public HintsCommand(Block4Block plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(sender instanceof Player) {
            FileConfiguration hintSettings = plugin.cfg.getHintSettings();
            String pUUID = ((Player) sender).getUniqueId().toString();

            if(args[0].equalsIgnoreCase("off")) {
                hintSettings.set(pUUID, "off");
                plugin.cfg.saveHintSettings();
                sender.sendMessage("Hints are now " + ChatColor.RED + "OFF");
                return true;
            }else if(args[0].equalsIgnoreCase("on")) {
                hintSettings.set(pUUID, "on");
                plugin.cfg.saveHintSettings();
                sender.sendMessage("Hints are now " + ChatColor.GREEN + "ON");
                return true;
            }
        }
        return false;
    }
}
