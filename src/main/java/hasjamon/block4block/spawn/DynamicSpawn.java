package hasjamon.block4block.spawn;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Moves the world spawn across fresh land, one claim cell at a time, on a timer and without randomness: players who
 * join around the same time land in the same place. See SpawnWalker for the path. Spawn is never inside a claim or its
 * buffer, the walk turns around at the world border and again at the centre, and a move only happens if somebody used
 * the current spawn since the last one.
 */
public class DynamicSpawn implements Listener {
    private static final Set<Material> BAD_FLOOR = EnumSet.of(Material.WATER, Material.LAVA, Material.MAGMA_BLOCK,
            Material.FIRE, Material.SOUL_FIRE, Material.CACTUS, Material.POWDER_SNOW, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH, Material.COBWEB, Material.ICE, Material.PACKED_ICE);
    private static final long PREPARE_AHEAD_MS = 5 * 60 * 1000;

    private final Block4Block plugin;
    private final YamlConfiguration config;
    private final File stateFile;
    private final YamlConfiguration state;

    // State, mirrored in spawn-state.yml
    private long step;
    private boolean outward = true;
    private boolean usedSinceMove;
    private boolean paused;
    private OffsetDateTime nextMoveAt;
    private int centerCellX, centerCellZ;

    /** A cell and spot chosen ahead of the move, with its chunks already loaded. */
    private static final class Plan {
        long step;
        boolean outward;
        Location spot;
    }

    private volatile Plan prepared;
    private boolean preparing;

    public DynamicSpawn(Block4Block plugin) {
        this.plugin = plugin;
        this.config = loadConfig();
        this.stateFile = new File(plugin.getDataFolder(), "spawn-state.yml");
        this.state = YamlConfiguration.loadConfiguration(stateFile);
        World w = world();
        if (w == null) {
            plugin.getLogger().warning("Dynamic spawn: world '" + cfgString("world", "world") + "' not found; it stays off.");
            return;
        }
        if (state.contains("next-move-at")) loadState();
        else migrate(w);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 60, 20L * 60);
        plugin.getLogger().info("Dynamic spawn on: cell " + cellOfStep(step)[0] + "," + cellOfStep(step)[1]
                + " (ring " + SpawnWalker.ringOf(step) + ", " + (outward ? "outward" : "inward") + "), next move "
                + nextMoveAt + (paused ? " (paused)" : ""));
    }

    // ---- config ------------------------------------------------------------------------------

    /** The plugin writes no config.yml, so the defaults are read from the jar's copy (and a data-folder one overrides). */
    private YamlConfiguration loadConfig() {
        YamlConfiguration cfg = new YamlConfiguration();
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in != null) cfg.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Dynamic spawn: could not read the jar config: " + e.getMessage());
        }
        File override = new File(plugin.getDataFolder(), "config.yml");
        if (override.exists()) {
            YamlConfiguration o = YamlConfiguration.loadConfiguration(override);
            if (o.isConfigurationSection("dynamic-spawn")) cfg.set("dynamic-spawn", o.get("dynamic-spawn"));
        }
        return cfg;
    }

    private String cfgString(String key, String def) {
        return config.getString("dynamic-spawn." + key, def);
    }

    private int cfgInt(String key, int def) {
        return config.getInt("dynamic-spawn." + key, def);
    }

    private boolean cfgBool(String key, boolean def) {
        return config.getBoolean("dynamic-spawn." + key, def);
    }

    public boolean enabled() {
        return cfgBool("enabled", true) && world() != null;
    }

    private World world() {
        World w = Bukkit.getWorld(cfgString("world", "world"));
        return w != null && w.getEnvironment() == World.Environment.NORMAL ? w : null;
    }

    private Duration interval() {
        Matcher m = Pattern.compile("^(\\d+)\\s*([smhd])$").matcher(cfgString("interval", "1d").trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) return Duration.ofDays(1);
        long n = Long.parseLong(m.group(1));
        switch (m.group(2)) {
            case "s": return Duration.ofSeconds(n);
            case "m": return Duration.ofMinutes(n);
            case "h": return Duration.ofHours(n);
            default: return Duration.ofDays(n);
        }
    }

    // ---- state -------------------------------------------------------------------------------

    private void loadState() {
        step = state.getLong("step", 0);
        outward = !"inward".equals(state.getString("direction"));
        usedSinceMove = state.getBoolean("used-since-move", false);
        paused = state.getBoolean("paused", false);
        centerCellX = state.getInt("center-cell.x");
        centerCellZ = state.getInt("center-cell.z");
        try {
            nextMoveAt = OffsetDateTime.parse(state.getString("next-move-at"));
        } catch (Exception e) {
            nextMoveAt = OffsetDateTime.now().plus(interval());
        }
    }

    /**
     * First start: the spiral begins at the cell the current world spawn is in, so spawn does not jump back to the
     * centre. The old plugin's layer and index are ignored.
     */
    private void migrate(World w) {
        Location spawn = w.getSpawnLocation();
        int[] here = cellAt(spawn.getBlockX(), spawn.getBlockZ());
        Object c = config.get("dynamic-spawn.center");
        if (c instanceof ConfigurationSection) {
            ConfigurationSection cs = (ConfigurationSection) c;
            int[] fixed = cellAt(cs.getInt("x"), cs.getInt("z"));
            centerCellX = fixed[0];
            centerCellZ = fixed[1];
        } else {
            centerCellX = here[0];
            centerCellZ = here[1];
        }
        step = SpawnWalker.stepOf(here[0] - centerCellX, here[1] - centerCellZ);
        outward = true;
        usedSinceMove = false;
        nextMoveAt = OffsetDateTime.now().plus(interval());
        saveState(spawn);
        plugin.getLogger().info("Dynamic spawn: started from the current world spawn.");
    }

    private void saveState(Location current) {
        state.set("ring", SpawnWalker.ringOf(step));
        state.set("index", SpawnWalker.indexOf(step));
        state.set("step", step);
        state.set("direction", outward ? "outward" : "inward");
        state.set("next-move-at", nextMoveAt.toString());
        state.set("used-since-move", usedSinceMove);
        state.set("paused", paused);
        state.set("center-cell.x", centerCellX);
        state.set("center-cell.z", centerCellZ);
        if (current != null) {
            state.set("current.x", current.getBlockX());
            state.set("current.y", current.getBlockY());
            state.set("current.z", current.getBlockZ());
        }
        try {
            state.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Dynamic spawn: could not save spawn-state.yml: " + e.getMessage());
        }
    }

    // ---- cells ---------------------------------------------------------------------------------

    private int cellBlocks() {
        return utils.claimWidth * 16;
    }

    /** The claim cell (as in claim IDs) that contains a block column. */
    private int[] cellAt(int blockX, int blockZ) {
        int w = utils.claimWidth;
        int chunkX = blockX >> 4, chunkZ = blockZ >> 4;
        return new int[]{Math.floorDiv(chunkX, w), Math.floorDiv(chunkZ, w)};
    }

    private int[] cellOfStep(long s) {
        int[] d = SpawnWalker.cellOf(s);
        return new int[]{centerCellX + d[0], centerCellZ + d[1]};
    }

    private boolean claimed(int cx, int cz) {
        return plugin.cfg.getClaimData().contains(World.Environment.NORMAL.name() + "|" + cx + "," + cz);
    }

    private boolean nearClaim(int cx, int cz) {
        int buffer = Math.max(0, cfgInt("claim-buffer", 1));
        for (int dx = -buffer; dx <= buffer; dx++)
            for (int dz = -buffer; dz <= buffer; dz++)
                if (claimed(cx + dx, cz + dz)) return true;
        return false;
    }

    private boolean insideBorder(World w, int cx, int cz) {
        WorldBorder b = w.getWorldBorder();
        int size = cellBlocks();
        int x0 = cx * size, z0 = cz * size, x1 = x0 + size - 1, z1 = z0 + size - 1;
        return b.isInside(new Location(w, x0, 64, z0)) && b.isInside(new Location(w, x1, 64, z0))
                && b.isInside(new Location(w, x0, 64, z1)) && b.isInside(new Location(w, x1, 64, z1));
    }

    /** Does any cell of this ring lie inside the border? */
    private boolean ringHasLand(World w, int ring) {
        long base = ring == 0 ? 0 : (2L * ring - 1) * (2L * ring - 1);
        for (int i = 0; i < SpawnWalker.cellsInRing(ring); i++) {
            int[] c = cellOfStep(base + i);
            if (insideBorder(w, c[0], c[1])) return true;
        }
        return false;
    }

    // ---- choosing the next spawn --------------------------------------------------------------------

    /** One step along the path with the turn-arounds; returns the new {step, outward} or null if the walk is stuck. */
    private long[] advance(World w, long s, boolean out) {
        if (out) {
            int nextRing = SpawnWalker.ringOf(s + 1);
            if (nextRing != SpawnWalker.ringOf(s) && !ringHasLand(w, nextRing)) {
                out = false;                                  // a whole ring is outside the border: turn around
            }
        }
        if (!out) {
            if (s <= 0) { out = true; }                       // back at the centre: turn around
        }
        long next = out ? s + 1 : s - 1;
        if (!out && next < 0) next = 0;
        return new long[]{next, out ? 1 : 0};
    }

    /** The next cell after (s, out) that is clear of claims and inside the border; spends from skips[0]. */
    private Plan candidateAfter(World w, long s, boolean out, int[] skips, List<String> why) {
        while (skips[0]-- > 0) {
            long[] a = advance(w, s, out);
            s = a[0];
            out = a[1] == 1;
            int[] cell = cellOfStep(s);
            if (s == step) continue;   // turning around must not land back in the cell spawn is in now
            if (nearClaim(cell[0], cell[1])) { skipped(why, cell, "a claim is in or next to it"); continue; }
            if (!insideBorder(w, cell[0], cell[1])) { skipped(why, cell, "it is outside the world border"); continue; }
            Plan p = new Plan();
            p.step = s;
            p.outward = out;
            return p;
        }
        return null;
    }

    /** The next spawn: the first cell that qualifies and has a safe standing spot, within max-skips-per-move steps. */
    private void skipped(List<String> why, int[] cell, String reason) {
        if (why != null && why.size() < 12) why.add("cell " + cell[0] + "," + cell[1] + ": " + reason);
    }

    private Plan choose(World w) {
        return choose(w, null);
    }

    private Plan choose(World w, List<String> why) {
        int[] skips = {Math.max(1, cfgInt("max-skips-per-move", 200))};
        long s = step;
        boolean out = outward;
        Plan c;
        while ((c = candidateAfter(w, s, out, skips, why)) != null) {
            int[] cell = cellOfStep(c.step);
            Location spot = safeSpot(w, cell[0], cell[1]);
            if (spot != null) {
                c.spot = spot;
                return c;
            }
            skipped(why, cell, "no safe place to stand");
            s = c.step;
            out = c.outward;
        }
        return null;
    }

    private Location safeSpot(World w, int cx, int cz) {
        int size = cellBlocks();
        int midX = cx * size + size / 2, midZ = cz * size + size / 2;
        int radius = size / 2 - 2;
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += 2)
            for (int dz = -radius; dz <= radius; dz += 2) offsets.add(new int[]{dx, dz});
        offsets.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        int minY = w.getSeaLevel() - 5;
        for (int[] o : offsets) {
            int x = midX + o[0], z = midZ + o[1];
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (y < minY || y + 2 >= w.getMaxHeight()) continue;
            Material floor = w.getBlockAt(x, y, z).getType();
            if (!floor.isSolid() || BAD_FLOOR.contains(floor)) continue;
            if (!w.getBlockAt(x, y + 1, z).getType().isAir() || !w.getBlockAt(x, y + 2, z).getType().isAir()) continue;
            return new Location(w, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    // ---- timer -----------------------------------------------------------------------------------------

    private void tick() {
        World w = world();
        if (w == null || !cfgBool("enabled", true) || paused) return;
        OffsetDateTime now = OffsetDateTime.now();
        if (!now.isBefore(nextMoveAt)) {
            moveScheduled(w, now);
            return;
        }
        // Choose the next cell and load its chunks about five minutes ahead, so the move itself does not lag.
        if (!preparing && prepared == null && (!cfgBool("require-use", true) || usedSinceMove)
                && Duration.between(now, nextMoveAt).toMillis() <= PREPARE_AHEAD_MS) {
            prepare(w);
        }
    }

    private void moveScheduled(World w, OffsetDateTime now) {
        // A server that was down for several intervals moves once, then the timer jumps ahead of now.
        Duration iv = interval();
        OffsetDateTime next = nextMoveAt.plus(iv);
        while (!next.isAfter(now)) next = next.plus(iv);
        if (cfgBool("require-use", true) && !usedSinceMove) {
            plugin.getLogger().info("Dynamic spawn: nobody used the current spawn since the last move; skipped.");
            nextMoveAt = next;
            saveState(null);
            return;
        }
        if (doMove(w)) nextMoveAt = next;
        else nextMoveAt = next;                                // no cell qualified: stay put, retry next interval
        saveState(null);
    }

    /** Moves spawn now. Returns whether it moved. */
    public boolean doMove(World w) {
        Plan p = prepared;
        prepared = null;
        if (p != null) {
            int[] cell = cellOfStep(p.step);
            if (nearClaim(cell[0], cell[1])) p = null;          // claimed since it was chosen
        }
        if (p == null) p = choose(w);
        if (p == null) {
            plugin.getLogger().warning("Dynamic spawn: no cell qualified within " + cfgInt("max-skips-per-move", 200)
                    + " steps; spawn stays where it is.");
            return false;
        }
        step = p.step;
        outward = p.outward;
        usedSinceMove = false;
        w.setSpawnLocation(p.spot);
        saveState(p.spot);
        announce(p.spot);
        plugin.getLogger().info("Dynamic spawn moved to cell " + cellOfStep(step)[0] + "," + cellOfStep(step)[1]
                + " at " + p.spot.getBlockX() + "," + p.spot.getBlockY() + "," + p.spot.getBlockZ());
        return true;
    }

    private void announce(Location spot) {
        if (!cfgBool("announce", true)) return;
        String msg = "&6Spawn has moved to fresh land.";
        if (!plugin.getConfig().getBoolean("hide-coords-globally"))
            msg += " &7(" + spot.getBlockX() + ", " + spot.getBlockZ() + ")";
        Bukkit.broadcastMessage(utils.chat(msg));
    }

    private void prepare(World w) {
        preparing = true;
        Plan c = candidateAfter(w, step, outward, new int[]{Math.max(1, cfgInt("max-skips-per-move", 200))}, null);
        if (c == null) { preparing = false; return; }
        int[] cell = cellOfStep(c.step);
        int size = cellBlocks();
        int c0x = (cell[0] * size) >> 4, c0z = (cell[1] * size) >> 4, n = utils.claimWidth;
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int dx = 0; dx < n; dx++)
            for (int dz = 0; dz < n; dz++) loads.add(w.getChunkAtAsync(c0x + dx, c0z + dz));
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((v, ex) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Location spot = safeSpot(w, cell[0], cell[1]);   // its chunks are loaded now
                    if (spot != null) {
                        c.spot = spot;
                        prepared = c;                                // otherwise the move looks further on its own
                    }
                    preparing = false;
                }));
    }

    // ---- who used the spawn ------------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!e.getPlayer().hasPlayedBefore()) markUsed();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!e.isBedSpawn() && !e.isAnchorSpawn()) markUsed();
    }

    private void markUsed() {
        if (usedSinceMove) return;
        usedSinceMove = true;
        saveState(null);
    }

    // ---- ops (see SpawnCommand) ------------------------------------------------------------------------------

    private String when(OffsetDateTime t) {
        Duration d = Duration.between(OffsetDateTime.now(), t);
        long mins = Math.max(0, d.toMinutes());
        String in = mins >= 120 ? (mins / 60) + " hours" : mins + " minutes";
        return t.format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm")) + " (in " + in + ")";
    }

    private String where(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    /** Plain-language lines for an op; one per line. */
    public List<String> info() {
        World w = world();
        int[] cell = cellOfStep(step);
        List<String> out = new ArrayList<>();
        out.add("Spawn is now at " + (w == null ? "?" : where(w.getSpawnLocation())) + ".");
        out.add("It is in cell " + cell[0] + "," + cell[1] + ", ring " + SpawnWalker.ringOf(step) + " of the spiral, and the walk is going "
                + (outward ? "away from" : "back towards") + " the centre.");
        out.add(paused ? "Moves are PAUSED." : "Next move: " + when(nextMoveAt) + ".");
        out.add(usedSinceMove ? "A player has used this spawn since the last move, so the next move will happen."
                : "Nobody has used this spawn since the last move, so the next scheduled move will be skipped.");
        return out;
    }

    public List<String> next() {
        World w = world();
        List<String> out = new ArrayList<>();
        if (w == null) { out.add("The spawn world was not found."); return out; }
        List<String> why = new ArrayList<>();
        Plan p = choose(w, why);
        if (p == null) {
            out.add("No cell qualifies within the skip limit; spawn would stay where it is.");
        } else {
            int[] cell = cellOfStep(p.step);
            out.add("The next spawn would be in cell " + cell[0] + "," + cell[1] + " (ring " + SpawnWalker.ringOf(p.step) + "), at " + where(p.spot) + ".");
        }
        if (!why.isEmpty()) {
            out.add("Skipped on the way:");
            for (String line : why) out.add(" - " + line);
        }
        return out;
    }

    public boolean moveNow() {
        World w = world();
        return w != null && doMove(w);
    }

    public void setPaused(boolean pause) {
        paused = pause;
        saveState(null);
    }

    public String centerHere(Player p) {
        int[] cell = cellAt(p.getLocation().getBlockX(), p.getLocation().getBlockZ());
        centerCellX = cell[0];
        centerCellZ = cell[1];
        step = 0;
        outward = true;
        prepared = null;
        saveState(null);
        return "centre set to cell " + cell[0] + "," + cell[1] + "; the walk restarts at ring 0";
    }
}
