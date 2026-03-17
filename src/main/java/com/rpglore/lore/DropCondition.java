package com.rpglore.lore;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

public class DropCondition {

    public enum TimeFilter { ANY, DAY_ONLY, NIGHT_ONLY }
    public enum WeatherFilter { ANY, CLEAR_ONLY, RAIN_ONLY, THUNDER_ONLY }

    private final @Nullable List<ResourceLocation> mobTypes;
    private final @Nullable List<String> mobTags;
    private final @Nullable List<ResourceLocation> biomes;
    private final @Nullable List<String> biomeTags;
    private final @Nullable List<ResourceLocation> dimensions;
    private final @Nullable Integer minY;
    private final @Nullable Integer maxY;
    private final TimeFilter time;
    private final WeatherFilter weather;
    private final boolean requirePlayerKill;
    private final @Nullable Double baseChance;
    private final int maxCopiesPerPlayer;

    public DropCondition(
            @Nullable List<ResourceLocation> mobTypes,
            @Nullable List<String> mobTags,
            @Nullable List<ResourceLocation> biomes,
            @Nullable List<String> biomeTags,
            @Nullable List<ResourceLocation> dimensions,
            @Nullable Integer minY,
            @Nullable Integer maxY,
            TimeFilter time,
            WeatherFilter weather,
            boolean requirePlayerKill,
            @Nullable Double baseChance,
            int maxCopiesPerPlayer
    ) {
        this.mobTypes = mobTypes;
        this.mobTags = mobTags;
        this.biomes = biomes;
        this.biomeTags = biomeTags;
        this.dimensions = dimensions;
        this.minY = minY;
        this.maxY = maxY;
        this.time = time;
        this.weather = weather;
        this.requirePlayerKill = requirePlayerKill;
        this.baseChance = baseChance;
        this.maxCopiesPerPlayer = maxCopiesPerPlayer;
    }

    public @Nullable List<ResourceLocation> mobTypes() { return mobTypes; }
    public @Nullable List<String> mobTags() { return mobTags; }
    public @Nullable List<ResourceLocation> biomes() { return biomes; }
    public @Nullable List<String> biomeTags() { return biomeTags; }
    public @Nullable List<ResourceLocation> dimensions() { return dimensions; }
    public @Nullable Integer minY() { return minY; }
    public @Nullable Integer maxY() { return maxY; }
    public TimeFilter time() { return time; }
    public WeatherFilter weather() { return weather; }
    public boolean requirePlayerKill() { return requirePlayerKill; }
    public @Nullable Double baseChance() { return baseChance; }
    public int maxCopiesPerPlayer() { return maxCopiesPerPlayer; }

    /** Default condition with no filters — matches everything, requires player kill. */
    public static DropCondition defaultCondition() {
        return new DropCondition(
                null, null, null, null, null,
                null, null,
                TimeFilter.ANY, WeatherFilter.ANY,
                true, null, -1
        );
    }
}
