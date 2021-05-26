package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class IgnoreCommand implements CommandExecutor, TabCompleter {
    private final Block4Block plugin;

    public IgnoreCommand(Block4Block plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(args.length > 0 && sender instanceof Player) {
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
            long until = System.nanoTime();

            switch (label.toLowerCase()) {
                case "ignore":
                    if(args.length == 2)
                        if(args[1].matches("[0-9]+"))
                            until += Long.parseLong(args[1]) * 6e10;
                        else
                            return false;
                    else
                        until *= 2;
                    break;

                case "unignore":
                    until = 0;
                    break;

                default:
                    return false;
            }

            sendIgnoreMessage(ignorer, ignoreeName, System.nanoTime() < until);
            ignoreLists.set(field, until);
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

    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args){
        List<String> suggestions = new ArrayList<>();

        if(args.length == 1)
            for(Player p : Bukkit.getOnlinePlayers())
                if(!p.getName().equals(sender.getName()))
                    suggestions.add(p.getName());

        if(args.length == 2 && cmd.getName().equalsIgnoreCase("ignore"))
            suggestions.add("<minutes>");

        return suggestions;
    }
}
