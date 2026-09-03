package com.rpglore.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpglore.RpgLoreMod;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.config.LoreBookRegistry.LootTableCandidate;
import com.rpglore.config.ServerConfig;
import com.rpglore.lore.LoreBookItem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Injects lore books into named loot tables (chests, fishing, gameplay tables) for books
 * that declare a {@code loot_table} acquisition rule.
 *
 * <p>Mob drops are handled exclusively by {@link LoreBookLootModifier}; entity tables are
 * skipped here so a kill is never handled twice.
 */
public class LootTableLoreModifier extends LootModifier {

    /** Entity death tables live under this path prefix and belong to the entity modifier. */
    private static final String ENTITY_TABLE_PREFIX = "entities/";

    public static final Supplier<Codec<LootTableLoreModifier>> CODEC = () ->
            RecordCodecBuilder.create(inst -> codecStart(inst)
                    .apply(inst, LootTableLoreModifier::new));

    public LootTableLoreModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!ServerConfig.ENABLE_LOOT_TABLES.get()) return generatedLoot;

        ResourceLocation id = context.getQueriedLootTableId();
        if (id == null || id.getPath().startsWith(ENTITY_TABLE_PREFIX)) return generatedLoot;

        List<LootTableCandidate> candidates = LoreBookRegistry.getLootTableRules(id);
        if (candidates.isEmpty()) return generatedLoot;

        RandomSource random = context.getRandom();
        boolean useWeights = ServerConfig.ENABLE_PER_BOOK_WEIGHTS.get();

        // Each candidate rolls its own chance first; whatever survives competes on weight,
        // so a table never yields more than one lore book per generation.
        List<LootTableCandidate> rolled = new ArrayList<>();
        for (LootTableCandidate candidate : candidates) {
            if (random.nextDouble() < candidate.rule().chance()) {
                rolled.add(candidate);
            }
        }
        if (rolled.isEmpty()) return generatedLoot;

        LootTableCandidate picked = pickWeighted(rolled, random, useWeights);

        // No per-player cap: container generation has no player to attribute the copy to,
        // so maxCopiesPerPlayer (an entity_drop concept) deliberately does not apply here.
        RpgLoreMod.LOGGER.debug("Injecting lore book '{}' into loot table {}",
                picked.definition().id(), id);
        generatedLoot.add(LoreBookItem.createStack(picked.definition()));

        return generatedLoot;
    }

    /**
     * Weight of a candidate is the rule's own weight multiplied by the book weight when
     * per-book weights are enabled, so a pack can tune a book globally and still bias one
     * table against another.
     */
    private static LootTableCandidate pickWeighted(List<LootTableCandidate> pool, RandomSource random,
                                                   boolean useWeights) {
        if (pool.size() == 1) return pool.get(0);

        double total = 0;
        for (LootTableCandidate candidate : pool) {
            total += weightOf(candidate, useWeights);
        }

        double roll = random.nextDouble() * total;
        double cumulative = 0;
        for (LootTableCandidate candidate : pool) {
            cumulative += weightOf(candidate, useWeights);
            if (roll < cumulative) return candidate;
        }
        return pool.get(pool.size() - 1);
    }

    private static double weightOf(LootTableCandidate candidate, boolean useWeights) {
        return candidate.rule().weight() * (useWeights ? candidate.definition().weight() : 1.0);
    }
}
