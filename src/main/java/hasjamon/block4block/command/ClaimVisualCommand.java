package hasjamon.block4block.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class ClaimVisualCommand implements CommandExecutor {
    // Shared set passed from the main plugin to track players with visual enabled
    private final Set<UUID> visualEnabledPlayers;

    public ClaimVisualCommand(Set<UUID> visualEnabledPlayers) {
        this.visualEnabledPlayers = visualEnabledPlayers;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            UUID playerUUID = player.getUniqueId();

            if (args.length == 1) {
                String action = args[0].toLowerCase();

                if (action.equals("on")) {
                    if (!visualEnabledPlayers.contains(playerUUID)) {
                        visualEnabledPlayers.add(playerUUID);
                        player.sendMessage(ChatColor.GREEN + "Claim visual enabled.");
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "Claim visual is already enabled.");
                    }
                    return true;
                } else if (action.equals("off")) {
                    if (visualEnabledPlayers.contains(playerUUID)) {
                        visualEnabledPlayers.remove(playerUUID);
                        player.sendMessage(ChatColor.RED + "Claim visual disabled.");
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "Claim visual is already disabled.");
                    }
                    return true;
                } else {
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
