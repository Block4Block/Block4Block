package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class ClaimVisualCommand implements CommandExecutor {
    private final Block4Block plugin;
    private final Set<UUID> visualEnabledPlayers;

    // Single constructor to initialize both plugin and visualEnabledPlayers
    public ClaimVisualCommand(Block4Block plugin, Set<UUID> visualEnabledPlayers) {
        this.plugin = plugin;
        this.visualEnabledPlayers = visualEnabledPlayers;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            UUID playerUUID = player.getUniqueId();

            if (args.length == 1) {
                String action = args[0].toLowerCase();

                switch (action) {
                    case "on":
                        if (!visualEnabledPlayers.contains(playerUUID)) {
                            visualEnabledPlayers.add(playerUUID);
                            player.sendMessage(ChatColor.GREEN + "Claim visual enabled.");
                        } else {
                            player.sendMessage(ChatColor.YELLOW + "Claim visual is already enabled.");
                        }
                        return true;

                    case "off":
                        if (visualEnabledPlayers.contains(playerUUID)) {
                            visualEnabledPlayers.remove(playerUUID);
                            player.sendMessage(ChatColor.RED + "Claim visual disabled.");
                        } else {
                            player.sendMessage(ChatColor.YELLOW + "Claim visual is already disabled.");
                        }
                        return true;

                    default:
                        player.sendMessage(ChatColor.RED + "Usage: /claimvisual <on/off>");
                        return false;
                }
            } else {
                player.sendMessage(ChatColor.RED + "Usage: /claimvisual <on/off>");
                return false;
            }
        }
        return false;
    }
}
