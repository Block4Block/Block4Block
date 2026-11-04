package hasjamon.block4block.quest;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.events.ClaimBookPlacedEvent;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import hasjamon.block4block.events.B4BlockBreakEvent;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Config-defined questlines shown on a boss bar. A step states what to do and never why; the reason is the reveal.
 * Progress is kept per player in questprogress.yml.
 *
 * Step completion kinds ({@code complete.on}):
 * <ul>
 *   <li>{@code pickup}: picks up {@code item}, optionally only if it dropped from {@code source_entity}</li>
 *   <li>{@code craft}: has crafted {@code item} {@code count} times in total (server statistic)</li>
 *   <li>{@code has_items}: has every material in {@code items} in the inventory</li>
 *   <li>{@code write_book}: saves a book containing every word of {@code contains} ({@code {player}} is their name)</li>
 *   <li>{@code claim_book_placed}: places a claim book on a lectern</li>
 * </ul>
 */
public class QuestManager implements Listener {
    private static final long CHECK_TICKS = 40;
    private static final long SAVE_TICKS = 20 * 60;
    private static final long ITEM_SOURCE_TTL_MS = 10 * 60 * 1000;

    private final Block4Block plugin;
    private final File file;
    private final YamlConfiguration progress;
    private boolean dirty = false;

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, ItemSource> itemSources = new LinkedHashMap<>();
    private long deathTick = -1;
    private org.bukkit.Location deathLoc;
    private EntityType deathType;

    private static final class ItemSource {
        final EntityType type;
        final long at;
        ItemSource(EntityType type, long at) { this.type = type; this.at = at; }
    }

    /** What one online player is looking at right now. Not saved. */
    private static final class Session {
        final BossBar bar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        long lastProgressAt = System.currentTimeMillis();
        String shownKey = "";
        final Set<Integer> nudged = new HashSet<>();
    }

    public QuestManager(Block4Block plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "questprogress.yml");
        this.progress = YamlConfiguration.loadConfiguration(file);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, CHECK_TICKS, CHECK_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty, SAVE_TICKS, SAVE_TICKS);
        utils.claimBarHidden = this::claimBarHidden;
    }

    /** op tools: see where a player is, skip a step, or start the questlines over. */
    public String adminStatus(Player p) {
        StringBuilder sb = new StringBuilder();
        for (String id : questlineIds()) {
            sb.append(id).append(": ").append(isDone(p, id) ? "done" : isStarted(p, id) ? "step " + stepIndex(p, id) + "/" + steps(id).size() : "not started").append("; ");
        }
        return sb + "claim bar hidden: " + claimBarHidden(p);
    }

    public void adminStart(Player p, String id) {
        startQuestline(p, id);
    }

    public void adminSkip(Player p) {
        String id = displayed(p);
        if (id != null) advance(p, id);
    }

    public void adminReset(Player p) {
        progress.set(p.getUniqueId().toString(), null);
        Session s = sessions.get(p.getUniqueId());
        if (s != null) { s.bar.removeAll(); s.shownKey = ""; }
        markDirty();
        for (String q : questlineIds()) {
            ConfigurationSection start = questline(q).getConfigurationSection("start");
            if (start != null && "first_join".equals(start.getString("on"))) startQuestline(p, q);
        }
        saveIfDirty();
        refresh(p);
        utils.updateBossBar(p, utils.getClaimID(p.getLocation()));
    }

    public void shutdown() {
        utils.claimBarHidden = p -> false;
        for (Session s : sessions.values()) s.bar.removeAll();
        sessions.clear();
        saveIfDirty();
    }

    // ---- config ---------------------------------------------------------------------

    /**
     * The questlines section, read from the jar's config.yml (and from a config.yml in the data folder if there is
     * one with questlines in it). The plugin's own getConfig() cannot be used: it writes no file, so its values live
     * in the defaults, where getConfigurationSection hands back an empty section and getString ignores the tree.
     */
    private ConfigurationSection questlines() {
        if (questlinesCfg == null) questlinesCfg = loadQuestConfig();
        return questlinesCfg.getConfigurationSection("questlines");
    }

    private YamlConfiguration questlinesCfg;

    private YamlConfiguration loadQuestConfig() {
        YamlConfiguration cfg = new YamlConfiguration();
        try (java.io.InputStream in = plugin.getResource("config.yml")) {
            if (in != null) cfg.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read the questlines from the jar: " + e.getMessage());
        }
        File override = new File(plugin.getDataFolder(), "config.yml");
        if (override.exists()) {
            YamlConfiguration o = YamlConfiguration.loadConfiguration(override);
            if (o.isConfigurationSection("questlines")) cfg.set("questlines", o.get("questlines"));
        }
        return cfg;
    }

    private ConfigurationSection questline(String id) {
        ConfigurationSection all = questlines();
        return all == null ? null : all.getConfigurationSection(id);
    }

    private List<Map<?, ?>> steps(String id) {
        ConfigurationSection q = questline(id);
        return q == null ? List.of() : q.getMapList("steps");
    }

    private Set<String> questlineIds() {
        ConfigurationSection s = questlines();
        return s == null ? Set.of() : s.getKeys(false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<?, ?> m, String key) {
        Object o = m.get(key);
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    private static String str(Map<?, ?> m, String key) {
        Object o = m.get(key);
        return o == null ? null : o.toString();
    }

    // ---- progress -------------------------------------------------------------------

    private String path(Player p, String id, String leaf) {
        return p.getUniqueId() + "." + id + "." + leaf;
    }

    private boolean isDone(Player p, String id) {
        return progress.getBoolean(path(p, id, "done"), false);
    }

    private boolean isStarted(Player p, String id) {
        return progress.contains(path(p, id, "started"));
    }

    private int stepIndex(Player p, String id) {
        return progress.getInt(path(p, id, "step"), 0);
    }

    private boolean isActive(Player p, String id) {
        return isStarted(p, id) && !isDone(p, id);
    }

    private void markDirty() { dirty = true; }

    /**
     * Called when a step becomes the current one: counted steps start from zero, and a craft step measures only what
     * the player crafts from now on (an earlier craft, or a previous run, must not skip it).
     */
    private void initStep(Player p, String id, int idx) {
        progress.set(path(p, id, "count"), 0);
        progress.set(path(p, id, "base"), null);
        progress.set(path(p, id, "picked"), null);
        List<Map<?, ?>> steps = steps(id);
        if (idx < 0 || idx >= steps.size()) return;
        markDirty();
    }

    /** Count for a pickup step: the larger of what was picked up since the step began and what is carried now. */
    private int pickedOrHeld(Player p, String id, Material m) {
        return Math.max(progress.getInt(path(p, id, "picked")), held(p, m));
    }

    /** How many of a material the player carries now; it falls when they drop, lose or store it. */
    private static int held(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == m) n += it.getAmount();
        return n;
    }

    /** A craft step counts what the player has crafted before as well, or already holds: the larger of the two. */
    private int craftedSince(Player p, String id, Material m) {
        return Math.max(p.getStatistic(Statistic.CRAFT_ITEM, m), held(p, m));
    }

    /** Questline materials that a has_items step asks for. */
    private Set<Material> hasItemsMaterials(String id) {
        Set<Material> out = new HashSet<>();
        for (Map<?, ?> step : steps(id)) {
            Map<String, Object> c = sub(step, "complete");
            if (!"has_items".equals(str(c, "on")) || !(c.get("items") instanceof List)) continue;
            for (Object o : (List<?>) c.get("items")) {
                Material m = Material.matchMaterial(String.valueOf(o));
                if (m != null) out.add(m);
            }
        }
        return out;
    }

    /** True when the player holds the item now, or picked it up at any time since the questline began. */
    private boolean hasOrSeen(Player p, String id, Material m) {
        return p.getInventory().contains(m) || progress.getBoolean(path(p, id, "seen_" + m.name()));
    }

    private void saveIfDirty() {
        if (!dirty) return;
        try {
            progress.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save questprogress.yml: " + e.getMessage());
        }
    }

    /** Players who finished the claim questline, or who were here before questlines existed, see the claim bar. */
    public boolean claimBarHidden(Player p) {
        String unlock = "claim_bossbar";
        return !progress.getStringList(p.getUniqueId() + ".unlocked").contains(unlock);
    }

    private void unlock(Player p, String what) {
        List<String> list = new ArrayList<>(progress.getStringList(p.getUniqueId() + ".unlocked"));
        if (!list.contains(what)) {
            list.add(what);
            progress.set(p.getUniqueId() + ".unlocked", list);
            markDirty();
        }
    }

    // ---- starting and finishing -------------------------------------------------------

    public void startQuestline(Player p, String id) {
        if (questline(id) == null || isStarted(p, id)) return;
        progress.set(path(p, id, "started"), System.currentTimeMillis());
        progress.set(path(p, id, "step"), 0);
        initStep(p, id, 0);
        markDirty();
        log(p, id, "start", 0);
        saveIfDirty();   // a kill or crash must not lose the start: the next join would count them as an old player
        refresh(p);
        if (directedCfg(id) != null) {
            progress.set(path(p, id, "spawns"), 0);
            directed.put(p.getUniqueId() + "|" + id, new Directed());
        }
    }

    private void finish(Player p, String id) {
        progress.set(path(p, id, "done"), true);
        progress.set(path(p, id, "doneAt"), System.currentTimeMillis());
        markDirty();
        log(p, id, "done", stepIndex(p, id));
        ConfigurationSection q = questline(id);
        ConfigurationSection reveal = q == null ? null : q.getConfigurationSection("reveal_on_complete");
        if (reveal != null) {
            String title = reveal.getString("title", "");
            String subtitle = reveal.getString("subtitle", "");
            if (!title.isEmpty() || !subtitle.isEmpty()) {
                // A second later, so the claim messages and sounds of placing the book do not cover it.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!p.isOnline()) return;
                    p.sendTitle(utils.chat(title), utils.chat(subtitle), 10, 100, 30);
                    plugin.getLogger().info("[quest] " + p.getName() + " reveal title sent");
                    p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                }, 20L);
            }
            String actionbar = reveal.getString("actionbar");
            if (actionbar != null) p.sendActionBar(net.kyori.adventure.text.Component.text(utils.chat(actionbar)));
            for (String line : reveal.getStringList("messages")) p.sendMessage(utils.chat(line));
        }
        List<String> unlocks = q == null ? List.of() : q.getStringList("unlock_on_complete");
        for (String u : unlocks) unlock(p, u);
        saveIfDirty();
        refresh(p);
        if (unlocks.contains("claim_bossbar"))
            utils.updateBossBar(p, utils.getClaimID(p.getLocation()));
    }

    private void advance(Player p, String id) {
        int next = stepIndex(p, id) + 1;
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            s.lastProgressAt = System.currentTimeMillis();
            s.nudged.clear();
        }
        // The reveal of the step just finished (for example an action-bar line) plays before the next step shows.
        List<Map<?, ?>> steps = steps(id);
        int done = next - 1;
        if (done >= 0 && done < steps.size()) {
            String actionbar = str(sub(steps.get(done), "reveal"), "actionbar");
            if (actionbar != null) p.sendActionBar(net.kyori.adventure.text.Component.text(utils.chat(actionbar)));
        }
        progress.set(path(p, id, "step"), next);
        initStep(p, id, next);
        markDirty();
        log(p, id, "step", next);
        saveIfDirty();
        if (next >= steps.size()) finish(p, id);
        else refresh(p);
    }

    /** One line per step so the point where players drop off is visible (success metric in the spec). */
    private void log(Player p, String id, String what, int step) {
        plugin.getLogger().info("[quest] " + p.getName() + " " + id + " " + what + " " + step);
    }

    // ---- boss bar -----------------------------------------------------------------------

    /** The questline the player should see now: the newest active high-priority one, else the oldest-started active one. */
    private String displayed(Player p) {
        String best = null;
        long bestStarted = 0;
        boolean bestHigh = false;
        for (String id : questlineIds()) {
            if (!isActive(p, id)) continue;
            boolean high = "high".equalsIgnoreCase(questline(id).getString("priority", "normal"));
            long started = progress.getLong(path(p, id, "started"));
            boolean better = best == null
                    || (high && !bestHigh)
                    || (high == bestHigh && (high ? started > bestStarted : started < bestStarted));
            if (better) { best = id; bestHigh = high; bestStarted = started; }
        }
        return best;
    }

    private void refresh(Player p) {
        Session s = sessions.computeIfAbsent(p.getUniqueId(), k -> new Session());
        String id = displayed(p);
        if (id == null) {
            s.bar.removeAll();
            s.shownKey = "";
            return;
        }
        List<Map<?, ?>> steps = steps(id);
        int idx = Math.min(stepIndex(p, id), steps.size() - 1);
        if (idx < 0) { s.bar.removeAll(); return; }
        String text = str(steps.get(idx), "text");
        String key = id + "#" + idx;
        if (!key.equals(s.shownKey)) {
            s.shownKey = key;
            s.lastProgressAt = System.currentTimeMillis();
            s.nudged.clear();
        }
        int[] hn = haveNeed(p, id, steps.get(idx));
        s.bar.setTitle(utils.chat("&e" + label(text, hn)));
        double within = hn[1] > 0 ? Math.min(1.0, hn[0] / (double) hn[1]) : 0.0;
        s.bar.setProgress(Math.max(0.0, Math.min(1.0, (idx + within) / steps.size())));
        if (!s.bar.getPlayers().contains(p)) s.bar.addPlayer(p);
    }

    /** {have, need} for a step that counts something, else {0, 0}. */
    private int[] haveNeed(Player p, String id, Map<?, ?> step) {
        Map<String, Object> c = sub(step, "complete");
        String on = str(c, "on");
        if (("craft".equals(on) || "pickup".equals(on)) && c.get("count") instanceof Number && str(c, "source_entity") == null) {
            Material m = Material.matchMaterial(String.valueOf(c.get("item")));
            int need = ((Number) c.get("count")).intValue();
            if (m == null) return new int[]{0, 0};
            int have = "craft".equals(on) ? craftedSince(p, id, m) : pickedOrHeld(p, id, m);
            return new int[]{Math.min(have, need), need};
        }
        if ("has_items".equals(on) && c.get("items") instanceof List) {
            List<?> items = (List<?>) c.get("items");
            int have = 0;
            for (Object o : items) {
                Material m = Material.matchMaterial(String.valueOf(o));
                if (m != null && hasOrSeen(p, id, m)) have++;
            }
            return new int[]{have, items.size()};
        }
        return new int[]{0, 0};
    }

    private static String label(String text, int[] haveNeed) {
        return haveNeed[1] > 1 ? text + " (" + haveNeed[0] + "/" + haveNeed[1] + ")" : text;
    }

    // ---- periodic checks (statistics, inventory, stuck help) -------------------------------

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (String id : questlineIds()) {
                if (isActive(p, id)) checkPolled(p, id);
            }
            String id = displayed(p);
            Session s = sessions.get(p.getUniqueId());
            if (id == null || s == null) continue;
            refresh(p);   // keeps the counts on the bar current
            directedTick(p);
            applyStuckHelp(p, id, s, now);
        }
    }

    private void checkPolled(Player p, String id) {
        // A step can complete several in a row (for example when the player already crafted things).
        for (int guard = 0; guard < 20 && isActive(p, id); guard++) {
            List<Map<?, ?>> steps = steps(id);
            int idx = stepIndex(p, id);
            if (idx >= steps.size()) { finish(p, id); return; }
            Map<String, Object> c = sub(steps.get(idx), "complete");
            String on = str(c, "on");
            boolean ok = false;
            if ("craft".equals(on)) {
                Material m = Material.matchMaterial(str(c, "item"));
                int count = c.get("count") instanceof Number ? ((Number) c.get("count")).intValue() : 1;
                ok = m != null && craftedSince(p, id, m) >= count;
            } else if ("pickup".equals(on) && c.get("count") instanceof Number && str(c, "source_entity") == null) {
                Material m = Material.matchMaterial(String.valueOf(c.get("item")));
                ok = m != null && pickedOrHeld(p, id, m) >= ((Number) c.get("count")).intValue();
            } else if ("has_items".equals(on)) {
                Object items = c.get("items");
                ok = items instanceof List && !((List<?>) items).isEmpty();
                if (ok) for (Object o : (List<?>) items) {
                    Material m = Material.matchMaterial(String.valueOf(o));
                    if (m == null || !hasOrSeen(p, id, m)) { ok = false; break; }
                }
            }
            if (!ok) return;
            advance(p, id);
        }
    }

    private void applyStuckHelp(Player p, String id, Session s, long now) {
        List<Map<?, ?>> steps = steps(id);
        int idx = stepIndex(p, id);
        if (idx < 0 || idx >= steps.size()) return;
        Map<String, Object> stuck = sub(steps.get(idx), "stuck");
        if (stuck.isEmpty()) return;
        TreeMap<Integer, String> byTime = new TreeMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) stuck).entrySet()) {
            try { byTime.put(Integer.parseInt(String.valueOf(e.getKey())), String.valueOf(e.getValue())); }
            catch (NumberFormatException ignored) { }
        }
        long waited = (now - s.lastProgressAt) / 1000;
        String text = label(str(steps.get(idx), "text"), haveNeed(p, id, steps.get(idx)));
        boolean first = true;
        for (Map.Entry<Integer, String> e : byTime.entrySet()) {
            if (waited < e.getKey()) break;
            if (first) {
                // The step text never changes; only the "where" help underneath it.
                s.bar.setTitle(utils.chat("&e" + text + " &7- " + e.getValue()));
                first = false;
            } else if (s.nudged.add(e.getKey())) {
                p.sendActionBar(net.kyori.adventure.text.Component.text(utils.chat("&7" + e.getValue())));
            }
        }
    }

    // ---- event triggers ----------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        plugin.getLogger().info("[quest] join " + p.getName() + " playedBefore=" + p.hasPlayedBefore()
                + " known=" + progress.contains(id.toString()) + " questlines=" + questlineIds());
        if (!progress.contains(id.toString())) {
            if (p.hasPlayedBefore()) {
                // Here before questlines existed: nothing to teach, and they keep the claim bar.
                progress.set(id + ".grandfathered", true);
                unlock(p, "claim_bossbar");
                markDirty();
                saveIfDirty();
            } else {
                for (String q : questlineIds()) {
                    ConfigurationSection start = questline(q).getConfigurationSection("start");
                    if (start != null && "first_join".equals(start.getString("on"))) startQuestline(p, q);
                }
            }
        }
        refresh(p);
        utils.updateBossBar(p, utils.getClaimID(p.getLocation()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Session s = sessions.remove(e.getPlayer().getUniqueId());
        if (s != null) s.bar.removeAll();
        saveIfDirty();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        deathTick = Bukkit.getCurrentTick();
        deathLoc = e.getEntity().getLocation();
        deathType = e.getEntityType();
    }

    /** Drops spawn in the same tick as the death; remember what killed-off entity each item came from. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        if (deathLoc == null || Bukkit.getCurrentTick() != deathTick) return;
        if (!Objects.equals(deathLoc.getWorld(), e.getLocation().getWorld())) return;
        if (deathLoc.distanceSquared(e.getLocation()) > 9) return;
        long now = System.currentTimeMillis();
        itemSources.put(e.getEntity().getUniqueId(), new ItemSource(deathType, now));
        itemSources.values().removeIf(src -> now - src.at > ITEM_SOURCE_TTL_MS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        Item item = e.getItem();
        ItemStack stack = item.getItemStack();
        ItemSource src = itemSources.get(item.getUniqueId());
        for (String id : questlineIds()) {
            if (isActive(p, id) && hasItemsMaterials(id).contains(stack.getType())) {
                String seenKey = path(p, id, "seen_" + stack.getType().name());
                if (!progress.getBoolean(seenKey)) { progress.set(seenKey, true); markDirty(); }
            }
        }
        for (String id : questlineIds()) {
            if (!isActive(p, id)) continue;
            List<Map<?, ?>> steps = steps(id);
            int idx = stepIndex(p, id);
            if (idx >= steps.size()) continue;
            Map<String, Object> c = sub(steps.get(idx), "complete");
            if (!"pickup".equals(str(c, "on"))) continue;
            if (!pickupMatches(c, stack.getType())) continue;
            String need = str(c, "source_entity");
            if (need == null && c.get("count") instanceof Number) {
                // Counted two ways, the larger wins: everything picked up since the step began, and what the
                // player holds now. Checked once the item is in the inventory.
                int taken = stack.getAmount() - e.getRemaining();
                if (taken > 0) {
                    String key = path(p, id, "picked");
                    progress.set(key, progress.getInt(key) + taken);
                    markDirty();
                }
                Bukkit.getScheduler().runTask(plugin, () -> { checkPolled(p, id); refresh(p); });
                continue;
            }
            if (need != null && (src == null || !src.type.name().equalsIgnoreCase(need))) continue;
            advance(p, id);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        // The statistic updates after the event; check once it has.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String id : questlineIds()) if (isActive(p, id)) checkPolled(p, id);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent e) {
        Player p = e.getPlayer();
        BookMeta meta = e.getNewBookMeta();
        StringBuilder sb = new StringBuilder();
        for (String page : meta.getPages()) sb.append(page).append(' ');
        String text = org.bukkit.ChatColor.stripColor(sb.toString()).toLowerCase(Locale.ROOT);
        for (String id : questlineIds()) {
            if (!isActive(p, id)) continue;
            List<Map<?, ?>> steps = steps(id);
            int idx = stepIndex(p, id);
            if (idx >= steps.size()) continue;
            Map<String, Object> c = sub(steps.get(idx), "complete");
            if (!"write_book".equals(str(c, "on"))) continue;
            Object words = c.get("contains");
            if (!(words instanceof List)) continue;
            boolean all = true;
            for (Object w : (List<?>) words) {
                String word = String.valueOf(w).replace("{player}", p.getName()).toLowerCase(Locale.ROOT);
                if (!text.contains(word)) { all = false; break; }
            }
            if (all) advance(p, id);
        }
    }

    @EventHandler
    public void onClaimBookPlaced(ClaimBookPlacedEvent e) {
        Player p = e.player;
        for (String id : questlineIds()) {
            if (!isActive(p, id)) continue;
            List<Map<?, ?>> steps = steps(id);
            int idx = stepIndex(p, id);
            if (idx >= steps.size()) continue;
            if ("claim_book_placed".equals(str(sub(steps.get(idx), "complete"), "on"))) advance(p, id);
        }
    }

    // ---- surprise quests: started by a failed break ---------------------------------------------

    @EventHandler
    public void onFailedBreak(B4BlockBreakEvent e) {
        if (e.success) return;
        Player p = e.player;
        for (String id : questlineIds()) {
            ConfigurationSection start = questline(id).getConfigurationSection("start");
            if (start == null || !"failed_break".equals(start.getString("on"))) continue;
            if (!start.getStringList("blocks").contains(e.block.getType().name())) continue;
            startQuestline(p, id);   // once per family: a started questline is never started again
        }
    }

    private boolean pickupMatches(Map<String, Object> c, Material type) {
        Object items = c.get("items");
        if (items instanceof List) {
            for (Object o : (List<?>) items) if (type == Material.matchMaterial(String.valueOf(o))) return true;
            return false;
        }
        Material m = Material.matchMaterial(String.valueOf(c.get("item")));
        return m != null && type == m;
    }

    // ---- directed spawns: a plain vanilla mob, only where and when it appears is chosen ----------

    private static final class Directed {
        UUID mob;
        long lastAt = System.currentTimeMillis();
        boolean diedToIt;
        boolean tenMinutesDone;
    }

    private final Map<String, Directed> directed = new HashMap<>();

    private ConfigurationSection directedCfg(String id) {
        ConfigurationSection q = questline(id);
        return q == null ? null : q.getConfigurationSection("directed_spawn");
    }

    private NamespacedKey ownerKey() {
        return new NamespacedKey(plugin, "quest_owner");
    }

    private void directedTick(Player p) {
        long now = System.currentTimeMillis();
        for (String id : questlineIds()) {
            if (directedCfg(id) == null || !isActive(p, id)) continue;
            // A questline waiting behind a newer one does not spawn anything: the player is not working on it now.
            if (!id.equals(displayed(p))) continue;
            Directed d = directed.computeIfAbsent(p.getUniqueId() + "|" + id, k -> new Directed());
            if (progress.getInt(path(p, id, "spawns"), 0) == 0) {
                spawnDirected(p, id, false);   // the first one; tried again each pass until a hidden spot is found
                continue;
            }
            org.bukkit.entity.Entity cur = d.mob == null ? null : Bukkit.getEntity(d.mob);
            boolean alive = cur instanceof Mob && cur.isValid() && !cur.isDead();
            boolean engaged = alive && p.equals(((Mob) cur).getTarget());
            if ((!alive || !engaged) && now - d.lastAt >= 120_000) spawnDirected(p, id, true);   // not engaged after 2 min
            long started = progress.getLong(path(p, id, "started"));
            if (!d.tenMinutesDone && now - started >= 600_000) {   // ignored for 10 min: one more, the text help is on the bar
                d.tenMinutesDone = true;
                spawnDirected(p, id, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!(p.getLastDamageCause() instanceof EntityDamageByEntityEvent)) return;
        org.bukkit.entity.Entity damager = ((EntityDamageByEntityEvent) p.getLastDamageCause()).getDamager();
        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof org.bukkit.entity.Entity)
            damager = (org.bukkit.entity.Entity) ((Projectile) damager).getShooter();
        String owner = damager.getPersistentDataContainer().get(ownerKey(), PersistentDataType.STRING);
        if (owner == null || !owner.equals(p.getUniqueId().toString())) return;
        for (String id : questlineIds()) {
            if (directedCfg(id) != null && isActive(p, id))
                directed.computeIfAbsent(p.getUniqueId() + "|" + id, k -> new Directed()).diedToIt = true;
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        for (String id : questlineIds()) {
            Directed d = directed.get(p.getUniqueId() + "|" + id);
            if (d == null || !d.diedToIt) continue;
            d.diedToIt = false;
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && isActive(p, id) && id.equals(displayed(p))) spawnDirected(p, id, true); }, 60L);
        }
    }

    /** Spawns one skeleton behind the player. Returns whether it did (limits and a missing safe spot say no). */
    private boolean spawnDirected(Player p, String id, boolean closer) {
        ConfigurationSection cfg = directedCfg(id);
        if (cfg == null || p.getWorld().getEnvironment() != World.Environment.NORMAL) return false;
        int max = cfg.getInt("max", 3);
        int used = progress.getInt(path(p, id, "spawns"), 0);
        if (used >= max) return false;   // at most 3 per player, ever
        Location spot = findSpawnSpot(p, closer ? 8 : 10, closer ? 12 : 16, true);
        if (spot == null) spot = findSpawnSpot(p, closer ? 8 : 10, closer ? 12 : 16, false);
        if (spot == null) return false;
        World w = p.getWorld();
        boolean day = w.getTime() < 12300 || w.getTime() > 23850;
        boolean sunlit = spot.getBlock().getLightFromSky() >= 12;
        Skeleton sk = w.spawn(spot, Skeleton.class, mob -> {
            mob.getPersistentDataContainer().set(ownerKey(), PersistentDataType.STRING, p.getUniqueId().toString());
            if (mob.getEquipment() != null) {
                if (mob.getEquipment().getItemInMainHand().getType().isAir())
                    mob.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
                // A daytime skeleton in the open wears a helmet so it does not burn, as natural ones do.
                if (day && sunlit) {
                    mob.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
                    mob.getEquipment().setHelmetDropChance(0.085f);
                }
            }
        });
        Directed d = directed.computeIfAbsent(p.getUniqueId() + "|" + id, k -> new Directed());
        d.mob = sk.getUniqueId();
        d.lastAt = System.currentTimeMillis();
        progress.set(path(p, id, "spawns"), used + 1);
        markDirty();
        log(p, id, "directed spawn " + (used + 1), stepIndex(p, id));
        return true;
    }

    private static final Set<Material> BAD_FLOOR = EnumSet.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE,
            Material.SOUL_FIRE, Material.POWDER_SNOW, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.WATER,
            Material.LAVA, Material.SWEET_BERRY_BUSH, Material.COBWEB);

    /** A hidden standing spot 10 to 16 blocks away, outside the view cone (and without sight of the eyes if strict). */
    private Location findSpawnSpot(Player p, int minD, int maxD, boolean strict) {
        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection().setY(0);
        if (look.lengthSquared() < 1e-4) look = new Vector(0, 0, 1);
        look.normalize();
        World w = p.getWorld();
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        Location best = null;
        int bestSky = 99;
        for (int i = 0; i < 80; i++) {
            double dist = minD + r.nextDouble() * (maxD - minD);
            double ang = r.nextDouble() * Math.PI * 2;
            Vector dir = new Vector(Math.cos(ang), 0, Math.sin(ang));
            if (dir.dot(look) > -0.342) continue;   // inside 110 degrees of where the player faces
            int x = p.getLocation().getBlockX() + (int) Math.round(dir.getX() * dist);
            int z = p.getLocation().getBlockZ() + (int) Math.round(dir.getZ() * dist);
            Location spot = standingSpot(w, x, z, p.getLocation().getBlockY());
            if (spot == null) continue;
            if (plugin.cfg.getClaimData().contains(utils.getClaimID(spot))) continue;   // never inside a claim
            if (strict) {
                Vector to = spot.clone().add(0.0, 1.6, 0.0).toVector().subtract(eye.toVector());
                RayTraceResult hit = w.rayTraceBlocks(eye, to.clone().normalize(), to.length(), FluidCollisionMode.NEVER, true);
                if (hit == null) continue;   // a clear line to the eyes: it could be seen
            }
            int sky = spot.getBlock().getLightFromSky();   // prefer shade
            if (sky < bestSky) { best = spot; bestSky = sky; }
        }
        return best;
    }

    private Location standingSpot(World w, int x, int z, int nearY) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        for (int y = nearY + 6; y >= nearY - 8; y--) {
            org.bukkit.block.Block floor = w.getBlockAt(x, y, z);
            if (!floor.getType().isSolid() || BAD_FLOOR.contains(floor.getType())) continue;
            if (!w.getBlockAt(x, y + 1, z).getType().isAir() || !w.getBlockAt(x, y + 2, z).getType().isAir()) continue;
            return new Location(w, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }
}
