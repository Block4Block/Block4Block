package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CoordsCommand implements CommandExecutor, TabCompleter {
    private final Block4Block plugin;
    private final Set<String> validArgs = Set.of("off", "on");

    public CoordsCommand(Block4Block plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && args.length > 0) {
            FileConfiguration coordsSettings = plugin.cfg.getCoordsSettings();
            String pUUID = player.getUniqueId().toString();
            String arg = args[0];

            if (validArgs.contains(arg)) {
                coordsSettings.set(pUUID, arg.toLowerCase());
                plugin.cfg.saveCoordsSettings();
                player.sendMessage("Coords in system messages are now " + ChatColor.RED + arg.toUpperCase());
                utils.updateBossBar(player, utils.getClaimID(player.getLocation()));
                return true;
            }
        }
        return false;
    }

    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.add("on");
            suggestions.add("off");
        }

        return suggestions;
    }
}
