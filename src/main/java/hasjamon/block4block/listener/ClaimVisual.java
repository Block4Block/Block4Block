package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClaimVisual implements Listener {
    // Shared set for players with the visual enabled via command.
    private final Set<UUID> visualEnabledPlayers = new HashSet<>();
    private final Block4Block block4BlockPlugin;

    public ClaimVisual(Block4Block block4BlockPlugin) {
        this.block4BlockPlugin = block4BlockPlugin;
        startVisualUpdater(); // Start the visual updater when initialized.
    }

    // Start the visual updater task.
    private void startVisualUpdater() {
        Bukkit.getScheduler().runTaskTimer(block4BlockPlugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (shouldShowVisual(player)) {
                    showChunkBorders(player);
                } else if (visualEnabledPlayers.contains(player.getUniqueId())) {
                    visualEnabledPlayers.remove(player.getUniqueId());
                    String message = utils.chat("&cClaim visual disabled due to book removal or command.");
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                }
            }
        }, 0L, 10L); // Update every 10 ticks (0.5 seconds)
    }

    // Check if the player should see the visual (via holding a book or command toggle).
    private boolean shouldShowVisual(Player player) {
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        if (offHandItem != null) {
            Material type = offHandItem.getType();
            if (type == Material.WRITTEN_BOOK || type == Material.WRITABLE_BOOK) {
                return true;
            }
        }
        return visualEnabledPlayers.contains(player.getUniqueId());
    }

    // Render the chunk borders in a 3x3 grid around the player's current chunk.
    private void showChunkBorders(Player player) {
        Chunk playerChunk = player.getLocation().getChunk();
        int chunkX = playerChunk.getX();
        int chunkZ = playerChunk.getZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk chunk = player.getWorld().getChunkAt(chunkX + dx, chunkZ + dz);

                // Get claim information using utils.
                String claimID = utils.getClaimID(chunk.getBlock(0, 0, 0).getLocation());
                String[] members = utils.getMembers(claimID);

                // Choose particle color based on claim ownership.
                Particle.DustOptions dustOptions;
                if (members == null) {
                    dustOptions = new Particle.DustOptions(org.bukkit.Color.GRAY, 1.5F);
                } else if (utils.isMemberOfClaim(members, player)) {
                    dustOptions = new Particle.DustOptions(org.bukkit.Color.GREEN, 1.5F);
                } else {
                    dustOptions = new Particle.DustOptions(org.bukkit.Color.RED, 1.5F);
                }

                showChunkOutline(chunk, player, dustOptions);
            }
        }
    }

    // Draw particle outlines along the borders of a chunk.
    private void showChunkOutline(Chunk chunk, Player player, Particle.DustOptions dustOptions) {
        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        int y = player.getLocation().getBlockY();

        for (int x = minX; x <= maxX; x++) {
            drawParticle(player, x, y, minZ, dustOptions);
            drawParticle(player, x, y, maxZ, dustOptions);
        }
        for (int z = minZ; z <= maxZ; z++) {
            drawParticle(player, minX, y, z, dustOptions);
            drawParticle(player, maxX, y, z, dustOptions);
        }
    }

    // Spawn a particle at a specific location.
    private void drawParticle(Player player, int x, int y, int z, Particle.DustOptions dustOptions) {
        Location particleLocation = new Location(player.getWorld(), x + 0.5, y + 1.5, z + 0.5);
        player.spawnParticle(Particle.DUST, particleLocation, 1, dustOptions);
    }

    // Clear active visuals when the plugin is disabled.
    public void disableVisualUpdater() {
        visualEnabledPlayers.clear();
    }

    // Getter for visualEnabledPlayers to be accessed by ClaimVisualCommand.
    public Set<UUID> getVisualEnabledPlayers() {
        return visualEnabledPlayers;
    }
}