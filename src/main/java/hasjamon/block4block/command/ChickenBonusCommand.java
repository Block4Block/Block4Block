package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.ChickenBonuses;
import hasjamon.block4block.utils.utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class ChickenBonusCommand implements CommandExecutor {
    private final Block4Block plugin;

    public ChickenBonusCommand(Block4Block plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(sender instanceof Player player){
            ChickenBonuses bonuses = utils.calcChickenBonuses(player);
            Map<Character, Integer> letterBonuses = bonuses.letterBonuses;
            int numNamedChickens = bonuses.numNamedChickens;

            double spawnChance = plugin.getConfig().getDouble("spawn-egg-chance");
            double withBonus = spawnChance * utils.calcGeneralChickenBonus(numNamedChickens);
            player.sendMessage("§7Unique coords with named chickens nearby: " + numNamedChickens);
            player.sendMessage("§7Chance to lay a spawn egg: " + Math.floor(withBonus * 10000) / 100 + "%");
            player.sendMessage("§7Letter bonuses: " + letterBonuses.toString());
            return true;
        }
        return false;
    }
}
