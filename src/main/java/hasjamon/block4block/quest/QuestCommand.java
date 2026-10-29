package hasjamon.block4block.quest;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Op tool for the questlines: /b4bquest status|skip|reset [player]. */
public class QuestCommand implements CommandExecutor {
    private final QuestManager quests;

    public QuestCommand(QuestManager quests) {
        this.quests = quests;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2) return usage(sender, label);
        Player target = args.length == 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player ? (Player) sender : null;
        if (target == null) {
            sender.sendMessage("Name an online player: /" + label + " " + args[0] + " <player>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "status":
                sender.sendMessage(target.getName() + ": " + quests.adminStatus(target));
                return true;
            case "skip":
                quests.adminSkip(target);
                sender.sendMessage(target.getName() + ": " + quests.adminStatus(target));
                return true;
            case "reset":
                quests.adminReset(target);
                sender.sendMessage("Questlines restarted for " + target.getName() + ".");
                return true;
            default:
                return usage(sender, label);
        }
    }

    private boolean usage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " status|skip|reset [player]");
        return true;
    }
}
