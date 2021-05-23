package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class IgnoreCommand implements CommandExecutor {
    private final Block4Block plugin;

    public IgnoreCommand(Block4Block plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(args.length > 0 && sender instanceof  Player) {
            FileConfiguration ignoreLists = plugin.cfg.getIgnoreLists();
            String ignoreeName = args[0];
            List<Player> ignoreeCandidates = Bukkit.matchPlayer(ignoreeName);
            Player ignorer = (Player) sender;
            Player ignoree;

            if(ignoreeCandidates.size() == 1)
                ignoree = ignoreeCandidates.get(0);
            else
                return false;

            String field = ignoree.getUniqueId() + "." + ignorer.getUniqueId();
            boolean shouldIgnore = true;

            switch (label.toLowerCase()) {
                case "ignore":
                    if(ignoreLists.contains(field))
                        shouldIgnore = !ignoreLists.getBoolean(field);
                    break;

                case "unignore":
                    shouldIgnore = false;
                    break;

                default:
                    return false;
            }

            sendIgnoreMessage(ignorer, ignoreeName, shouldIgnore);
            ignoreLists.set(field, shouldIgnore);
            plugin.cfg.saveIgnoreLists();

            return true;
        }
        return false;
    }

    private void sendIgnoreMessage(Player ignorer, String ignoreeName, boolean shouldIgnore) {
        if(shouldIgnore)
            ignorer.sendMessage("Messages from " + ignoreeName + " are now " + ChatColor.RED + "OFF");
        else
            ignorer.sendMessage("Messages from " + ignoreeName + " are now " + ChatColor.GREEN + "ON");
    }
}
