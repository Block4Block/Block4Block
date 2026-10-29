package hasjamon.block4block.tooltips;

import hasjamon.block4block.Block4Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;

/**
 * Starts the server-side tooltips only when PacketEvents is installed. Kept separate from {@link ServerTooltips} so
 * that class (which imports PacketEvents) is never loaded on a server without it.
 */
public final class TooltipsBootstrap implements CommandExecutor {
    private static ServerTooltips tooltips;

    private TooltipsBootstrap() {}

    public static void enable(Block4Block plugin) {
        if (!plugin.getConfig().getBoolean("server-tooltips.enabled", true)) return;
        if (!plugin.getServer().getPluginManager().isPluginEnabled("packetevents")) {
            plugin.getLogger().info("PacketEvents not found - skipping server-side tooltips.");
            return;
        }
        tooltips = new ServerTooltips(plugin);
        tooltips.register();
        PluginCommand cmd = plugin.getCommand("b4btooltips");
        if (cmd != null) cmd.setExecutor(new TooltipsBootstrap());
        plugin.getLogger().info("Server-side tooltips enabled.");
    }

    public static void disable() {
        if (tooltips != null) tooltips.unregister();
        tooltips = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload") && tooltips != null) {
            Block4Block plugin = Block4Block.getInstance();
            plugin.reloadConfig();
            tooltips.reload(plugin.getConfig());
            sender.sendMessage("Server-side tooltips reloaded.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status") && tooltips != null) {
            sender.sendMessage("Server-side tooltips: " + tooltips.status());
            return true;
        }
        sender.sendMessage("Usage: /" + label + " reload|status");
        return true;
    }
}
