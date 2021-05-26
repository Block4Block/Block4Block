package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ClaimContestCommand implements CommandExecutor, TabCompleter {
    private final Block4Block plugin;
    private BukkitTask contestTask;
    private final int SECONDS_PER_HOUR = 60 * 60;
    private final int SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR;


    public ClaimContestCommand(Block4Block plugin) {
        this.plugin = plugin;

        // In case the server restarted while a contest was underway:
        this.contestTask = startContest();
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if(args.length > 0 && sender instanceof Player) {
            Player p = (Player) sender;
            FileConfiguration claimContest = plugin.cfg.getClaimContest();

            try {
                switch (args[0].toLowerCase()) {
                    case "help":
                        p.sendMessage(ChatColor.GOLD + "To create a claim contest, use a combination of:");
                        p.sendMessage(ChatColor.GRAY + "/claimcontest chunk [overworld | nether | end] x z");
                        p.sendMessage(ChatColor.GRAY + "/claimcontest duration minutes [hours] [days]");
                        p.sendMessage(ChatColor.GRAY + "/claimcontest prize amount");
                        p.sendMessage(ChatColor.GRAY + "/claimcontest start");
                        p.sendMessage(ChatColor.GRAY + "/claimcontest cancel");
                        break;

                    case "chunk":
                    case "loc":
                    case "at":
                    case "xyz":
                        if (args.length >= 4) {
                            int x = Integer.parseInt(args[2]);
                            int z = Integer.parseInt(args.length >= 5 ? args[4] : args[3]);
                            World.Environment environment;
                            String dimension = args[1].toLowerCase();

                            switch (dimension) {
                                case "overworld":
                                    environment = World.Environment.NORMAL;
                                    break;

                                case "nether":
                                    environment = World.Environment.NETHER;
                                    break;

                                case "end":
                                    environment = World.Environment.THE_END;
                                    break;

                                default:
                                    return false;
                            }

                            claimContest.set("data.chunkLoc", StringUtils.capitalize(dimension) + " (X: " + x + ", Z: " + z + ")");
                            claimContest.set("data.chunkID", utils.getChunkID(x, z, environment));
                        }else{
                            return false;
                        }
                        break;

                    case "duration":
                    case "time":
                        long minutes = 0;

                        if (args.length >= 2) {
                            minutes += Long.parseLong(args[1]);
                            if (args.length >= 3) {
                                minutes += Long.parseLong(args[2]) * 60;
                                if (args.length >= 4)
                                    minutes += Long.parseLong(args[3]) * 24 * 60;
                            }
                        } else {
                            return false;
                        }

                        claimContest.set("data.duration", minutes * (long) 6e10);
                        break;

                    case "prize":
                        if(args.length > 1)
                            claimContest.set("data.prize", String.join(" ", args).substring(6));
                        else
                            return false;
                        break;

                    case "start":
                        if (this.contestTask == null)
                            this.contestTask = startContest();
                        else
                            p.sendMessage(ChatColor.RED + "A contest is already running!");
                        break;

                    case "cancel":
                        this.cancelContest();
                        break;

                    default:
                        return false;
                }
                sendContestData(p);
                return true;
            }catch(Exception e){
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    private void sendContestData(Player p){
        FileConfiguration claimContest = plugin.cfg.getClaimContest();
        long durationNs = claimContest.getLong("data.duration", 0);
        long endTime = System.nanoTime() + durationNs;

        String chunkLoc = claimContest.getString("data.chunkLoc", "none");
        String duration = getTimeLeft(endTime, System.nanoTime());
        String prize = claimContest.getString("data.prize", "none");

        p.sendMessage("Location: " + chunkLoc);
        p.sendMessage("Duration: " + duration);
        p.sendMessage("Prize: " + prize);
    }

    private BukkitTask startContest() {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        FileConfiguration claimContest = plugin.cfg.getClaimContest();

        String chunkID = claimContest.getString("data.chunkID");
        long duration = claimContest.getLong("data.duration", 0);
        plugin.cfg.saveClaimContest();

        if(duration > 0 && chunkID != null) {
            long endTime = System.nanoTime() + duration;
            claimContest.set("data.start-timestamp", System.nanoTime());

            return Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> {
                        if (System.nanoTime() >= endTime) {
                            String chunkLoc = claimContest.getString("data.chunkLoc", "none");
                            String claimant = claimData.getString(chunkID + ".members", "No one");
                            String prize = claimContest.getString("data.prize", "none");

                            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MMM-dd_HH-mm");
                            LocalDateTime now = LocalDateTime.now();
                            String endDate = dtf.format(now);

                            claimContest.set(endDate + ".location", chunkLoc);
                            claimContest.set(endDate + ".winner", claimant);
                            claimContest.set(endDate + ".prize", prize);

                            Bukkit.broadcastMessage(ChatColor.GOLD + "THE CONTEST HAS ENDED!");
                            if(!claimant.equals("No one"))
                                Bukkit.broadcastMessage("And the winner is... " + ChatColor.GREEN + claimant + "!");
                            else
                                Bukkit.broadcastMessage("No one won this round! Better luck next time!");
                            this.cancelContest();
                        } else {
                            String timeLeft = getTimeLeft(endTime, System.nanoTime());

                            String claimant = claimData.getString(chunkID + ".members", "No one");
                            claimant = claimant.split("\\n")[0];

                            String prize = claimContest.getString("data.prize", "none");

                            String chunkLoc = claimContest.getString("data.chunkLoc", "none");

                            // Information shown to/on the current claimant
                            Scoreboard claimantBoard = createClaimantScoreboard(timeLeft, prize, claimant, chunkLoc);

                            // Information shown to everyone else
                            Scoreboard playerBoard = createPlayerScoreboard(timeLeft, prize, claimant, chunkLoc);

                            for(Player p : Bukkit.getOnlinePlayers())
                                if(p.getName().equalsIgnoreCase(claimant) && claimantBoard != null)
                                    p.setScoreboard(claimantBoard);
                                else if(playerBoard != null)
                                    p.setScoreboard(playerBoard);

                            // Inform the players if the claimant has changed
                            String prevClaimant = claimContest.getString("data.claimant", "No one");
                            if(!claimant.equalsIgnoreCase(prevClaimant) && !claimant.equals("No one"))
                                Bukkit.broadcastMessage(ChatColor.GOLD + claimant + " has claimed the Contest Chunk!");
                            claimContest.set("data.claimant", claimant);
                        }
                    }, 0, 20);
        }
        return null;
    }

    private String getTimeLeft(long endTime, long now) {
        long secondsTotal = (endTime - now) / (long) 1e9;
        long daysLeft = secondsTotal / SECONDS_PER_DAY;
        long hoursLeft = (secondsTotal % SECONDS_PER_DAY) / SECONDS_PER_HOUR;
        long minutesLeft = (secondsTotal % SECONDS_PER_HOUR) / 60;
        long secondsLeft = secondsTotal % 60;

        return daysLeft + ":" +
                (hoursLeft < 10 ? "0" + hoursLeft : hoursLeft) +  ":" +
                (minutesLeft < 10 ? "0" + minutesLeft : minutesLeft) + ":" +
                (secondsLeft < 10 ? "0" + secondsLeft : secondsLeft);
    }

    private void cancelContest() {
        this.contestTask.cancel();
        this.contestTask = null;
        plugin.cfg.clearClaimContest();

        if(Bukkit.getScoreboardManager() != null)
            for(Player p : Bukkit.getOnlinePlayers())
                p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }

    private Scoreboard createPlayerScoreboard(String timeLeft, String prize, String claimant, String chunkLoc){
        if(Bukkit.getScoreboardManager() != null) {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective sidebarObj = scoreboard.registerNewObjective("contest", "dummy", "Claim Contest");
            Objective claimantObj = scoreboard.registerNewObjective("claimant", "dummy", ChatColor.GOLD + "Claimant");

            sidebarObj.setDisplaySlot(DisplaySlot.SIDEBAR);
            sidebarObj.getScore("Location: " + chunkLoc).setScore(-1);
            sidebarObj.getScore("Countdown: " + timeLeft).setScore(-2);
            sidebarObj.getScore("Prize: " + prize).setScore(-3);
            sidebarObj.getScore("Claimant: " + claimant).setScore(-4);

            claimantObj.setDisplaySlot(DisplaySlot.BELOW_NAME);
            claimantObj.getScore(claimant).setScore(0);
            claimantObj.setDisplayName(ChatColor.GOLD + "Claim Contest Leader");

            return scoreboard;
        }
        return null;
    }

    private Scoreboard createClaimantScoreboard(String timeLeft, String prize, String claimant, String chunkLoc){
        if(Bukkit.getScoreboardManager() != null) {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective sidebarObj = scoreboard.registerNewObjective("contest", "dummy", "Claim Contest");

            sidebarObj.setDisplaySlot(DisplaySlot.SIDEBAR);
            sidebarObj.getScore("Location: " + chunkLoc).setScore(-1);
            sidebarObj.getScore("Countdown: " + timeLeft).setScore(-2);
            sidebarObj.getScore("Prize: " + prize).setScore(-3);
            sidebarObj.getScore("Claimant: " + claimant).setScore(-4);

            return scoreboard;
        }
        return null;
    }

    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args){
        List<String> suggestions = new ArrayList<>();
        Player p = sender instanceof Player ? (Player) sender : null;

        if(args.length == 1){
            suggestions.add("help");
            suggestions.add("chunk");
            suggestions.add("duration");
            suggestions.add("prize");
            suggestions.add("start");
            suggestions.add("cancel");
        }

        if(args.length == 2) {
            if(args[0].equalsIgnoreCase("chunk")) {
                suggestions.add("overworld");
                suggestions.add("nether");
                suggestions.add("end");
            }else if (args[0].equalsIgnoreCase("duration")){
                suggestions.add("<minutes>");
            }else if (args[0].equalsIgnoreCase("prize")){
                suggestions.add("<amount>");
            }
        }

        if(args.length == 3) {
            if(args[0].equalsIgnoreCase("chunk")) {
                if(p != null)
                    suggestions.add(String.valueOf(p.getLocation().getX()));
                suggestions.add("<X>");
            }else if (args[0].equalsIgnoreCase("duration")){
                suggestions.add("<hours>");
            }
        }

        if(args.length == 3) {
            if(args[0].equalsIgnoreCase("chunk")) {
                if(p != null)
                    suggestions.add(String.valueOf(p.getLocation().getZ()));
                suggestions.add("<Z>");
            }else if (args[0].equalsIgnoreCase("duration")){
                suggestions.add("<days>");
            }
        }

        return suggestions;
    }
}