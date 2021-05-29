package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class WelcomeCommand implements CommandExecutor {
    private final Block4Block plugin;

    public WelcomeCommand(Block4Block plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        List<String> welcomeMessages = plugin.getConfig().getStringList("welcome-messages");

        for(String msg : welcomeMessages)
            sender.sendMessage(utils.chat(msg));

        return true;
    }
}
