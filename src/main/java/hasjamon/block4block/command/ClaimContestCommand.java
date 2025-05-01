package hasjamon.block4block.command;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.ClaimContestOverEvent;
import hasjamon.block4block.events.ContestChunkClaimedEvent; // Ensure this event class exists and is correct
import hasjamon.block4block.utils.utils; // Assuming this contains getClaimID
import org.apache.commons.lang.StringUtils;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.ZoneId;

public class ClaimContestCommand implements CommandExecutor, TabCompleter, Listener {

    private final Block4Block plugin;
    private final FileConfiguration claimContestConfig;
    private final FileConfiguration claimDataConfig;
    private final ScoreboardManager scoreboardManager;
    private final BukkitScheduler scheduler;
    private final PluginManager pluginManager;

    // --- Task Handles ---
    private BukkitTask mainTask; // Handles PreReveal countdown OR Standard Contest Timer OR Active Phase checks

    // --- Contest State ---
    // Made Phase enum public so other classes can reference it
    public enum Phase { PENDING, PRE_REVEAL, ACTIVE, HOLD, FINISHED }
    private Phase currentPhase = Phase.PENDING;
    private String currentContestClaimId = null; // Claim ID of the running contest
    private String currentHolderName = NO_CLAIMANT; // Player currently holding in HOLD phase
    private long contestStartTimeMillis = -1; // This is the start of the ACTIVE phase
    private long contestEndTimeMillis = -1;   // Used for standard timer OR pre-reveal end
    private final AtomicLong holdEndTimeMillis = new AtomicLong(-1); // Used for HOLD phase timer
    private boolean contestEnded = false; // Flag to prevent double-ending

    // --- Configuration Flags (read at start) ---
    private boolean modePreReveal = false;
    private boolean modeHold = false;
    private long preRevealDurationMillis = 0;
    private long holdDurationMillis = 0;
    private long contestDurationMillis = 0; // Standard duration if not HOLD mode

    // --- Constants ---
    private static final String DATA_SECTION = "data";
    private static final String HISTORY_SECTION = "history";
    private static final String CHUNK_LOC_KEY = "chunkLoc";
    private static final String CLAIM_ID_KEY = "claimID";
    private static final String DURATION_KEY = "duration"; // Standard duration (milliseconds)
    private static final String PRIZE_KEY = "prize";
    private static final String COMMAND_START_TIMESTAMP_KEY = "command-start-timestamp"; // Timestamp when /claimcontest start was issued
    private static final String START_TIMESTAMP_KEY = "active-phase-start-timestamp"; // Timestamp when ACTIVE phase began
    private static final String CONFIG_SAVE_TIMESTAMP_KEY = "config-save-timestamp"; // When config was last set before start
    private static final String CURRENT_CLAIMANT_KEY = "current-claimant"; // Persisted claimant during standard/active
    private static final String CURRENT_HOLDER_KEY = "current-holder"; // Persisted holder during HOLD phase
    private static final String PHASE_KEY = "current-phase";
    private static final String PRE_REVEAL_END_TIMESTAMP_KEY = "pre-reveal-end-timestamp";
    private static final String CONTEST_END_TIMESTAMP_KEY = "contest-end-timestamp"; // For standard timer persistence
    private static final String HOLD_END_TIMESTAMP_KEY = "hold-end-timestamp";

    // Proximity radius for displaying below-name titles (in chunks)
    private static final int PROXIMITY_RADIUS_CHUNKS = 5; // Adjust this value (in chunks) as needed

    // Mode configuration keys
    private static final String MODE_PRE_REVEAL_KEY = "mode.pre-reveal.enabled";
    private static final String MODE_HOLD_KEY = "mode.hold.enabled";
    private static final String DURATION_PRE_REVEAL_KEY = "mode.pre-reveal.duration"; // milliseconds
    private static final String DURATION_HOLD_KEY = "mode.hold.duration"; // milliseconds

    // Default values
    private static final long DEFAULT_PRE_REVEAL_MINUTES = 2;
    private static final long DEFAULT_HOLD_MINUTES = 5;

    private static final String NO_CLAIMANT = "No one"; // Represents no one holding the claim
    private static final String SCOREBOARD_OBJECTIVE_NAME = "b4bcontest";
    private static final String SCOREBOARD_TITLE = ChatColor.GOLD + "Claim Contest";
    // private static final String SCOREBOARD_CLAIMANT_BELOWNAME_OBJ = "b4bclaimant"; // Commented out
    // private static final String SCOREBOARD_CLAIMANT_BELOWNAME_TITLE = ChatColor.GOLD + "Claimant"; // Commented out

    // New constants for Scoreboard Teams
    private static final String TEAM_CLAIMANT = "b4bclaimant_team";
    private static final String TEAM_DEFENDER = "b4bdefender_team";
    private static final String TEAM_INTRUDER = "b4bintruder_team";


    private static final DateTimeFormatter HISTORY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MMM-dd_HH-mm-ss");

    // New constants for history keys
    private static final String HISTORY_START_DATE_KEY = "start-date";
    private static final String HISTORY_END_DATE_KEY = "end-date";
    private static final String HISTORY_CALCULATED_DURATION_KEY = "calculated-duration";


    private static final long TICKS_PER_SECOND = 20L;
    private static final long MILLIS_PER_MINUTE = 60000L;

    // Time constants in seconds for clarity
    private static final long SECONDS_PER_MINUTE = 60;
    private static final long SECONDS_PER_HOUR = SECONDS_PER_MINUTE * 60;
    private static final long SECONDS_PER_DAY = SECONDS_PER_HOUR * 24;


    public ClaimContestCommand(Block4Block plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.claimContestConfig = plugin.cfg.getClaimContest(); // Assuming plugin.cfg handles loading/saving
        this.claimDataConfig = plugin.cfg.getClaimData();       // Assuming plugin.cfg handles loading/saving
        this.scoreboardManager = Bukkit.getScoreboardManager();
        this.scheduler = Bukkit.getScheduler();
        this.pluginManager = Bukkit.getPluginManager();

        Objects.requireNonNull(this.claimContestConfig, "ClaimContest config cannot be null");
        Objects.requireNonNull(this.claimDataConfig, "ClaimData config cannot be null");
        Objects.requireNonNull(this.scoreboardManager, "ScoreboardManager cannot be null");
        Objects.requireNonNull(this.scheduler, "Scheduler cannot be null");
        Objects.requireNonNull(this.pluginManager, "PluginManager cannot be null");

        // Register listener for scoreboard cleanup and claim events
        this.pluginManager.registerEvents(this, plugin);

        // Resume contest if server restarted mid-contest
        loadAndResumeContest();
    }

    // Added public getter for the current contest claim ID
    public String getCurrentContestClaimId() {
        return currentContestClaimId;
    }

    // Added public getter for the current phase
    public Phase getCurrentPhase() {
        return currentPhase;
    }

    // Added public getter for modeHold flag
    public boolean isModeHold() {
        return modeHold;
    }


    // --- Command Handling ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            sendCurrentContestStatus(player); // Show status if no args
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Prevent config changes while *any* phase is active
        boolean configLocked = currentPhase != Phase.PENDING && currentPhase != Phase.FINISHED;
        List<String> configCommands = Arrays.asList("chunk", "loc", "at", "xyz", "duration", "time", "prize", "mode");

        if (configLocked && configCommands.contains(subCommand)) {
            player.sendMessage(ChatColor.RED + "Cannot change configuration while a contest is active (" + currentPhase + "). Use /claimcontest cancel first.");
            return true;
        }

        try {
            boolean success = false;
            switch (subCommand) {
                case "help":
                    success = handleHelpCommand(player);
                    break;
                case "chunk": // Aliases handled in main switch
                case "loc":
                case "at":
                case "xyz":
                    success = handleChunkCommand(player, args);
                    break;
                case "duration": // Standard duration
                case "time":
                    success = handleDurationCommand(player, args);
                    break;
                case "prize":
                    success = handlePrizeCommand(player, args);
                    break;
                case "mode": // Handle mode flags and their specific durations
                    success = handleModeCommand(player, args);
                    break;
                case "start":
                    success = handleStartCommand(player);
                    break;
                case "cancel":
                    success = handleCancelCommand(player);
                    break;
                case "status":
                    success = sendCurrentContestStatus(player);
                    break;
                default:
                    player.sendMessage(ChatColor.RED + "Unknown subcommand. Use '/" + label + " help'.");
                    return false; // Return false to show usage message from plugin.yml
            }

            // If a configuration command was successful, show the current pending state
            if (success && !subCommand.equals("start") && !subCommand.equals("cancel") && !subCommand.equals("status")) {
                setDataValue(CONFIG_SAVE_TIMESTAMP_KEY, System.currentTimeMillis()); // Mark config change time
                plugin.cfg.saveClaimContest(); // Save immediately after any config change
                sendCurrentContestStatus(player);
            }
            return success;

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid number provided: " + e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid argument: " + e.getMessage());
            return false;
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "An unexpected error occurred. Check server logs.");
            plugin.getLogger().severe("Error executing ClaimContestCommand: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    // --- Subcommand Handlers ---

    private boolean handleHelpCommand(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Claim Contest Help ---");
        player.sendMessage(ChatColor.GRAY + "/claimcontest chunk <world> <x> <z> " + ChatColor.WHITE + "- Set location.");
        player.sendMessage(ChatColor.GRAY + "/claimcontest duration <time> " + ChatColor.WHITE + "- Set overall duration (if not using hold mode, e.g., 1h 30m).");
        player.sendMessage(ChatColor.GRAY + "/claimcontest prize <text> " + ChatColor.WHITE + "- Set prize.");
        player.sendMessage(ChatColor.GRAY + "/claimcontest mode <flag> [value] " + ChatColor.WHITE + "- Toggle/set modes:");
        player.sendMessage(ChatColor.GRAY + "  pre_reveal [time] " + ChatColor.WHITE + "- Enable/set pre-reveal phase (e.g., 5m).");
        player.sendMessage(ChatColor.GRAY + "  hold [time] " + ChatColor.WHITE + "- Enable/set hold phase (e.g., 10m).");
        player.sendMessage(ChatColor.GRAY + "  standard " + ChatColor.WHITE + "- Disable special modes (uses main duration).");
        player.sendMessage(ChatColor.GRAY + "/claimcontest start " + ChatColor.WHITE + "- Start the contest.");
        player.sendMessage(ChatColor.GRAY + "/claimcontest cancel " + ChatColor.WHITE + "- Cancel the current contest.");
        player.sendMessage(ChatColor.GRAY + "/claimcontest status " + ChatColor.WHITE + "- Show current status/config.");
        return true;
    }

    private boolean handleChunkCommand(Player player, String[] args) {
        // Usage: /claimcontest chunk <dimension> <x> <z>
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /claimcontest chunk <overworld|nether|end> <x> <z>");
            return false;
        }
        // Config change check happens in onCommand

        String dimensionNameInput = args[1].toLowerCase();
        World.Environment environment;
        String chunkDisplayName; // The name to display in the scoreboard

        switch (dimensionNameInput) {
            case "overworld":
                environment = World.Environment.NORMAL;
                chunkDisplayName = "Overworld"; // Keep "Overworld" as is
                break;
            case "nether":
                environment = World.Environment.NETHER;
                chunkDisplayName = "The Nether"; // Use "The Nether"
                break;
            case "end":
                environment = World.Environment.THE_END;
                chunkDisplayName = "The End"; // Use "The End"
                break;
            default:
                player.sendMessage(ChatColor.RED + "Invalid dimension: " + args[1] + ". Use overworld, nether, or end.");
                return false;
        }

        try {
            int x = Integer.parseInt(args[2]);
            int z = Integer.parseInt(args[3]);
            // Use the prepared chunkDisplayName
            String chunkLocString = chunkDisplayName + " (X: " + x + ", Z: " + z + ")";
            String claimId = utils.getClaimID(x, z, environment);

            if (claimId == null) {
                player.sendMessage(ChatColor.RED + "Failed to generate claim ID for the specified location.");
                return true;
            }

            setDataValue(CHUNK_LOC_KEY, chunkLocString);
            setDataValue(CLAIM_ID_KEY, claimId);
            player.sendMessage(ChatColor.GREEN + "Contest location set to: " + chunkLocString);
            return true; // Save happens in onCommand wrapper

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid coordinates: X and Z must be numbers.");
            return false;
        }
    }

    // Handles standard contest duration (used if modeHold is false)
    private boolean handleDurationCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /claimcontest duration <time> (e.g., 1d 2h 30m)");
            return false;
        }

        long durationMillis = parseTimeArgument(Arrays.copyOfRange(args, 1, args.length));
        if (durationMillis < 0) {
            player.sendMessage(ChatColor.RED + "Invalid time format. Use #d, #h, #m (e.g., 1d 2h 30m).");
            return false;
        }
        if (durationMillis == 0) {
            player.sendMessage(ChatColor.RED + "Standard duration must be positive.");
            return true;
        }

        setDataValue(DURATION_KEY, durationMillis);
        player.sendMessage(ChatColor.GREEN + "Contest standard duration set to: " + formatDuration(durationMillis));
        return true;
    }

    private boolean handlePrizeCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /claimcontest prize <description...>");
            return false;
        }
        String prizeDescription = Arrays.stream(args).skip(1).collect(Collectors.joining(" "));
        prizeDescription = ChatColor.translateAlternateColorCodes('&', prizeDescription);

        setDataValue(PRIZE_KEY, prizeDescription);
        player.sendMessage(ChatColor.GREEN + "Contest prize set to: " + prizeDescription);
        return true;
    }

    private boolean handleModeCommand(Player player, String[] args) {
        // Usage: /claimcontest mode <flag> [value]
        // Flags: pre_reveal [time], hold [time], standard
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /claimcontest mode <pre_reveal|hold|standard> [time]");
            return false;
        }

        String flag = args[1].toLowerCase();
        long timeValueMillis = -1;

        // Check for optional time value
        if (args.length > 2) {
            timeValueMillis = parseTimeArgument(Arrays.copyOfRange(args, 2, args.length));
            if (timeValueMillis < 0) {
                player.sendMessage(ChatColor.RED + "Invalid time format for mode duration. Use #d, #h, #m.");
                return false;
            }
            if (timeValueMillis == 0 && !flag.equals("standard")) { // Allow 0 only implicitly for standard/disabling
                player.sendMessage(ChatColor.RED + "Mode duration must be positive.");
                return true;
            }
        }


        switch(flag) {
            case "pre_reveal":
                boolean enabledPre = timeValueMillis != 0; // Enable if time > 0 or if no time specified (use default)
                if (enabledPre && timeValueMillis <= 0) { // Use default if enabling without specific time
                    timeValueMillis = DEFAULT_PRE_REVEAL_MINUTES * MILLIS_PER_MINUTE;
                }
                setDataValue(MODE_PRE_REVEAL_KEY, enabledPre);
                setDataValue(DURATION_PRE_REVEAL_KEY, enabledPre ? timeValueMillis : 0);
                player.sendMessage(ChatColor.GREEN + "Pre-Reveal mode " + (enabledPre ? "enabled (" + formatDuration(timeValueMillis) + ")" : "disabled."));
                break;

            case "hold":
                boolean enabledHold = timeValueMillis != 0;
                if (enabledHold && timeValueMillis <= 0) {
                    timeValueMillis = DEFAULT_HOLD_MINUTES * MILLIS_PER_MINUTE;
                }
                setDataValue(MODE_HOLD_KEY, enabledHold);
                setDataValue(DURATION_HOLD_KEY, enabledHold ? timeValueMillis : 0);
                // Automatically disable standard duration if hold is enabled
                if (enabledHold) {
                    setDataValue(DURATION_KEY, 0L);
                    player.sendMessage(ChatColor.YELLOW + "Standard duration disabled as Hold mode is active.");
                }
                player.sendMessage(ChatColor.GREEN + "Hold mode " + (enabledHold ? "enabled (" + formatDuration(timeValueMillis) + ")" : "disabled."));
                break;

            case "standard":
                // Explicitly disable other modes
                setDataValue(MODE_PRE_REVEAL_KEY, false);
                setDataValue(MODE_HOLD_KEY, false);
                setDataValue(DURATION_PRE_REVEAL_KEY, 0L);
                setDataValue(DURATION_HOLD_KEY, 0L);
                // Ensure standard duration exists if switching to standard
                if (getDataLong(DURATION_KEY) <= 0) {
                    setDataValue(DURATION_KEY, 30 * MILLIS_PER_MINUTE); // Default to 30 mins if none set
                    player.sendMessage(ChatColor.YELLOW + "Set default standard duration (30m). Use /claimcontest duration to change.");
                }
                player.sendMessage(ChatColor.GREEN + "Switched to Standard mode.");
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown mode flag: " + flag + ". Use pre_reveal, hold, or standard.");
                return false;
        }
        return true; // Save happens in onCommand
    }


    private boolean handleStartCommand(Player player) {
        if (currentPhase != Phase.PENDING && currentPhase != Phase.FINISHED) {
            player.sendMessage(ChatColor.RED + "A contest is already active ("+ currentPhase + ")!");
            return true;
        }

        // --- Validate required settings ---
        currentContestClaimId = getDataString(CLAIM_ID_KEY); // Load claim ID for the contest
        contestDurationMillis = getDataLong(DURATION_KEY); // Standard duration
        modePreReveal = getDataBoolean(MODE_PRE_REVEAL_KEY);
        modeHold = getDataBoolean(MODE_HOLD_KEY);
        preRevealDurationMillis = getDataLong(DURATION_PRE_REVEAL_KEY);
        holdDurationMillis = getDataLong(DURATION_HOLD_KEY);
        String chunkLoc = getDataString(CHUNK_LOC_KEY);
        String prize = getDataString(PRIZE_KEY, "None");

        if (currentContestClaimId == null || currentContestClaimId.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Contest location is not set! Use '/claimcontest chunk'.");
            return true;
        }
        // Validate durations based on mode
        if (modeHold && holdDurationMillis <= 0) {
            player.sendMessage(ChatColor.RED + "Hold mode duration is not set or invalid! Use '/claimcontest mode hold <time>'.");
            return true;
        }
        if (!modeHold && contestDurationMillis <= 0) { // Standard duration required if not hold mode
            player.sendMessage(ChatColor.RED + "Contest standard duration is not set or invalid! Use '/claimcontest duration <time>'.");
            return true;
        }
        if (modePreReveal && preRevealDurationMillis <= 0) {
            player.sendMessage(ChatColor.RED + "Pre-Reveal mode duration is not set or invalid! Use '/claimcontest mode pre_reveal <time>'.");
            return true;
        }

        // --- Save the timestamp when the command was issued ---
        setDataValue(COMMAND_START_TIMESTAMP_KEY, System.currentTimeMillis());
        plugin.cfg.saveClaimContest(); // Save immediately

        // --- Start the contest workflow ---
        player.sendMessage(ChatColor.GREEN + "Starting claim contest...");
        startContestWorkflow();
        return true;
    }

    private boolean handleCancelCommand(Player player) {
        if (currentPhase == Phase.PENDING || currentPhase == Phase.FINISHED) {
            player.sendMessage(ChatColor.RED + "No contest is currently active.");
            return true;
        }

        cancelContest(player.getName()); // Pass canceller name
        player.sendMessage(ChatColor.YELLOW + "Claim contest cancelled.");
        return true;
    }

    // --- Contest Workflow & Phase Management ---

    private void startContestWorkflow() {
        // Clear previous runtime data but keep configuration
        clearRuntimeContestData();

        // Set initial state based on modes
        if (modePreReveal) {
            transitionToPhase(Phase.PRE_REVEAL);
        } else {
            transitionToPhase(Phase.ACTIVE); // Start directly into active phase
        }
    }

    private void transitionToPhase(Phase nextPhase) {
        plugin.getLogger().info("Contest transitioning from " + currentPhase + " to " + nextPhase);
        Phase previousPhase = currentPhase;
        currentPhase = nextPhase;

        // Persist phase change
        setDataValue(PHASE_KEY, currentPhase.name());
        plugin.cfg.saveClaimContest();

        // Stop tasks from previous phase (if any)
        if (mainTask != null) {
            plugin.getLogger().info("Cancelling previous mainTask.");
            mainTask.cancel();
            mainTask = null;
        }

        // --- Start logic for the new phase ---
        long now = System.currentTimeMillis();
        switch (currentPhase) {
            case PRE_REVEAL:
                contestEndTimeMillis = now + preRevealDurationMillis; // Use this for pre-reveal end time
                setDataValue(PRE_REVEAL_END_TIMESTAMP_KEY, contestEndTimeMillis);
                plugin.cfg.saveClaimContest();

                Bukkit.broadcastMessage(ChatColor.GOLD + "A CLAIM CONTEST IS STARTING SOON!");
                Bukkit.broadcastMessage(ChatColor.YELLOW + "The exact location will be revealed in " + formatDuration(preRevealDurationMillis));
                // Start the pre-reveal ticker task
                plugin.getLogger().info("Scheduling tickPreReveal task.");
                mainTask = scheduler.runTaskTimer(plugin, this::tickPreReveal, 0L, TICKS_PER_SECOND);
                break;

            case ACTIVE:
                contestStartTimeMillis = now; // Mark the official contest start time (Active phase start)
                setDataValue(START_TIMESTAMP_KEY, contestStartTimeMillis); // Save Active phase start time
                currentHolderName = NO_CLAIMANT; // Reset holder when entering active phase
                setDataValue(CURRENT_HOLDER_KEY, NO_CLAIMANT);
                setDataValue(CURRENT_CLAIMANT_KEY, NO_CLAIMANT); // Also reset standard claimant tracker

                // Announce start (or reveal if coming from pre-reveal)
                if (previousPhase == Phase.PRE_REVEAL) {
                    broadcastExactArea(); // Reveal location
                    Bukkit.broadcastMessage(ChatColor.GOLD + "THE CLAIM CONTEST HAS OFFICIALLY BEGUN!");
                } else {
                    // Starting directly into ACTIVE
                    Bukkit.broadcastMessage(ChatColor.GOLD + "A new Claim Contest has started!");
                    broadcastExactArea(); // Show location immediately
                }
                String prize = getDataString(PRIZE_KEY, "None");
                if (!prize.equals("None")) {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Prize: " + ChatColor.WHITE + prize);
                }

                // Start appropriate ticker based on mode
                if (modeHold) {
                    // In Hold mode, ACTIVE phase just waits for a claim. Ticker checks for claimant changes.
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Mode: Claim and Hold! First to claim must hold it for " + formatDuration(holdDurationMillis) + ".");
                    plugin.getLogger().info("Scheduling tickActiveHoldMode task.");
                    mainTask = scheduler.runTaskTimer(plugin, this::tickActiveHoldMode, 0L, TICKS_PER_SECOND);
                    // Immediately update the scoreboard to show the initial claimant status and teams
                    plugin.getLogger().info("Updating Active Hold Mode scoreboards immediately after phase transition.");
                    updateActiveHoldModeScoreboards();

                    // --- ADDED EDGE CASE CHECK FOR ALREADY CLAIMED CHUNK ---
                    String initialClaimant = getPrimaryClaimantName(currentContestClaimId);
                    if (!initialClaimant.equals(NO_CLAIMANT)) {
                        plugin.getLogger().info("Contest started in ACTIVE (Hold Mode) with chunk already claimed by: " + initialClaimant + ". Directly transitioning to HOLD.");
                        currentHolderName = initialClaimant;
                        // Manually trigger the logic to start the hold timer, similar to onContestChunkClaimed
                        long holdEnds = System.currentTimeMillis() + holdDurationMillis;
                        holdEndTimeMillis.set(holdEnds);
                        setDataValue(HOLD_END_TIMESTAMP_KEY, holdEnds);
                        setDataValue(CURRENT_HOLDER_KEY, currentHolderName);
                        plugin.cfg.saveClaimContest();
                        // Transition to HOLD phase - this will cancel tickActiveHoldMode and start tickHold
                        transitionToPhase(Phase.HOLD);
                    }
                    // --- END ADDED EDGE CASE CHECK ---

                } else {
                    // Standard mode: Fixed duration timer
                    contestEndTimeMillis = contestStartTimeMillis + contestDurationMillis;
                    setDataValue(CONTEST_END_TIMESTAMP_KEY, contestEndTimeMillis);
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Mode: Standard! Contest ends in " + formatDuration(contestDurationMillis) + ".");
                    plugin.getLogger().info("Scheduling tickStandard task.");
                    mainTask = scheduler.runTaskTimer(plugin, this::tickStandard, 0L, TICKS_PER_SECOND);
                }
                plugin.cfg.saveClaimContest();
                break;

            case HOLD: // Transition to HOLD happens via onContestChunkClaimed event handler
                plugin.getLogger().info("Transitioning to HOLD phase.");
                // Logic is handled there and in tickHold
                // Ensure holder name is set before entering this phase
                long holdEnds = now + holdDurationMillis;
                holdEndTimeMillis.set(holdEnds);
                setDataValue(HOLD_END_TIMESTAMP_KEY, holdEnds);
                setDataValue(CURRENT_HOLDER_KEY, currentHolderName); // Persist holder
                plugin.cfg.saveClaimContest();

                Bukkit.broadcastMessage(ChatColor.GOLD + currentHolderName + " has claimed the Contest Area!");
                Bukkit.broadcastMessage(ChatColor.YELLOW + "They must hold it for " + formatDuration(holdDurationMillis) + " to win!");
                // Start the hold timer task
                plugin.getLogger().info("Scheduling tickHold task for holder: " + currentHolderName);
                mainTask = scheduler.runTaskTimer(plugin, this::tickHold, 0L, TICKS_PER_SECOND);
                // The updateHoldScoreboards is called by tickHold, so no immediate call needed here.
                break;

            case FINISHED:
                plugin.getLogger().info("Transitioning to FINISHED phase. Cancelling contest.");
                // Clean up tasks, clear runtime data, handle winner announcement (done in handleContestEnd)
                cancelContest(null); // Use null for natural finish
                break;

            case PENDING:
                // Should not transition *to* pending during runtime, only on init/cancel
                plugin.getLogger().warning("Attempted to transition to PENDING phase during runtime.");
                break;
        }
    }

    // --- Ticker Methods (Called every second by mainTask) ---

    private void tickPreReveal() {
        long now = System.currentTimeMillis();
        if (now >= contestEndTimeMillis) { // contestEndTimeMillis stores pre-reveal end time here
            // Pre-reveal finished, transition to Active
            transitionToPhase(Phase.ACTIVE);
        } else {
            // Update pre-reveal scoreboards
            updatePreRevealScoreboards(contestEndTimeMillis - now);
        }
    }

    private void tickStandard() {
        long now = System.currentTimeMillis();
        if (now >= contestEndTimeMillis) { // contestEndTimeMillis stores standard contest end time here
            // Standard contest finished
            String finalClaimant = getPrimaryClaimantName(currentContestClaimId); // Check who holds it at the end
            handleContestEnd(finalClaimant);
            // transitionToPhase(Phase.FINISHED); // Called within handleContestEnd -> cancelContest
        } else {
            // Update standard scoreboards and check for claimant changes
            updateStandardScoreboards(contestEndTimeMillis - now);
            checkStandardClaimantChange(); // Handles broadcast/event for standard mode claim changes
        }
    }

    // Ticks during ACTIVE phase *when in Hold mode*. Just updates scoreboard and waits for claim.
    private void tickActiveHoldMode() {
        updateActiveHoldModeScoreboards();
        // No timer, just waits for the ContestChunkClaimedEvent
        // Could add a check here for an overall contest timeout if desired
    }

    private void tickHold() {
        if (contestEnded) {
            return; // contest already ended, don't do anything
        }

        long now = System.currentTimeMillis();
        long holdEnds = holdEndTimeMillis.get();
        long millisLeft = holdEnds - now;

        plugin.getLogger().info("tickHold running. Time left: " + formatTimeLeft(millisLeft));

        if (now >= holdEnds) {
            plugin.getLogger().info("Hold time completed. Ending contest for holder: " + currentHolderName);
            handleContestEnd(currentHolderName);
            return;
        }

        // Check if the current holder STILL holds the claim
        String actualClaimant = getPrimaryClaimantName(currentContestClaimId);
        if (!actualClaimant.equalsIgnoreCase(currentHolderName)) {
            // Holder lost the claim!
            plugin.getLogger().info(currentHolderName + " lost the claim (" + currentContestClaimId + "). Actual claimant is now: " + actualClaimant + ". Resetting hold timer.");
            Bukkit.broadcastMessage(ChatColor.YELLOW + currentHolderName + " lost the claim! The hold timer has reset.");
            currentHolderName = NO_CLAIMANT; // Reset holder
            setDataValue(CURRENT_HOLDER_KEY, NO_CLAIMANT);
            holdEndTimeMillis.set(-1);
            setDataValue(HOLD_END_TIMESTAMP_KEY, -1);
            plugin.cfg.saveClaimContest();
            // Go back to ACTIVE phase to wait for a new claim
            transitionToPhase(Phase.ACTIVE); // This will cancel the current tickHold task and start tickActiveHoldMode
        } else {
            // Still holding, update scoreboard with remaining time
            updateHoldScoreboards(millisLeft); // This is called every second to update the timer
        }
    }

    // --- Event Handlers ---

    @EventHandler
    public void onContestChunkClaimed(ContestChunkClaimedEvent event) {
        // --- START DEBUG LOGS ---
        plugin.getLogger().info("[DEBUG] onContestChunkClaimed: Event received for claimant: " + event.claimant); //
        plugin.getLogger().info("[DEBUG] onContestChunkClaimed: Current Phase (instance var): " + currentPhase); //
        plugin.getLogger().info("[DEBUG] onContestChunkClaimed: Mode Hold (instance var): " + modeHold); //
        // --- END DEBUG LOGS ---

        if (currentPhase == Phase.ACTIVE && modeHold) { //
            String activeContestClaimId = getDataString(CLAIM_ID_KEY); //
            if (activeContestClaimId == null || activeContestClaimId.isEmpty()) { //
                plugin.getLogger().warning("ContestChunkClaimedEvent fired in ACTIVE/HOLD but CLAIM_ID_KEY is missing!"); //
                return; //
            }

            // --- START DEBUG LOGS ---
            plugin.getLogger().info("[DEBUG] onContestChunkClaimed: Checking Contest Claim ID: " + activeContestClaimId); //
            String ownerInfoRaw = claimDataConfig.getString(activeContestClaimId + ".members"); //
            plugin.getLogger().info("[DEBUG] onContestChunkClaimed: Raw members string from config: '" + ownerInfoRaw + "'"); //
            // --- END DEBUG LOGS ---

            String actualCurrentClaimant = getPrimaryClaimantName(activeContestClaimId); // Modified to use primary claimant
            plugin.getLogger().info("Actual current claimant of contest chunk " + activeContestClaimId + ": " + actualCurrentClaimant); //

            // --- START DEBUG LOGS ---
            plugin.getLogger().info("[DEBUG] onContestChunkClaimed: Current Holder Name (before check): " + currentHolderName); //
            // --- END DEBUG LOGS ---

            if (!actualCurrentClaimant.equals(NO_CLAIMANT) && !actualCurrentClaimant.equalsIgnoreCase(currentHolderName)) { //
                // --- START DEBUG LOGS ---
                plugin.getLogger().info("[DEBUG] onContestChunkClaimed: Condition PASSED. Transitioning to HOLD."); //
                // --- END DEBUG LOGS ---
                currentHolderName = actualCurrentClaimant; //
                transitionToPhase(Phase.HOLD); //
            } else {
                // --- START DEBUG LOGS ---
                plugin.getLogger().info("[DEBUG] onContestChunkClaimed: Condition FAILED. No transition."); //
                if (actualCurrentClaimant.equals(NO_CLAIMANT)) { //
                    plugin.getLogger().info("[DEBUG] onContestChunkClaimed: Reason: actualCurrentClaimant is NO_CLAIMANT."); //
                } else if (actualCurrentClaimant.equalsIgnoreCase(currentHolderName)) { //
                    plugin.getLogger().info("[DEBUG] onContestChunkClaimed: Reason: actualCurrentClaimant matches currentHolderName."); //
                }
                // --- END DEBUG LOGS ---
                // Existing fine logging based on specific failure reason
                if (actualCurrentClaimant.equals(NO_CLAIMANT)) { //
                    plugin.getLogger().fine("ContestChunkClaimedEvent fired, but contest chunk " + activeContestClaimId + " is still unclaimed according to claim data."); //
                } else { // Must be actualCurrentClaimant.equalsIgnoreCase(currentHolderName) //
                    plugin.getLogger().fine("ContestChunkClaimedEvent fired, but " + actualCurrentClaimant + " is already the current holder."); //
                }
            }
        } else {
            // --- START DEBUG LOGS ---
            plugin.getLogger().info("[DEBUG] onContestChunkClaimed: Event ignored. Phase (" + currentPhase + ") not ACTIVE or not Hold mode ("+ modeHold +")."); //
            // --- END DEBUG LOGS ---
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up scoreboard for quitting player
        clearPlayerScoreboard(event.getPlayer());
        plugin.getLogger().info("Cleared scoreboard for quitting player: " + event.getPlayer().getName());
        // Also remove from any contest teams
        removePlayerFromContestTeams(event.getPlayer());
    }

    private void removePlayerFromContestTeams(Player player) {
        // When a player quits, attempt to remove them from all possible contest teams
        // This helps prevent issues if the main contest scoreboard isn't the main server scoreboard
        Scoreboard currentBoard = player.getScoreboard(); // Get the player's current scoreboard
        if (currentBoard == null || currentBoard == scoreboardManager.getMainScoreboard()) {
            // If they have the main scoreboard, attempt to clear from teams on the main board
            currentBoard = scoreboardManager.getMainScoreboard();
        }
        // Attempt to get the teams from the current scoreboard
        Team claimantTeam = currentBoard.getTeam(TEAM_CLAIMANT);
        Team defenderTeam = currentBoard.getTeam(TEAM_DEFENDER);
        Team intruderTeam = currentBoard.getTeam(TEAM_INTRUDER);

        if (claimantTeam != null) claimantTeam.removeEntry(player.getName());
        if (defenderTeam != null) defenderTeam.removeEntry(player.getName());
        if (intruderTeam != null) intruderTeam.removeEntry(player.getName());
    }


    // --- Contest End/Cancel Logic ---

    private void handleContestEnd(String winnerName) {
        if (contestEnded) return; // avoid double ending
        contestEnded = true;
        Phase phaseEnded = currentPhase; // Store phase before cancelling
        plugin.getLogger().info("Contest ending. Winner: " + winnerName + " (Ended from Phase: " + phaseEnded + ")");

        // Record history happens before cleanup
        // --- Record history ---
        String chunkLoc = getDataString(CHUNK_LOC_KEY, "Unknown");
        String prize = getDataString(PRIZE_KEY, "None");
        // Use the timestamp from when handleContestEnd is called as the end time
        long contestEndTimeMillisActual = System.currentTimeMillis();
        String endDateFormatted = HISTORY_DATE_FORMAT.format(LocalDateTime.now());
        String historyPath = HISTORY_SECTION + "." + endDateFormatted;

        // Retrieve the command start time from config
        long commandStartTimeMillis = getDataLong(COMMAND_START_TIMESTAMP_KEY);

        // Calculate duration if command start time was recorded
        String calculatedDuration = "N/A";
        if (commandStartTimeMillis > 0) {
            long durationMillis = contestEndTimeMillisActual - commandStartTimeMillis;
            if (durationMillis >= 0) { // Ensure duration is not negative due to clock issues
                calculatedDuration = formatDuration(durationMillis);
            } else {
                calculatedDuration = "Error: Negative Duration";
                plugin.getLogger().warning("Calculated negative contest duration for history. Command start time: " + commandStartTimeMillis + ", End time: " + contestEndTimeMillisActual);
            }
        } else {
            plugin.getLogger().warning("Contest command start time not found in config for history entry.");
        }


        claimContestConfig.set(historyPath + ".location", chunkLoc);
        claimContestConfig.set(historyPath + ".winner", winnerName);
        claimContestConfig.set(historyPath + ".prize", prize);
        claimContestConfig.set(historyPath + ".mode.pre-reveal", getDataBoolean(MODE_PRE_REVEAL_KEY));
        claimContestConfig.set(historyPath + ".mode.hold", getDataBoolean(MODE_HOLD_KEY));
        // Save the configured durations for context, not the actual elapsed time here (that's calculated_duration)
        claimContestConfig.set(historyPath + ".config_duration.standard", formatDuration(getDataLong(DURATION_KEY)));
        claimContestConfig.set(historyPath + ".config_duration.pre-reveal", formatDuration(getDataLong(DURATION_PRE_REVEAL_KEY)));
        claimContestConfig.set(historyPath + ".config_duration.hold", formatDuration(getDataLong(DURATION_HOLD_KEY)));
        claimContestConfig.set(historyPath + ".ended_phase", phaseEnded.name());
        // Save formatted start (from command time) and end dates, and calculated duration
        claimContestConfig.set(historyPath + "." + HISTORY_START_DATE_KEY, commandStartTimeMillis > 0 ? HISTORY_DATE_FORMAT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(commandStartTimeMillis), ZoneId.systemDefault())) : "N/A");
        claimContestConfig.set(historyPath + "." + HISTORY_END_DATE_KEY, endDateFormatted); // Use the time this method was called
        claimContestConfig.set(historyPath + "." + HISTORY_CALCULATED_DURATION_KEY, calculatedDuration);


        // Save happens in cancelContest

        // --- Announce Winner ---
        Bukkit.broadcastMessage(ChatColor.GOLD + "THE CONTEST HAS ENDED!");
        if (!winnerName.equals(NO_CLAIMANT)) {
            Player winnerPlayer = Bukkit.getPlayerExact(winnerName);
            String finalWinnerName = (winnerPlayer != null) ? winnerPlayer.getName() : winnerName; // Use correct case if online

            Bukkit.broadcastMessage("Location: " + ChatColor.AQUA + chunkLoc);
            Bukkit.broadcastMessage("And the winner is... " + ChatColor.GREEN + finalWinnerName + "!");
            if (!prize.equals("None")) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Prize: " + ChatColor.WHITE + prize);
            }
            if (!calculatedDuration.equals("N/A") && !calculatedDuration.contains("Error")) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Duration: " + ChatColor.WHITE + calculatedDuration);
            }


            // Call custom event for the winner
            if (winnerPlayer != null) {
                pluginManager.callEvent(new ClaimContestOverEvent(winnerPlayer)); // Assuming this event exists
            } else {
                plugin.getLogger().info("Contest winner " + finalWinnerName + " is offline. Prize: " + prize + (calculatedDuration.equals("N/A") ? "" : ", Duration: " + calculatedDuration));
            }

        } else {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "No one secured the claim when the contest ended!");
            if (!calculatedDuration.equals("N/A") && !calculatedDuration.contains("Error")) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Duration: " + ChatColor.WHITE + calculatedDuration);
            }
        }

        clearContestTeams(); // Clear teams before final cleanup
        // --- Cleanup ---
        cancelContest(null); // Final cleanup, null signifies natural end
    }


    private void cancelContest(String cancelledBy) {
        contestEnded = false; // Reset flag for next contest
        if (currentPhase == Phase.PENDING || currentPhase == Phase.FINISHED) return; // Already stopped

        Phase phaseCancelled = currentPhase;
        plugin.getLogger().info("Cancelling contest. Current phase: " + phaseCancelled + (cancelledBy != null ? " by " + cancelledBy : ""));

        if (mainTask != null) {
            mainTask.cancel();
            mainTask = null;
        }

        // Reset state variables
        currentPhase = Phase.FINISHED; // Mark as finished immediately
        currentContestClaimId = null;
        currentHolderName = NO_CLAIMANT;
        contestStartTimeMillis = -1; // Active phase start time
        contestEndTimeMillis = -1; // Standard or pre-reveal end time
        holdEndTimeMillis.set(-1); // Hold phase end time

        // Clear runtime data from config, keeping setup config and history
        clearRuntimeContestData();
        plugin.cfg.saveClaimContest(); // Save cleared data and any history written

        // Clear scoreboards
        clearAllScoreboards();
        clearContestTeams(); // Clear teams

        if (cancelledBy != null) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "The claim contest was cancelled by " + cancelledBy + ".");
        }
    }

    // --- Scoreboard Management ---

    // Update scoreboard during Pre-Reveal phase
    private void updatePreRevealScoreboards(long millisLeft) {
        String timeLeftFormatted = formatTimeLeft(millisLeft);
        String approxLocation = getApproximateLocationString(); // Get obfuscated location

        Scoreboard board = scoreboardManager.getNewScoreboard();
        Objective sidebar = board.registerNewObjective(SCOREBOARD_OBJECTIVE_NAME, "dummy", SCOREBOARD_TITLE);
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);

        sidebar.getScore(ChatColor.WHITE + "Contest starting soon!").setScore(5);
        sidebar.getScore(" ").setScore(4);
        sidebar.getScore(ChatColor.YELLOW + "Approx. Location:").setScore(3);
        sidebar.getScore(ChatColor.WHITE + approxLocation).setScore(2);
        sidebar.getScore("  ").setScore(1);
        sidebar.getScore(ChatColor.YELLOW + "Revealing in: " + ChatColor.WHITE + timeLeftFormatted).setScore(0);

        setBoardForAllPlayers(board);
        // No teams needed during pre-reveal as claimant is unknown
    }

    // Update scoreboard during standard fixed-duration contest
    private void updateStandardScoreboards(long millisLeft) {
        String timeLeftFormatted = formatTimeLeft(millisLeft);
        String chunkLoc = getDataString(CHUNK_LOC_KEY, "Unknown");
        String prize = getDataString(PRIZE_KEY, "None");
        // Use primary claimant here for display purposes if needed, teams handle below-name
        // String currentClaimant = getPrimaryClaimantName(currentContestClaimId);

        // Scoreboard for all players (teams handle the distinction below name)
        Scoreboard board = createScoreboardBase(SCOREBOARD_TITLE); // Create one board
        Objective sidebar = board.getObjective(DisplaySlot.SIDEBAR);
        // Split location into World and Coordinates
        String[] locationParts = chunkLoc.split(" \\(X: ");
        String worldName = locationParts[0];
        String coords = "(X: " + locationParts[1];

        sidebar.getScore(ChatColor.WHITE + "Location:").setScore(5);
        sidebar.getScore(getWorldColor(worldName) + worldName).setScore(4);
        sidebar.getScore(ChatColor.WHITE + coords).setScore(3);
        sidebar.getScore(" ").setScore(2);
        sidebar.getScore(ChatColor.YELLOW + "Time Left: " + ChatColor.WHITE + timeLeftFormatted).setScore(1);
        sidebar.getScore(ChatColor.YELLOW + "Prize: " + ChatColor.WHITE + prize).setScore(0);

        // Update player teams based on who is claimant, defender, or intruder on THIS board
        updatePlayerTeams(board, currentContestClaimId);

        // Assign the same board to all players, teams manage the below-name text
        setBoardForAllPlayers(board);
    }

    // Update scoreboard during ACTIVE phase when in HOLD mode (waiting for claim)
    private void updateActiveHoldModeScoreboards() {
        String chunkLoc = getDataString(CHUNK_LOC_KEY, "Unknown");
        String prize = getDataString(PRIZE_KEY, "None");
        // String holdDurationFormatted = formatDuration(holdDurationMillis); // Not used in sidebar for this phase
        // Fetch the current claimant for the contested chunk
        String currentClaimant = getPrimaryClaimantName(currentContestClaimId); // Use primary claimant
        // Determine the text to display based on whether the chunk is claimed in the sidebar
        String claimantStatus = currentClaimant.equals(NO_CLAIMANT) ? ChatColor.WHITE + "Claimant: " + ChatColor.GRAY + "Unclaimed" : ChatColor.YELLOW + "Claimant: " + ChatColor.GREEN + currentClaimant;


        Scoreboard board = createScoreboardBase(SCOREBOARD_TITLE);
        Objective sidebar = board.getObjective(DisplaySlot.SIDEBAR);

        String[] locationParts = chunkLoc.split(" \\(X: ");
        String worldName = locationParts[0];
        String coords = "(X: " + locationParts[1];

        sidebar.getScore(ChatColor.WHITE + "Location:").setScore(6);
        sidebar.getScore(getWorldColor(worldName) + worldName).setScore(5);
        sidebar.getScore(ChatColor.WHITE + coords).setScore(4);
        sidebar.getScore(" ").setScore(3);
        sidebar.getScore(ChatColor.YELLOW + "Mode: " + ChatColor.AQUA + "Claim and Hold").setScore(2);
        sidebar.getScore(claimantStatus).setScore(1); // Display claimant status (claimed or unclaimed)
        sidebar.getScore(ChatColor.YELLOW + "Prize: " + ChatColor.WHITE + prize).setScore(0);

        // Update player teams based on who is claimant, defender, or intruder on THIS board
        updatePlayerTeams(board, currentContestClaimId); // Pass currentClaimantId (or contestedClaimId)

        setBoardForAllPlayers(board);
    }

    // Update scoreboard during HOLD phase (someone has claimed)
    private void updateHoldScoreboards(long millisLeft) {
        String timeLeftFormatted = formatTimeLeft(millisLeft); // This formats the remaining time
        String chunkLoc = getDataString(CHUNK_LOC_KEY, "Unknown");
        String prize = getDataString(PRIZE_KEY, "None");
        // currentHolderName should be set when entering HOLD phase

        // Scoreboard for all players (teams handle the distinction below name)
        Scoreboard board = createScoreboardBase(ChatColor.GOLD + "Hold the Claim!"); // Create one board
        Objective sidebar = board.getObjective(DisplaySlot.SIDEBAR);
        sidebar.getScore(" ").setScore(6); // Adjusted scores
        // Split location into World and Coordinates
        String[] locationParts = chunkLoc.split(" \\(X: ");
        String worldName = locationParts[0];
        String coords = "(X: " + locationParts[1];
        sidebar.getScore(ChatColor.WHITE + "Location:").setScore(8);
        sidebar.getScore(getWorldColor(worldName) + worldName).setScore(7);
        sidebar.getScore(ChatColor.WHITE + coords).setScore(6);
        sidebar.getScore(ChatColor.YELLOW + "Holder: " + ChatColor.GREEN + currentHolderName).setScore(5); // Shows who is holding in sidebar
        sidebar.getScore(ChatColor.YELLOW + "Hold Time Left: " + ChatColor.WHITE + timeLeftFormatted).setScore(4); // Displays the ticking timer for others in sidebar
        sidebar.getScore(" ").setScore(3);
        sidebar.getScore(ChatColor.YELLOW + "Prize: " + ChatColor.WHITE + prize).setScore(2);


        // Update player teams based on who is claimant, defender, or intruder on THIS board
        updatePlayerTeams(board, currentContestClaimId);

        // Assign the same board to all players, teams manage the below-name text
        // Added logging for board assignment
        plugin.getLogger().info("updateHoldScoreboards: Created board. Assigning scoreboards and updating teams.");
        setBoardForAllPlayers(board);
    }

    private ChatColor getWorldColor(String worldName) {
        switch (worldName) { // Check for the stored display name
            case "Overworld":
                return ChatColor.GREEN;
            case "The Nether":
                return ChatColor.RED;
            case "The End":
                return ChatColor.AQUA;
            // Fallback cases for environment names if needed elsewhere, though using the display name is preferred for consistency
            case "world":
            case "world_nether":
            case "world_the_end":
                // Log a warning if these unexpected values are being used here
                plugin.getLogger().warning("getWorldColor received unexpected internal world name: " + worldName);
                return ChatColor.WHITE;
            default:
                return ChatColor.WHITE;
        }
    }


    private Scoreboard createScoreboardBase(String title) {
        // Use the main scoreboard manager to get a scoreboard instance
        Scoreboard board = scoreboardManager.getNewScoreboard(); // Get a new board for this contest instance
        Objective sidebar = board.registerNewObjective(SCOREBOARD_OBJECTIVE_NAME, "dummy", title);
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        return board;
    }

    // Helper method to get the contest chunk's Location object
    private Location getContestChunkLocation() {
        String chunkLocString = getDataString(CHUNK_LOC_KEY);
        if (chunkLocString == null || chunkLocString.isEmpty() || chunkLocString.equals(ChatColor.RED + "Not Set")) {
            return null; // Location not set
        }
        try {
            // Expected format: "Dimension (X: x, Z: z)"
            String[] parts = chunkLocString.split(" \\(X: ");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid chunkLoc format");

            String dimensionName = parts[0].trim();
            String coordsPart = parts[1].replace("X: ", "").replace("Z: ", "").replace(")", "").trim();
            String[] coords = coordsPart.split(", ");
            if (coords.length != 2) throw new IllegalArgumentException("Invalid coords format");

            // Map dimension name to actual world name
            String worldName;
            switch (dimensionName) {
                case "Overworld":
                    worldName = "world"; // Assuming default world name
                    break;
                case "The Nether":
                    worldName = "world_nether"; // Assuming default nether name
                    break;
                case "The End":
                    worldName = "world_the_end"; // Assuming default end name
                    break;
                default:
                    plugin.getLogger().warning("Unknown dimension name in chunkLoc: " + dimensionName);
                    return null; // Unknown dimension
            }

            // Get the World using the world name
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Could not find world with name: " + worldName);
                return null; // World not found
            }

            // Chunk coordinates x, z are block coordinates x*16, z*16 for center/corner
            // Let's use the block coordinates of the chunk corner for distance calculation
            int chunkX = Integer.parseInt(coords[0]) * 16;
            int chunkZ = Integer.parseInt(coords[1]) * 16;

            // To get a rough center, add half a chunk size (8 blocks) and a middle Y
            int blockX = chunkX + 8;
            int blockZ = chunkZ + 8;
            int blockY = world.getHighestBlockYAt(blockX, blockZ); // Use highest block at this x,z

            return new Location(world, blockX, blockY, blockZ);

        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Failed to parse chunk coordinates from chunkLoc: " + chunkLocString);
            return null;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid chunkLoc format: " + chunkLocString + " - " + e.getMessage());
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("An unexpected error occurred parsing chunkLoc: " + chunkLocString + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // Helper method to get or create a team
    private Team getOrCreateTeam(Scoreboard board, String teamName, String prefix, ChatColor color) {
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
            team.setPrefix(color + prefix + ChatColor.RESET + " "); // Added space for readability
            team.setColor(color); // Can set name color too
            team.setAllowFriendlyFire(true); // Adjust friendly fire as needed
            team.setCanSeeFriendlyInvisibles(false); // Adjust visibility as needed
        }
        return team;
    }

    // Helper method to update player teams based on contest status and proximity (with Claimant always visible, proximity in chunks)
    private void updatePlayerTeams(Scoreboard board, String contestedClaimId) {
        plugin.getLogger().info("updatePlayerTeams: Starting team update.");
        if (contestedClaimId == null || contestedClaimId.isEmpty()) {
            plugin.getLogger().warning("updatePlayerTeams: contestedClaimId is null or empty. Cannot update teams.");
            return;
        }

        String primaryClaimant = getPrimaryClaimantName(contestedClaimId);
        List<String> claimMembers = getClaimMembers(contestedClaimId);
        plugin.getLogger().info("updatePlayerTeams: Primary Claimant for " + contestedClaimId + ": " + primaryClaimant);
        plugin.getLogger().info("updatePlayerTeams: Claim Members for " + contestedClaimId + ": " + claimMembers);


        // Parse contested chunk location to get World and block coordinates
        World contestWorld = null;
        int contestBlockX = Integer.MAX_VALUE; // Initialize with invalid values
        int contestBlockZ = Integer.MAX_VALUE;

        String chunkLocString = getDataString(CHUNK_LOC_KEY);
        if (chunkLocString != null && !chunkLocString.isEmpty() && !chunkLocString.equals(ChatColor.RED + "Not Set")) {
            try {
                // Expected format: "Dimension (X: x, Z: z)"
                String[] parts = chunkLocString.split(" \\(X: ");
                if (parts.length == 2) {
                    String dimensionName = parts[0].trim();
                    String coordsPart = parts[1].replace("X: ", "").replace("Z: ", "").replace(")", "").trim();
                    String[] coords = coordsPart.split(", ");
                    if (coords.length == 2) {
                        // Map dimension name to actual world name string
                        String worldName = null;
                        switch (dimensionName) {
                            case "Overworld":
                                worldName = "world"; // Assuming default overworld name
                                break;
                            case "The Nether":
                                worldName = "world_nether"; // Assuming default nether name
                                break;
                            case "The End":
                                worldName = "world_the_end"; // Assuming default end name
                                break;
                            default:
                                plugin.getLogger().warning("updatePlayerTeams: Unknown dimension name in chunkLoc for teams proximity: " + dimensionName);
                                worldName = null; // Explicitly set to null if unknown
                                break;
                        }

                        if(worldName != null) {
                            // Get world by name
                            contestWorld = Bukkit.getWorld(worldName);
                            if (contestWorld == null) {
                                plugin.getLogger().warning("updatePlayerTeams: Could not find world for name: " + worldName + " (from dimension: " + dimensionName + ") for teams proximity check.");
                            } else {
                                plugin.getLogger().info("updatePlayerTeams: Contest world identified: " + contestWorld.getName());
                            }
                        }

                        // FIXED: These are now treated as block coordinates directly, not chunk coordinates
                        contestBlockX = Integer.parseInt(coords[0]);
                        contestBlockZ = Integer.parseInt(coords[1]);
                        plugin.getLogger().info("updatePlayerTeams: Contest block coords (X, Z): " + contestBlockX + ", " + contestBlockZ);

                    } else { plugin.getLogger().warning("updatePlayerTeams: Invalid coords format in chunkLoc for teams proximity: " + chunkLocString); }
                } else { plugin.getLogger().warning("updatePlayerTeams: Invalid chunkLoc format for teams proximity: " + chunkLocString); }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("updatePlayerTeams: Failed to parse chunk coordinates from chunkLoc for teams proximity: " + chunkLocString);
            } catch (Exception e) {
                plugin.getLogger().warning("updatePlayerTeams: An unexpected error occurred parsing chunkLoc for teams proximity: " + chunkLocString + " - " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            plugin.getLogger().warning("updatePlayerTeams: chunkLocString is null, empty, or not set. Cannot perform proximity check.");
        }


        Team claimantTeam = getOrCreateTeam(board, TEAM_CLAIMANT, "Claimant", ChatColor.GOLD);
        Team defenderTeam = getOrCreateTeam(board, TEAM_DEFENDER, "Defender", ChatColor.BLUE);
        Team intruderTeam = getOrCreateTeam(board, TEAM_INTRUDER, "Intruder", ChatColor.RED);

        // Ensure teams were created successfully before proceeding
        if (claimantTeam == null || defenderTeam == null || intruderTeam == null) {
            plugin.getLogger().severe("updatePlayerTeams: Failed to get or create all required teams. Aborting team assignment.");
            return;
        }

        plugin.getLogger().info("updatePlayerTeams: Iterating through online players.");
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerName = player.getName();
            plugin.getLogger().info("updatePlayerTeams: Processing player: " + playerName);

            // Always remove from all contest teams first to ensure correct assignment
            // This ensures players who move OUT of proximity lose their tag
            plugin.getLogger().info("updatePlayerTeams: Attempting to remove " + playerName + " from all contest teams.");
            if (claimantTeam.hasEntry(playerName)) { claimantTeam.removeEntry(playerName); plugin.getLogger().info(playerName + " removed from " + TEAM_CLAIMANT); }
            if (defenderTeam.hasEntry(playerName)) { defenderTeam.removeEntry(playerName); plugin.getLogger().info(playerName + " removed from " + TEAM_DEFENDER); }
            if (intruderTeam.hasEntry(playerName)) { intruderTeam.removeEntry(playerName); plugin.getLogger().info(playerName + " removed from " + TEAM_INTRUDER); }

            // --- Claimant Exception: Always assign Claimant team if they are the primary claimant ---
            if (playerName.equalsIgnoreCase(primaryClaimant)) {
                plugin.getLogger().info("updatePlayerTeams: " + playerName + " is the primary claimant. Assigning to Claimant team.");
                claimantTeam.addEntry(playerName);
                continue; // Move to the next player after assigning Claimant team
            }
            // --- End Claimant Exception ---


            // --- Proximity Check (for non-claimants) ---
            boolean isInProximity = false;
            // Only perform proximity check if the contest world was successfully identified and contest block coords are valid
            if (contestWorld != null && player.getWorld().equals(contestWorld) && contestBlockX != Integer.MAX_VALUE && contestBlockZ != Integer.MAX_VALUE) {
                plugin.getLogger().info("updatePlayerTeams: " + playerName + " is in the contest world (" + contestWorld.getName() + "). Checking proximity.");

                // FIXED: Get the player's block coordinates directly
                int playerBlockX = player.getLocation().getBlockX();
                int playerBlockZ = player.getLocation().getBlockZ();
                plugin.getLogger().info("updatePlayerTeams: " + playerName + "'s block coords (X, Z): " + playerBlockX + ", " + playerBlockZ);

                // Calculate distance in blocks (using absolute difference)
                int deltaX = Math.abs(playerBlockX - contestBlockX);
                int deltaZ = Math.abs(playerBlockZ - contestBlockZ);

                // Convert chunk radius to block radius (16 blocks per chunk)
                int proximityRadiusBlocks = PROXIMITY_RADIUS_CHUNKS * 16;
                plugin.getLogger().info("updatePlayerTeams: Proximity check for " + playerName + ". Delta X: " + deltaX + ", Delta Z: " + deltaZ + ", Radius in blocks: " + proximityRadiusBlocks);

                if (deltaX <= proximityRadiusBlocks && deltaZ <= proximityRadiusBlocks) {
                    isInProximity = true;
                    plugin.getLogger().info("updatePlayerTeams: " + playerName + " IS within proximity radius.");
                } else {
                    plugin.getLogger().info("updatePlayerTeams: " + playerName + " is NOT within proximity radius.");
                }
            } else {
                plugin.getLogger().info("updatePlayerTeams: " + playerName + " is not in the contest world or contest location is invalid. Skipping proximity check.");
            }
            // --- End Proximity Check ---


            if (isInProximity) {
                // Assign Defender or Intruder team only if the player is within proximity
                plugin.getLogger().info("updatePlayerTeams: " + playerName + " is in proximity. Checking claim membership.");
                if (claimMembers.stream().anyMatch(member -> member.equalsIgnoreCase(playerName))) {
                    plugin.getLogger().info("updatePlayerTeams: " + playerName + " is a claim member. Assigning to Defender team.");
                    defenderTeam.addEntry(playerName);
                } else {
                    plugin.getLogger().info("updatePlayerTeams: " + playerName + " is NOT a claim member. Assigning to Intruder team.");
                    intruderTeam.addEntry(playerName);
                }
            } else {
                // Player is outside proximity and not the Claimant, they should not be on any contest team.
                // The removal at the start of the loop ensures this.
                plugin.getLogger().info("updatePlayerTeams: " + playerName + " is outside proximity and not the claimant. Not assigning to Defender/Intruder teams.");
            }
        }
        plugin.getLogger().info("updatePlayerTeams: Finished team update iteration.");
    }

    // Sets board universally
    private void setBoardForAllPlayers(Scoreboard board) {
        if (board == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Simple check - might cause flicker if board content is identical frame to frame
            // A more complex check could compare objective scores if performance is an issue
            // if (p.getScoreboard() != board) {
            p.setScoreboard(board);
            // }
        }
    }


    private void clearPlayerScoreboard(Player player) {
        if (player.getScoreboard() != null && player.getScoreboard().getObjective(SCOREBOARD_OBJECTIVE_NAME) != null) {
            player.setScoreboard(scoreboardManager.getMainScoreboard());
        }
    }

    private void clearAllScoreboards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            clearPlayerScoreboard(p);
        }
    }

    // Method to clear players from contest teams
    private void clearContestTeams() {
        // Using the main scoreboard here to get the teams and clear entries
        // Assuming teams were registered on the main scoreboard or a consistent contest board
        Scoreboard mainBoard = scoreboardManager.getMainScoreboard(); // Get the main server scoreboard
        // Attempt to get the teams from the main scoreboard
        Team claimantTeam = mainBoard.getTeam(TEAM_CLAIMANT);
        Team defenderTeam = mainBoard.getTeam(TEAM_DEFENDER);
        Team intruderTeam = mainBoard.getTeam(TEAM_INTRUDER);

        // Remove all players from the teams if the teams exist
        if (claimantTeam != null) {
            claimantTeam.getEntries().forEach(entry -> claimantTeam.removeEntry(entry));
            // Optionally unregister teams after clearing if you don't need them permanently
            // claimantTeam.unregister();
        }
        if (defenderTeam != null) {
            defenderTeam.getEntries().forEach(entry -> defenderTeam.removeEntry(entry));
            // defenderTeam.unregister();
        }
        if (intruderTeam != null) {
            intruderTeam.getEntries().forEach(entry -> intruderTeam.removeEntry(entry));
            // intruderTeam.unregister();
        }
    }


    // --- Utility / Helper Methods ---

    // Parses time strings like "1d 2h 30m" into milliseconds. Returns -1 on error.
    private long parseTimeArgument(String[] args) {
        long totalSeconds = 0;
        boolean valid = false;
        for (String part : args) {
            part = part.toLowerCase();
            try {
                long value = Long.parseLong(part.substring(0, part.length() - 1));
                if (part.endsWith("d")) totalSeconds += value * SECONDS_PER_DAY;
                else if (part.endsWith("h")) totalSeconds += value * SECONDS_PER_HOUR;
                else if (part.endsWith("m")) totalSeconds += value * SECONDS_PER_MINUTE;
                else if (part.endsWith("s")) totalSeconds += value;
                else return -1; // Invalid suffix
                valid = true;
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                return -1; // Not a valid number or format
            }
        }
        return valid ? totalSeconds * 1000L : -1; // Return milliseconds or -1 if no valid parts found
    }

    // Helper method to get all members of a claim
    private List<String> getClaimMembers(String claimId) {
        if (claimId == null || claimId.isEmpty()) return Collections.emptyList();
        String membersString = claimDataConfig.getString(claimId + ".members");
        if (membersString == null || membersString.isEmpty()) {
            return Collections.emptyList();
        }
        // Split by newline and filter out empty lines
        return Arrays.stream(membersString.split("\\n")) // Assumes members are separated by newlines in the config
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // Helper method to get the primary claimant (first member)
    private String getPrimaryClaimantName(String claimId) {
        List<String> members = getClaimMembers(claimId);
        return members.isEmpty() ? NO_CLAIMANT : members.get(0);
    }

    // Checks for claimant change in standard mode and fires event/broadcast
    private void checkStandardClaimantChange() {
        String actualClaimant = getPrimaryClaimantName(currentContestClaimId); // Use primary claimant
        String previousClaimant = getDataString(CURRENT_CLAIMANT_KEY, NO_CLAIMANT);

        if (!actualClaimant.equalsIgnoreCase(previousClaimant)) {
            setDataValue(CURRENT_CLAIMANT_KEY, actualClaimant);
            plugin.cfg.saveClaimContest();

            if (!actualClaimant.equals(NO_CLAIMANT)) {
                Bukkit.broadcastMessage(ChatColor.GOLD + actualClaimant + " has claimed the Contest Area!");
                // Call custom event IF NEEDED for standard mode claims (might be noisy)
                // pluginManager.callEvent(new ContestChunkClaimedEvent(actualClaimant));
            } else if (!previousClaimant.equals(NO_CLAIMANT)) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + previousClaimant + " no longer holds the Contest Area!");
            }
        }
    }

    private void broadcastExactArea() {
        String chunkLoc = getDataString(CHUNK_LOC_KEY, "Unknown");
        Bukkit.broadcastMessage(ChatColor.GREEN + "Contest Location: " + ChatColor.AQUA + chunkLoc);
    }

    private String getApproximateLocationString() {
        String chunkLoc = getDataString(CHUNK_LOC_KEY, "Unknown Location");
        try {
            // Simple obfuscation: Show dimension, hide exact coords
            String dimension = chunkLoc.split(" ")[0];
            if (dimension.isEmpty() || dimension.contains("(")) dimension = "Unknown"; // Basic sanity check

            // More advanced: Generate randomish coords nearby like the example?
            // Example: Random offset within ~100 blocks, consistent per contest start
            int x = getDataInt(CLAIM_ID_KEY.hashCode() + "_approx_x_offset", 0); // Use hash for pseudo-randomness
            int z = getDataInt(CLAIM_ID_KEY.hashCode() + "_approx_z_offset", 0);
            if (x == 0 && z == 0) { // Generate once if not set
                Random random = new Random(getDataString(CLAIM_ID_KEY, "").hashCode()); // Seed with claim ID
                int offsetX = (random.nextBoolean() ? 1 : -1) * (64 + random.nextInt(64));
                int offsetZ = (random.nextBoolean() ? 1 : -1) * (64 + random.nextInt(64));

                String[] parts = chunkLoc.split("\\(")[1].split(",");
                int actualX = Integer.parseInt(parts[0].replaceAll("[^\\d-]", ""));
                int actualZ = Integer.parseInt(parts[1].replaceAll("[^\\d-]", ""));
                x = actualX + offsetX;
                z = actualZ + offsetZ;
                // Store for consistency during pre-reveal
                setDataValue(CLAIM_ID_KEY.hashCode() + "_approx_x_offset", x);
                setDataValue(CLAIM_ID_KEY.hashCode() + "_approx_z_offset", z);
                plugin.cfg.saveClaimContest(); // Save generated offsets
            }

            return dimension + " near X:" + x + ", Z:" + z;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse chunkLoc for approximate location: " + chunkLoc);
            return "An Unknown Area";
        }
    }


    private boolean sendCurrentContestStatus(Player player) {
        ConfigurationSection data = claimContestConfig.getConfigurationSection(DATA_SECTION);
        boolean configExists = (data != null && !data.getKeys(false).isEmpty());

        player.sendMessage(ChatColor.GOLD + "--- Contest Status ---");

        if (currentPhase != Phase.PENDING && currentPhase != Phase.FINISHED) {
            player.sendMessage(ChatColor.GREEN + "Status: ACTIVE (" + currentPhase + ")");
        } else if (configExists && getDataLong(CONFIG_SAVE_TIMESTAMP_KEY) > 0) {
            player.sendMessage(ChatColor.YELLOW + "Status: Configured (Pending Start)");
        } else {
            player.sendMessage(ChatColor.GRAY + "Status: No contest configured.");
            return true;
        }

        // Display configuration details (even if running, show what it was set to)
        String chunkLoc = getDataString(CHUNK_LOC_KEY, ChatColor.RED + "Not Set");
        String prize = getDataString(PRIZE_KEY, ChatColor.GRAY + "None");
        boolean preReveal = getDataBoolean(MODE_PRE_REVEAL_KEY);
        boolean hold = getDataBoolean(MODE_HOLD_KEY);
        long stdDur = getDataLong(DURATION_KEY);
        long preRevDur = getDataLong(DURATION_PRE_REVEAL_KEY);
        long holdDur = getDataLong(DURATION_HOLD_KEY);

        player.sendMessage(ChatColor.YELLOW + "Location: " + ChatColor.WHITE + chunkLoc);
        player.sendMessage(ChatColor.YELLOW + "Prize: " + ChatColor.WHITE + prize);

        // Mode details
        if (hold) {
            player.sendMessage(ChatColor.YELLOW + "Mode: " + ChatColor.AQUA + "Claim and Hold (" + formatDuration(holdDur) + ")");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Mode: " + ChatColor.AQUA + "Standard (" + formatDuration(stdDur) + ")");
        }
        if (preReveal) {
            player.sendMessage(ChatColor.YELLOW + "Pre-Reveal: " + ChatColor.GREEN + "Enabled (" + formatDuration(preRevDur) + ")");
        }

        // Runtime details if active
        if (currentPhase != Phase.PENDING && currentPhase != Phase.FINISHED) {
            player.sendMessage(ChatColor.GOLD + "--- Runtime Info ---");
            long now = System.currentTimeMillis();
            switch (currentPhase) {
                case PRE_REVEAL:
                    player.sendMessage(ChatColor.YELLOW + "Revealing in: " + ChatColor.WHITE + formatTimeLeft(contestEndTimeMillis - now));
                    break;
                case ACTIVE:
                    if (modeHold) {
                        // In Active Hold mode, show current claimant status
                        String currentClaimant = getPrimaryClaimantName(currentContestClaimId); // Use primary claimant
                        player.sendMessage(ChatColor.YELLOW + "Claimant: " + (currentClaimant.equals(NO_CLAIMANT) ? ChatColor.GRAY + "Unclaimed" : ChatColor.GREEN + currentClaimant));
                        player.sendMessage(ChatColor.YELLOW + "Hold for " + formatDuration(holdDurationMillis) + " to win!"); // Show the goal duration
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "Time Left: " + ChatColor.WHITE + formatTimeLeft(contestEndTimeMillis - now));
                        player.sendMessage(ChatColor.YELLOW + "Current Claimant: " + ChatColor.WHITE + getPrimaryClaimantName(currentContestClaimId)); // Use primary claimant
                    }
                    break;
                case HOLD:
                    player.sendMessage(ChatColor.YELLOW + "Current Holder: " + ChatColor.GREEN + currentHolderName);
                    player.sendMessage(ChatColor.YELLOW + "Hold Time Left: " + ChatColor.WHITE + formatTimeLeft(holdEndTimeMillis.get() - now));
                    break;
            }
            // Also show the command start time if available
            long commandStartTime = getDataLong(COMMAND_START_TIMESTAMP_KEY);
            if (commandStartTime > 0) {
                player.sendMessage(ChatColor.YELLOW + "Started At: " + ChatColor.WHITE + HISTORY_DATE_FORMAT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(commandStartTime), ZoneId.systemDefault())));
            }

        } else if (currentPhase == Phase.PENDING && configExists){
            player.sendMessage(ChatColor.GRAY + "Use '/claimcontest start' to begin.");
            // Show the configured command start time if available (e.g. from previous unfinished contest config)
            long commandStartTime = getDataLong(COMMAND_START_TIMESTAMP_KEY);
            if (commandStartTime > 0) {
                player.sendMessage(ChatColor.YELLOW + "Last Configured Start At: " + ChatColor.WHITE + HISTORY_DATE_FORMAT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(commandStartTime), ZoneId.systemDefault())));
            }
        }


        return true;
    }

    // Format duration in a human-readable way (e.g., "1d 2h 30m 5s")
    private String formatDuration(long millis) {
        if (millis <= 0) return "0s";
        long days = TimeUnit.MILLISECONDS.toDays(millis); millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis); millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis); millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    // Format time left specifically for scoreboard (DD:HH:MM:SS)
    private String formatTimeLeft(long millis) {
        if (millis <= 0) return "00:00:00:00";
        long secondsTotal = Math.max(0, millis / 1000);
        long days = secondsTotal / SECONDS_PER_DAY;
        long hours = (secondsTotal % SECONDS_PER_DAY) / SECONDS_PER_HOUR;
        long minutes = (secondsTotal % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        long seconds = secondsTotal % SECONDS_PER_MINUTE;
        return String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds);
    }

    // --- Config Helpers ---
    private void setDataValue(String key, Object value) {
        claimContestConfig.set(DATA_SECTION + "." + key, value);
    }
    private String getDataString(String key, String def) {
        return claimContestConfig.getString(DATA_SECTION + "." + key, def);
    }
    private String getDataString(String key) { return getDataString(key, null); }
    private long getDataLong(String key) { return claimContestConfig.getLong(DATA_SECTION + "." + key, 0L); }
    private boolean getDataBoolean(String key){ return claimContestConfig.getBoolean(DATA_SECTION + "." + key, false); }
    private int getDataInt(String key, int def) { return claimContestConfig.getInt(DATA_SECTION + "." + key, def); }

    // Clears only runtime state data from config, keeping setup config and history
    private void clearRuntimeContestData() {
        ConfigurationSection dataSection = claimContestConfig.getConfigurationSection(DATA_SECTION);
        if (dataSection != null) {
            // Keep COMMAND_START_TIMESTAMP_KEY for history calculation, clear others
            dataSection.set(START_TIMESTAMP_KEY, null); // Clear Active phase start time
            dataSection.set(CURRENT_CLAIMANT_KEY, null);
            dataSection.set(CURRENT_HOLDER_KEY, null);
            dataSection.set(PHASE_KEY, null);
            dataSection.set(PRE_REVEAL_END_TIMESTAMP_KEY, null);
            dataSection.set(CONTEST_END_TIMESTAMP_KEY, null);
            dataSection.set(HOLD_END_TIMESTAMP_KEY, null);
            // Clear approx location cache if used
            dataSection.set(CLAIM_ID_KEY.hashCode() + "_approx_x_offset", null);
            dataSection.set(CLAIM_ID_KEY.hashCode() + "_approx_z_offset", null);
        }
        // Keep: chunkLoc, claimID, duration, prize, mode settings, config-save-timestamp, COMMAND_START_TIMESTAMP_KEY
    }

    // Completely clears the data section (use with caution, maybe for full reset?)
    private void clearFullContestData() {
        claimContestConfig.set(DATA_SECTION, null);
    }

    // --- Load and Resume Logic ---
    private void loadAndResumeContest() {
        ConfigurationSection data = claimContestConfig.getConfigurationSection(DATA_SECTION);
        if (data == null || !data.contains(PHASE_KEY)) {
            currentPhase = Phase.PENDING; // No contest running or invalid data
            return;
        }

        try {
            currentPhase = Phase.valueOf(data.getString(PHASE_KEY, Phase.PENDING.name()));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid phase found in contest config: " + data.getString(PHASE_KEY) + ". Resetting.");
            clearFullContestData(); // Clear everything if phase is corrupt
            plugin.cfg.saveClaimContest();
            currentPhase = Phase.PENDING;
            return;
        }

        if (currentPhase == Phase.PENDING || currentPhase == Phase.FINISHED) {
            // Contest wasn't active on shutdown, no need to resume task
            // We might want to clear runtime data here if it's FINISHED but wasn't cleared properly
            if(currentPhase == Phase.FINISHED) clearRuntimeContestData();
            // If finished, clear the COMMAND_START_TIMESTAMP_KEY as well so it doesn't show for a new pending contest
            if (currentPhase == Phase.FINISHED) {
                setDataValue(COMMAND_START_TIMESTAMP_KEY, null);
                plugin.cfg.saveClaimContest();
            }
            return;
        }

        plugin.getLogger().info("Resuming claim contest from Phase: " + currentPhase);

        // Load common state needed for resume
        currentContestClaimId = data.getString(CLAIM_ID_KEY);
        modeHold = data.getBoolean(MODE_HOLD_KEY); // Load mode flags
        holdDurationMillis = data.getLong(DURATION_HOLD_KEY); // Need hold duration for HOLD phase
        // Load command start time if available (needed for history calculation on end)
        long commandStartTimeLoaded = data.getLong(COMMAND_START_TIMESTAMP_KEY, -1);


        if (currentContestClaimId == null || currentContestClaimId.isEmpty()) {
            plugin.getLogger().severe("Cannot resume contest: Claim ID missing from config!");
            clearFullContestData(); // Clear config if critical info missing
            plugin.cfg.saveClaimContest();
            currentPhase = Phase.PENDING;
            return;
        }

        long now = System.currentTimeMillis();

        // Resume logic based on persisted phase
        switch (currentPhase) {
            case PRE_REVEAL:
                contestEndTimeMillis = data.getLong(PRE_REVEAL_END_TIMESTAMP_KEY, -1);
                if (now >= contestEndTimeMillis) { // Should have ended
                    plugin.getLogger().warning("Pre-Reveal phase ended while server was down. Advancing...");
                    // Load necessary config for ACTIVE phase before transitioning
                    modePreReveal = data.getBoolean(MODE_PRE_REVEAL_KEY);
                    preRevealDurationMillis = data.getLong(DURATION_PRE_REVEAL_KEY);
                    contestDurationMillis = getDataLong(DURATION_KEY); // Standard duration needed if applicable
                    // Manually trigger transition post-load
                    scheduler.runTask(plugin, () -> transitionToPhase(Phase.ACTIVE));
                } else {
                    // Resume pre-reveal task
                    mainTask = scheduler.runTaskTimer(plugin, this::tickPreReveal, 0L, TICKS_PER_SECOND);
                    plugin.getLogger().info("Pre-Reveal phase resumed. Ends in " + formatDuration(contestEndTimeMillis - now));
                }
                break;

            case ACTIVE:
                contestStartTimeMillis = data.getLong(START_TIMESTAMP_KEY, -1); // Load Active phase start time
                // Ensure COMMAND_START_TIMESTAMP_KEY is present if resuming ACTIVE
                if (commandStartTimeLoaded <= 0) {
                    plugin.getLogger().warning("COMMAND_START_TIMESTAMP_KEY missing when resuming ACTIVE phase. Cannot calculate full duration on end.");
                }

                if (modeHold) {
                    // Just resume the ActiveHold ticker, no end time needed here
                    mainTask = scheduler.runTaskTimer(plugin, this::tickActiveHoldMode, 0L, TICKS_PER_SECOND);
                    plugin.getLogger().info("Active (Hold Mode) phase resumed. Waiting for claim.");
                    // Update scoreboards and teams immediately on resume
                    updateActiveHoldModeScoreboards();
                } else {
                    // Resume standard timer
                    contestEndTimeMillis = data.getLong(CONTEST_END_TIMESTAMP_KEY, -1);
                    if (now >= contestEndTimeMillis) { // Should have ended
                        plugin.getLogger().warning("Standard contest phase ended while server was down. Finalizing...");
                        String finalClaimant = data.getString(CURRENT_CLAIMANT_KEY, NO_CLAIMANT); // Get last known claimant
                        scheduler.runTask(plugin, () -> handleContestEnd(finalClaimant));
                    } else {
                        mainTask = scheduler.runTaskTimer(plugin, this::tickStandard, 0L, TICKS_PER_SECOND);
                        plugin.getLogger().info("Standard contest phase resumed. Ends in " + formatDuration(contestEndTimeMillis - now));
                        // Update scoreboards and teams immediately on resume
                        updateStandardScoreboards(contestEndTimeMillis - now);
                    }
                }
                break;

            case HOLD:
                currentHolderName = data.getString(CURRENT_HOLDER_KEY, NO_CLAIMANT);
                long holdEnds = data.getLong(HOLD_END_TIMESTAMP_KEY, -1);
                holdEndTimeMillis.set(holdEnds);
                // Ensure COMMAND_START_TIMESTAMP_KEY is present if resuming HOLD
                if (commandStartTimeLoaded <= 0) {
                    plugin.getLogger().warning("COMMAND_START_TIMESTAMP_KEY missing when resuming HOLD phase. Cannot calculate full duration on end.");
                }


                if (currentHolderName.equals(NO_CLAIMANT) || holdEnds <= 0) {
                    plugin.getLogger().warning("Inconsistent HOLD phase state found. Resetting to ACTIVE.");
                    scheduler.runTask(plugin, () -> transitionToPhase(Phase.ACTIVE)); // Go back to active
                    return;
                }

                if (now >= holdEnds) { // Hold should have finished
                    plugin.getLogger().warning("Hold phase ended while server was down. Finalizing for holder: " + currentHolderName);
                    scheduler.runTask(plugin, () -> handleContestEnd(currentHolderName));
                } else {
                    // Resume hold task
                    mainTask = scheduler.runTaskTimer(plugin, this::tickHold, 0L, TICKS_PER_SECOND);
                    plugin.getLogger().info("Hold phase resumed for " + currentHolderName + ". Ends in " + formatDuration(holdEnds - now));
                    // Update scoreboards and teams immediately on resume
                    updateHoldScoreboards(holdEnds - now);
                }
                break;
        }
    }


    // --- Tab Completion ---

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> possibilities = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();
        boolean configLocked = currentPhase != Phase.PENDING && currentPhase != Phase.FINISHED;


        if (args.length == 1) {
            possibilities.addAll(Arrays.asList("help", "start", "cancel", "status"));
            if (!configLocked) { // Only show config commands if not active
                possibilities.addAll(Arrays.asList("chunk", "duration", "prize", "mode"));
            }
        } else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();
            if (!configLocked) { // Config sub-args only allowed if not active
                switch (subCommand) {
                    case "chunk":
                    case "loc": case "at": case "xyz":
                        if (args.length == 2) possibilities.addAll(Arrays.asList("overworld", "nether", "end"));
                        else if (args.length == 3 && sender instanceof Player) {
                            possibilities.add(String.valueOf(((Player) sender).getLocation().getBlockX())); possibilities.add("<X>");
                        } else if (args.length == 4 && sender instanceof Player) {
                            possibilities.add(String.valueOf(((Player) sender).getLocation().getBlockZ())); possibilities.add("<Z>");
                        }
                        break;
                    case "duration": // Standard duration
                    case "time":
                        if (args.length == 2) possibilities.add("<e.g., 1h 30m>");
                        else possibilities.addAll(Arrays.asList("d", "h", "m", "s"));
                        break;
                    case "prize":
                        if (args.length == 2) possibilities.add("<description>");
                        break;
                    case "mode":
                        if (args.length == 2) {
                            possibilities.addAll(Arrays.asList("pre_reveal", "hold", "standard"));
                        } else if (args.length == 3 && (args[1].equalsIgnoreCase("pre_reveal") || args[1].equalsIgnoreCase("hold"))) {
                            possibilities.add("<e.g., 5m>"); // Suggest format for time value
                        } else if (args.length > 3 && (args[1].equalsIgnoreCase("pre_reveal") || args[1].equalsIgnoreCase("hold"))) {
                            possibilities.addAll(Arrays.asList("d", "h", "m", "s")); // Suggest suffixes
                        }
                        break;
                }
            }
            // No further args for start, cancel, status, help
        }

        // Filter possibilities
        for (String p : possibilities) {
            if (p.toLowerCase().startsWith(currentArg)) {
                completions.add(p);
            }
        }
        return completions;
    }
}