package hasjamon.block4block.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lectern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.*;


/*
 * NOTE: This class depends entirely on ProtocolLib.
 * TODO: Re-enable or test fully when ProtocolLib supports Minecraft 1.21.5.
 * As of now (version 5.4.0-SNAPSHOT-742), it only supports up to 1.21.3.
 */
public class BookReadLectern implements Listener {
    private final Block4Block plugin;
    private final ProtocolManager protocolManager;
    private final Map<String, Boolean> processingLecterns = new HashMap<>();

    public BookReadLectern(Block4Block plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    @EventHandler
    public void onLecternRead(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null
                || e.getClickedBlock().getType() != Material.LECTERN) return;

        Player player = e.getPlayer();
        Block lecternBlock = e.getClickedBlock();
        String lecternKey = lecternBlock.getWorld().getName() + "," +
                lecternBlock.getX() + "," +
                lecternBlock.getY() + "," +
                lecternBlock.getZ();

        if (processingLecterns.getOrDefault(lecternKey, false)) return;

        BlockState state = lecternBlock.getState();
        if (!(state instanceof Lectern)) return;

        Lectern lectern = (Lectern) state;
        ItemStack book = lectern.getInventory().getItem(0);
        if (book == null || book.getType() != Material.WRITTEN_BOOK) return;

        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        boolean bookNeedsUpdate = false;

        if (meta.getLore() != null) {
            try {
                FileConfiguration masterBooks = plugin.cfg.getMasterBooks();
                String bookID = String.join("", meta.getLore()).substring(17);

                if (plugin.getConfig().getBoolean("enable-master-books")
                        && masterBooks.contains(bookID + ".pages")) {
                    List<String> masterBookPages = masterBooks.getStringList(bookID + ".pages");

                    if (!meta.getPages().equals(masterBookPages)) {
                        meta.setPages(masterBookPages);
                        bookNeedsUpdate = true;
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Error processing book lore: " + ex.getMessage());
            }
        }

        String claimID = utils.getClaimID(lectern.getLocation());
        if (plugin.getConfig().getBoolean("enable-claim-takeovers")) {
            FileConfiguration claimTakeovers = plugin.cfg.getClaimTakeovers();

            if (claimTakeovers.contains(claimID)) {
                List<String> replacements = claimTakeovers.getStringList(claimID);
                if (!replacements.isEmpty()) {
                    List<String> pages = meta.getPages();
                    boolean contentChanged = false;

                    for (String replacement : replacements) {
                        String[] parts = replacement.split("\\|");
                        if (parts.length == 2 && utils.replaceInClaimPages(pages, parts[0], parts[1])) {
                            contentChanged = true;
                        }
                    }

                    if (contentChanged) {
                        meta.setPages(pages);
                        bookNeedsUpdate = true;
                        claimTakeovers.set(claimID, null);
                        plugin.cfg.saveClaimTakeovers();
                    }
                }
            }
        }

        if (bookNeedsUpdate) {
            e.setCancelled(true);
            processingLecterns.put(lecternKey, true);

            FileConfiguration claimData = plugin.cfg.getClaimData();
            boolean isClaimed = claimData.contains(claimID);
            boolean isClaimBlock = utils.isClaimBlock(lecternBlock);

            List<String> members = null;
            if (isClaimed && isClaimBlock) {
                members = Arrays.asList(utils.getMembers(claimID));
            }

            book.setItemMeta(meta);
            lectern.getInventory().clear();
            lectern.update();

            if (isClaimed && isClaimBlock && members != null) {
                claimData.set(claimID + ".members", members);
                plugin.cfg.saveClaimData();
            }

            lectern.getInventory().setItem(0, book);
            lectern.update();

            // --- Send fake TILE_ENTITY_DATA packet ---
            BlockPosition blockPos = new BlockPosition(
                    lectern.getX(), lectern.getY(), lectern.getZ()
            );

            NbtCompound bookTag = (NbtCompound) NbtFactory.fromItemTag(book);
            NbtCompound lecternNBT = NbtFactory.ofCompound("");
            lecternNBT.put("id", "minecraft:lectern");
            lecternNBT.put("x", lectern.getX());
            lecternNBT.put("y", lectern.getY());
            lecternNBT.put("z", lectern.getZ());
            lecternNBT.put("Book", bookTag);
            lecternNBT.put("HasBook", (byte) 1);
            lecternNBT.put("page", 0);

            PacketContainer packet = new PacketContainer(PacketType.Play.Server.TILE_ENTITY_DATA);
            packet.getBlockPositionModifier().write(0, blockPos);
            packet.getIntegers().write(0, 1); // action type
            packet.getNbtModifier().write(0, lecternNBT);

            try {
                protocolManager.sendServerPacket(player, packet);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.openInventory(lectern.getInventory());
                processingLecterns.remove(lecternKey);
            }, 1L);
        }
    }
}
