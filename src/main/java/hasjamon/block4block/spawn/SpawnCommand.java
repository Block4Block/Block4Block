package hasjamon.block4block.spawn;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Op tool for the dynamic spawn: /b4bspawn info|next|move|pause|resume|center here. */
public class SpawnCommand implements CommandExecutor {
    private final DynamicSpawn spawn;

    public SpawnCommand(DynamicSpawn spawn) {
        this.spawn = spawn;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return usage(sender, label);
        switch (args[0].toLowerCase()) {
            case "info":
                sender.sendMessage("Dynamic spawn: " + spawn.info());
                return true;
            case "next":
                sender.sendMessage("Next spawn: " + spawn.next());
                return true;
            case "move":
                sender.sendMessage(spawn.moveNow() ? "Spawn moved. " + spawn.info() : "No cell qualified; spawn stays.");
                return true;
            case "pause":
                spawn.setPaused(true);
                sender.sendMessage("Dynamic spawn paused.");
                return true;
            case "resume":
                spawn.setPaused(false);
                sender.sendMessage("Dynamic spawn resumed.");
                return true;
            case "center":
                if (args.length == 2 && args[1].equalsIgnoreCase("here") && sender instanceof Player) {
                    sender.sendMessage(spawn.centerHere((Player) sender));
                    return true;
                }
                return usage(sender, label);
            default:
                return usage(sender, label);
        }
    }

    private boolean usage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " info|next|move|pause|resume|center here");
        return true;
    }
}
