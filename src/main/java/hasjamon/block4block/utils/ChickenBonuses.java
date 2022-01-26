package hasjamon.block4block.utils;

import java.util.Map;

public class ChickenBonuses {
    public Map<Character, Integer> letterBonuses;
    public int numNamedChickens;

    public ChickenBonuses(Map<Character, Integer> letterBonuses, int numNamedChickens){
        this.letterBonuses = letterBonuses;
        this.numNamedChickens = numNamedChickens;
    }
}
