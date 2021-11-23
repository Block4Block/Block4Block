package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.BedCmdSucceededEvent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class BedCommand implements CommandExecutor {
    private final Block4Block plugin;

    public BedCommand(Block4Block plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(sender instanceof Player){
            Player p = (Player) sender;
            String pID = p.getUniqueId().toString();
            FileConfiguration bedCommandUsage = plugin.cfg.getBedCommandUsage();
            long now = System.nanoTime();

            if(now > bedCommandUsage.getLong(pID, 0)){
                p.getInventory().addItem(new ItemStack(Material.WHITE_BED, 1));
                p.sendMessage(ChatColor.GRAY + "A bed has been added to your inventory!");

                bedCommandUsage.set(pID, now + 24 * 60 * 60 * 1e9);
                plugin.cfg.saveBedCommandUsage();
            }else{
                p.sendMessage(ChatColor.GRAY + "It's been less than 24 hours since your last use of /bed!");
            }

            plugin.pluginManager.callEvent(new BedCmdSucceededEvent(p));
            return true;
        }
        return false;
    }
}
