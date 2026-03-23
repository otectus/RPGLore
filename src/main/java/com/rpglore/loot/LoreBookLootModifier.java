package com.rpglore.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpglore.RpgLoreMod;
import com.rpglore.config.BooksConfigLoader;
import com.rpglore.config.ServerConfig;
import com.rpglore.lore.DropConditionContext;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookItem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class LoreBookLootModifier extends LootModifier {

    public static final Supplier<Codec<LoreBookLootModifier>> CODEC = () ->
            RecordCodecBuilder.create(inst -> codecStart(inst)
                    .apply(inst, LoreBookLootModifier::new));

    public LoreBookLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        Entity victimEntity = context.getParamOrNull(LootContextParams.THIS_ENTITY);

        // Must be a living entity dying
        if (!(victimEntity instanceof LivingEntity victim)) return generatedLoot;

        // Determine killer: check KILLER_ENTITY first, then LAST_DAMAGE_PLAYER as fallback.
        // Forge entity death loot tables primarily provide LAST_DAMAGE_PLAYER for player kills.
        Player player = null;
        Entity killerEntity = context.getParamOrNull(LootContextParams.KILLER_ENTITY);
        if (killerEntity instanceof Player p) {
            player = p;
        } else {
            // LAST_DAMAGE_PLAYER is the reliable source for player-killed-entity loot contexts
            player = context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
        }

        // Check if player kill is required globally
        if (!ServerConfig.ALLOW_NON_PLAYER_KILLS.get() && player == null) return generatedLoot;

        // Check hostile mob filter
        if (ServerConfig.ONLY_HOSTILE_MOBS.get()) {
            if (victim.getType().getCategory() != MobCategory.MONSTER) return generatedLoot;
        }

        // Build condition context
        DropConditionContext ctx = DropConditionContext.from(context, victim, player);

        // Get all matching book definitions
        List<LoreBookDefinition> matching = BooksConfigLoader.getMatchingBooks(ctx);
        if (matching.isEmpty()) return generatedLoot;

        // Calculate effective global drop chance
        double chance = ServerConfig.GLOBAL_DROP_CHANCE.get();
        if (ServerConfig.LOOT_SCALING.get() && player != null) {
            int lootingLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MOB_LOOTING,
                    player.getMainHandItem());
            chance += lootingLevel * 0.01;
        }

        // Roll global drop chance
        RandomSource random = context.getRandom();
        if (random.nextDouble() >= chance) return generatedLoot;

        // Select books (by weight or uniform), capped at maxBooksPerKill
        int maxBooks = ServerConfig.MAX_BOOKS_PER_KILL.get();
        boolean useWeights = ServerConfig.ENABLE_PER_BOOK_WEIGHTS.get();
        List<LoreBookDefinition> selected = selectBooks(matching, random, maxBooks, useWeights);

        // Create ItemStacks and add to loot
        for (LoreBookDefinition def : selected) {
            // Per-book base_chance override
            if (def.dropCondition().baseChance() != null) {
                if (random.nextDouble() >= def.dropCondition().baseChance()) continue;
            }

            // Per-player copy limit
            if (player != null && def.dropCondition().maxCopiesPerPlayer() >= 0) {
                if (!BooksConfigLoader.canPlayerReceive(player.getUUID(), def.id(),
                        def.dropCondition().maxCopiesPerPlayer())) {
                    continue;
                }
                BooksConfigLoader.recordPlayerReceived(player.getUUID(), def.id());
            }

            RpgLoreMod.LOGGER.debug("Dropping lore book '{}' from {}", def.id(),
                    victim.getType().getDescriptionId());
            generatedLoot.add(LoreBookItem.createStack(def));
        }

        return generatedLoot;
    }

    private static List<LoreBookDefinition> selectBooks(
            List<LoreBookDefinition> candidates, RandomSource random, int max, boolean useWeights) {

        if (candidates.size() <= max && !useWeights) {
            return candidates;
        }

        List<LoreBookDefinition> selected = new ArrayList<>();
        List<LoreBookDefinition> pool = new ArrayList<>(candidates);

        for (int i = 0; i < max && !pool.isEmpty(); i++) {
            if (useWeights) {
                double totalWeight = pool.stream().mapToDouble(LoreBookDefinition::weight).sum();
                double roll = random.nextDouble() * totalWeight;
                double cumulative = 0;

                LoreBookDefinition pick = pool.get(pool.size() - 1);
                for (LoreBookDefinition def : pool) {
                    cumulative += def.weight();
                    if (roll < cumulative) {
                        pick = def;
                        break;
                    }
                }
                selected.add(pick);
                pool.remove(pick);
            } else {
                int idx = random.nextInt(pool.size());
                selected.add(pool.remove(idx));
            }
        }

        return selected;
    }
}
