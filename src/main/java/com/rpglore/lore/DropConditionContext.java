package com.rpglore.lore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record DropConditionContext(
        ResourceLocation victimType,
        Set<String> victimEntityTags,
        @Nullable Player killer,
        ResourceLocation dimension,
        ResourceLocation biome,
        Set<String> biomeTags,
        double victimY,
        long dayTime,
        boolean isDay,
        boolean isRaining,
        boolean isThundering,
        boolean wasPlayerKill
) {

    public static DropConditionContext from(LootContext lootContext, LivingEntity victim, @Nullable Player killer) {
        ServerLevel level = lootContext.getLevel();

        ResourceLocation victimType = ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());

        // Gather entity type tags
        Set<String> victimEntityTags = ForgeRegistries.ENTITY_TYPES.tags()
                .getReverseTag(victim.getType())
                .map(tag -> tag.getTagKeys()
                        .map(k -> k.location().toString())
                        .collect(Collectors.toSet()))
                .orElse(Set.of());

        ResourceLocation dimension = level.dimension().location();

        BlockPos pos = victim.blockPosition();
        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceLocation biomeId = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getKey(biomeHolder.value());

        Set<String> biomeTags = biomeHolder.tags()
                .map(TagKey::location)
                .map(ResourceLocation::toString)
                .collect(Collectors.toSet());

        double y = victim.getY();
        long dayTime = level.getDayTime();
        boolean isDay = level.isDay();
        boolean isRaining = level.isRaining();
        boolean isThundering = level.isThundering();
        boolean wasPlayerKill = killer != null;

        return new DropConditionContext(
                victimType, victimEntityTags, killer,
                dimension, biomeId != null ? biomeId : new ResourceLocation("minecraft", "plains"),
                biomeTags, y, dayTime, isDay,
                isRaining, isThundering, wasPlayerKill
        );
    }

    public boolean matches(DropCondition cond) {
        // Player kill check
        if (cond.requirePlayerKill() && !wasPlayerKill) return false;

        // Mob type whitelist
        if (cond.mobTypes() != null && !cond.mobTypes().isEmpty()) {
            if (!cond.mobTypes().contains(victimType)) return false;
        }

        // Mob tag whitelist
        if (cond.mobTags() != null && !cond.mobTags().isEmpty()) {
            boolean anyMatch = cond.mobTags().stream().anyMatch(victimEntityTags::contains);
            if (!anyMatch) return false;
        }

        // Biome whitelist
        if (cond.biomes() != null && !cond.biomes().isEmpty()) {
            if (!cond.biomes().contains(biome)) return false;
        }

        // Biome tag whitelist
        if (cond.biomeTags() != null && !cond.biomeTags().isEmpty()) {
            boolean anyMatch = cond.biomeTags().stream().anyMatch(biomeTags::contains);
            if (!anyMatch) return false;
        }

        // Dimension whitelist
        if (cond.dimensions() != null && !cond.dimensions().isEmpty()) {
            if (!cond.dimensions().contains(dimension)) return false;
        }

        // Y range
        if (cond.minY() != null && victimY < cond.minY()) return false;
        if (cond.maxY() != null && victimY > cond.maxY()) return false;

        // Time of day
        if (cond.time() != DropCondition.TimeFilter.ANY) {
            if (cond.time() == DropCondition.TimeFilter.DAY_ONLY && !isDay) return false;
            if (cond.time() == DropCondition.TimeFilter.NIGHT_ONLY && isDay) return false;
        }

        // Weather
        if (cond.weather() != DropCondition.WeatherFilter.ANY) {
            switch (cond.weather()) {
                case CLEAR_ONLY -> { if (isRaining || isThundering) return false; }
                case RAIN_ONLY -> { if (!isRaining) return false; }
                case THUNDER_ONLY -> { if (!isThundering) return false; }
            }
        }

        return true;
    }
}
