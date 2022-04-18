package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CoordsCommand implements CommandExecutor, TabCompleter {
    private final Block4Block plugin;

    public CoordsCommand(Block4Block plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(sender instanceof Player && args.length > 0) {
            FileConfiguration coordsSettings = plugin.cfg.getCoordsSettings();
            String pUUID = ((Player) sender).getUniqueId().toString();

            if(args[0].equalsIgnoreCase("off")) {
                coordsSettings.set(pUUID, "off");
                plugin.cfg.saveCoordsSettings();
                sender.sendMessage("Coords in system messages are now " + ChatColor.RED + "OFF");
                return true;
            }else if(args[0].equalsIgnoreCase("on")) {
                coordsSettings.set(pUUID, "on");
                plugin.cfg.saveCoordsSettings();
                sender.sendMessage("Coords in system messages are now " + ChatColor.GREEN + "ON");
                return true;
            }
        }
        return false;
    }

    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args){
        List<String> suggestions = new ArrayList<>();

        if(args.length == 1){
            suggestions.add("on");
            suggestions.add("off");
        }

        return suggestions;
    }
}
