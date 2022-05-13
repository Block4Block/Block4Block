package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.BedCmdSucceededEvent;
import hasjamon.block4block.utils.utils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;

public class BedCommand implements CommandExecutor {
    private final Block4Block plugin;

    public BedCommand(Block4Block plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(sender instanceof Player player){
            String pID = player.getUniqueId().toString();
            FileConfiguration bedCommandUsage = plugin.cfg.getBedCommandUsage();
            long now = System.currentTimeMillis();
            long nextAvailable = bedCommandUsage.getLong(pID, 0);
            long diff = nextAvailable - now;

            if(diff <= 0){
                player.getInventory().addItem(new ItemStack(Material.WHITE_BED, 1));
                player.sendMessage(ChatColor.GRAY + "A bed has been added to your inventory!");

                bedCommandUsage.set(pID, now + 24 * 60 * 60 * 1000);
                plugin.cfg.saveBedCommandUsage();
            }else{
                DecimalFormat decimals = new DecimalFormat("#.#");
                String hours = decimals.format(diff / (60.0 * 60 * 1000));
                String msg = utils.chat("&7It's been less than 24 hours since your last use of /bed! Try again in " + hours + " hours.");

                player.sendMessage(msg);
            }

            plugin.pluginManager.callEvent(new BedCmdSucceededEvent(player));
            return true;
        }
        return false;
    }
}
