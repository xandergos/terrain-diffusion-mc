package com.github.xandergos.terraindiffusionmc.config;

import java.util.List;
import java.util.Map;

public class BiomeRegion {
    public String name;
    /** Keys are variable names: "temperature", "precipitation", "elevation", "slope". */
    public Map<String, RangeCondition> conditions;
    public List<BiomeEntry> biomes;

    public static class RangeCondition {
        public float min = Float.NEGATIVE_INFINITY;
        public float max = Float.POSITIVE_INFINITY;
    }
}
