package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public class ClaimContestCommand implements CommandExecutor {
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
                switch (args[0]) {
                    case "help":
                        p.sendMessage(ChatColor.GOLD + "To create a claim contest, use a combination of:");
                        p.sendMessage(ChatColor.GRAY + "/claimcontest chunk [overworld | nether | end] x z");
                        p.sendMessage(ChatColor.GRAY + "/claimcontest duration minutes [hours] [days]");
                        p.sendMessage(ChatColor.GRAY + "/claimcontest prize amount");
                        p.sendMessage(ChatColor.GRAY + "/claimcontest start");
                        break;

                    case "chunk":
                    case "loc":
                    case "at":
                    case "xyz":
                        if (args.length >= 4) {
                            int x = Integer.parseInt(args[2]);
                            int z = Integer.parseInt(args.length >= 5 ? args[4] : args[3]);
                            World.Environment dimension;

                            switch (args[1].toLowerCase()) {
                                case "overworld":
                                    dimension = World.Environment.NORMAL;
                                    break;

                                case "nether":
                                    dimension = World.Environment.NETHER;
                                    break;

                                case "end":
                                    dimension = World.Environment.THE_END;
                                    break;

                                default:
                                    return false;
                            }

                            claimContest.set("chunkID", utils.getChunkID(x, z, dimension));
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

                        claimContest.set("duration", minutes * (long) 6e10);
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

                    case "prize":
                        if(args.length > 1)
                            claimContest.set("prize", args[1]);
                        else
                            return false;
                        break;

                    default:
                        return false;
                }
                return true;
            }catch(Exception e){
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    private BukkitTask startContest() {
        FileConfiguration claimData = plugin.cfg.getClaimData();
        FileConfiguration claimContest = plugin.cfg.getClaimContest();

        String chunkID = claimContest.getString("chunkID");
        long duration = claimContest.getLong("duration", 0);
        plugin.cfg.saveClaimContest();

        if(duration > 0 && chunkID != null) {
            long endTime = System.nanoTime() + duration;
            claimContest.set("start-timestamp", System.nanoTime());

            return Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> {
                        if (System.nanoTime() >= endTime) {
                            String claimant = claimData.getString(chunkID + ".members", "No one");

                            Bukkit.broadcastMessage(ChatColor.GOLD + "THE CONTEST HAS ENDED!");
                            Bukkit.broadcastMessage("And the winner is... " + ChatColor.GREEN + claimant + "!");
                            this.cancelContest();
                        } else {
                            String timeLeft = getTimeLeft(endTime, System.nanoTime());

                            String claimant = claimData.getString(chunkID + ".members", "No one");
                            claimant = claimant.split("\\n")[0];

                            String prize = claimContest.getString("prize", "none");

                            // Information shown to/on the current claimant
                            Scoreboard claimantBoard = createClaimantScoreboard(timeLeft, prize, claimant);

                            // Information shown to everyone else
                            Scoreboard playerBoard = createPlayerScoreboard(timeLeft, prize, claimant);

                            for(Player p : Bukkit.getOnlinePlayers())
                                if(p.getName().equalsIgnoreCase(claimant) && claimantBoard != null)
                                    p.setScoreboard(claimantBoard);
                                else if(playerBoard != null)
                                    p.setScoreboard(playerBoard);
                        }
                    }, 0, 20);
        }
        return null;
    }

    private String getTimeLeft(long endTime, long nanoTime) {
        long secondsTotal = (endTime - System.nanoTime()) / (long) 1e9;
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

    private Scoreboard createPlayerScoreboard(String timeLeft, String prize, String claimant){
        if(Bukkit.getScoreboardManager() != null) {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective sidebarObj = scoreboard.registerNewObjective("contest", "dummy", "Claim Contest");

            sidebarObj.setDisplaySlot(DisplaySlot.SIDEBAR);
            sidebarObj.getScore("Countdown: " + timeLeft).setScore(-1);
            sidebarObj.getScore("Prize: " + prize).setScore(-2);
            sidebarObj.getScore("Claimant: " + claimant).setScore(-3);

            return scoreboard;
        }
        return null;
    }

    private Scoreboard createClaimantScoreboard(String timeLeft, String prize, String claimant){
        if(Bukkit.getScoreboardManager() != null) {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective sidebarObj = scoreboard.registerNewObjective("contest", "dummy", "Claim Contest");
            Objective claimantObj = scoreboard.registerNewObjective("claimant", "dummy", ChatColor.GOLD + "Claimant");

            sidebarObj.setDisplaySlot(DisplaySlot.SIDEBAR);
            sidebarObj.getScore("Countdown: " + timeLeft).setScore(-1);
            sidebarObj.getScore("Prize: " + prize).setScore(-2);
            sidebarObj.getScore("Claimant: " + claimant).setScore(-3);

            claimantObj.setDisplaySlot(DisplaySlot.BELOW_NAME);
            claimantObj.getScore(ChatColor.GOLD + "Claim Contest Leader").setScore(0);

            return scoreboard;
        }
        return null;
    }

}
