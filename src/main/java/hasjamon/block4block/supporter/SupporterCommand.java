package hasjamon.block4block.supporter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Op tool for the Supporter cosmetics:
 * /b4bsupporter grant <player> prefix [days]
 * /b4bsupporter grant <player> claimcolor <colour> [days]
 * /b4bsupporter remove <player> prefix|claimcolor|all
 * /b4bsupporter info <player> | list | colors
 * Days left out means permanent. Works from the console too.
 */
public class SupporterCommand implements TabExecutor {
    private final SupporterPerks perks;

    public SupporterCommand(SupporterPerks perks) {
        this.perks = perks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return usage(sender, label);
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list": {
                Map<String, UUID> all = perks.supporters();
                if (all.isEmpty()) sender.sendMessage("No supporter perks granted.");
                all.forEach((name, id) -> sender.sendMessage(name + ": " + String.join("; ", perks.describe(id))));
                return true;
            }
            case "colors":
            case "colours":
                sender.sendMessage("Claim colours: " + String.join(", ", perks.colorNames()));
                return true;
            case "info":
            case "grant":
            case "remove":
                break;
            default:
                return usage(sender, label);
        }
        if (args.length < 2) return usage(sender, label);
        OfflinePlayer target = find(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "No player called " + args[1] + " has joined this server.");
            return true;
        }
        String name = target.getName() != null ? target.getName() : args[1];

        if (sub.equals("info")) {
            List<String> lines = perks.describe(target.getUniqueId());
            sender.sendMessage(name + ": " + (lines.isEmpty() ? "no supporter perks" : String.join("; ", lines)));
            return true;
        }

        if (args.length < 3) return usage(sender, label);
        String perkArg = args[2].toLowerCase(Locale.ROOT);

        if (sub.equals("remove")) {
            String perk = perkArg.equals("all") ? null : perkOf(perkArg);
            if (perk == null && !perkArg.equals("all")) return usage(sender, label);
            boolean removed = perks.remove(target.getUniqueId(), perk);
            sender.sendMessage(removed ? "Removed " + perkArg + " from " + name + "." : name + " had no " + perkArg + ".");
            return true;
        }

        // grant
        String perk = perkOf(perkArg);
        if (perk == null) return usage(sender, label);
        String color = null;
        int daysAt = 3;
        if (perk.equals(SupporterPerks.CLAIM_COLOR)) {
            if (args.length < 4 || !perks.colorNames().contains(args[3].toLowerCase(Locale.ROOT))) {
                sender.sendMessage(ChatColor.RED + "Pick a colour: " + String.join(", ", perks.colorNames()));
                return true;
            }
            color = args[3].toLowerCase(Locale.ROOT);
            daysAt = 4;
        }
        int days = 0;
        if (args.length > daysAt) {
            try {
                days = Integer.parseInt(args[daysAt]);
                if (days < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Days must be a whole number (leave it out for permanent).");
                return true;
            }
        }
        perks.grant(target, perk, color, days, sender.getName());
        String what = perk.equals(SupporterPerks.PREFIX) ? "the Supporter chat prefix" : "the " + color + " claim-map colour";
        sender.sendMessage("Gave " + name + " " + what + (days > 0 ? " for " + days + " days." : ", permanent."));
        Player online = target.getPlayer();
        if (online != null && online != sender)
            online.sendMessage(ChatColor.GOLD + "Thank you for supporting Block4Block! You now have " + what + ".");
        return true;
    }

    private static String perkOf(String arg) {
        switch (arg) {
            case "prefix": return SupporterPerks.PREFIX;
            case "claimcolor": case "claimcolour": case "claim-color": return SupporterPerks.CLAIM_COLOR;
            default: return null;
        }
    }

    /** An online player, else one who has joined before; never a lookup by name on Mojang's servers. */
    private static OfflinePlayer find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.hasPlayedBefore() ? cached : null;
    }

    private boolean usage(CommandSender sender, String label) {
        sender.sendMessage("Usage: /" + label + " grant <player> prefix [days] | grant <player> claimcolor <colour> [days]"
                + " | remove <player> prefix|claimcolor|all | info <player> | list | colors");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options;
        switch (args.length) {
            case 1: options = List.of("grant", "remove", "info", "list", "colors"); break;
            case 2: options = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()); break;
            case 3:
                options = args[0].equalsIgnoreCase("grant") ? List.of("prefix", "claimcolor")
                        : args[0].equalsIgnoreCase("remove") ? List.of("prefix", "claimcolor", "all") : List.of();
                break;
            case 4:
                options = args[0].equalsIgnoreCase("grant") && args[2].toLowerCase(Locale.ROOT).startsWith("claimcol")
                        ? perks.colorNames() : List.of();
                break;
            default: options = List.of();
        }
        String start = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(start)).collect(Collectors.toList());
    }
}
