package hasjamon.block4block.utils;

import org.bukkit.Material;

public class GracePeriod {
    public Long timestamp;
    public Material type;

    public GracePeriod(Long timestamp, Material type){
        this.timestamp = timestamp;
        this.type = type;
    }
}
