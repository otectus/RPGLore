package com.rpglore.lore;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

public record DropCondition(
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

    public enum TimeFilter { ANY, DAY_ONLY, NIGHT_ONLY }
    public enum WeatherFilter { ANY, CLEAR_ONLY, RAIN_ONLY, THUNDER_ONLY }

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
