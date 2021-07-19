package hasjamon.block4block.listener;

import hasjamon.block4block.Block4Block;
import hasjamon.block4block.utils.utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.List;

public class LecternRightClick implements Listener {
    private final Block4Block plugin;

    public LecternRightClick(Block4Block plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e){
        if(e.getAction() == Action.RIGHT_CLICK_BLOCK){
            Block b = e.getClickedBlock();

            if(b != null && b.getType() == Material.LECTERN){
                Player p = e.getPlayer();
                ItemStack item = p.getInventory().getItemInMainHand();

                if(item.getType() == Material.FILLED_MAP){
                    MapMeta mapMeta = (MapMeta) item.getItemMeta();

                    if(mapMeta != null && mapMeta.getMapView() != null) {
                        MapView view = mapMeta.getMapView();
                        List<MapRenderer> renderers = view.getRenderers();

                        if (renderers.size() == 1 && view.getWorld() != null) {
                            List<String> lore = new ArrayList<>();
                            lore.add(p.getName() + "'s Claim Map");

                            MapView newView = Bukkit.getServer().createMap(view.getWorld());
                            newView.setCenterX(view.getCenterX());
                            newView.setCenterZ(view.getCenterZ());
                            newView.setLocked(view.isLocked());
                            newView.setScale(view.getScale());
                            newView.setTrackingPosition(view.isTrackingPosition());
                            newView.setUnlimitedTracking(view.isUnlimitedTracking());
                            newView.addRenderer(utils.createClaimRenderer(p));
                            newView.addRenderer(utils.createIntruderRenderer(p));

                            mapMeta.setLore(lore);
                            mapMeta.setMapView(newView);
                            item.setItemMeta(mapMeta);
                            p.getInventory().setItemInMainHand(item);

                            FileConfiguration claimMaps = plugin.cfg.getClaimMaps();

                            claimMaps.set(Integer.toString(newView.getId()), p.getUniqueId().toString());
                            plugin.cfg.saveClaimMaps();
                        }
                    }
                }
            }
        }
    }
}
