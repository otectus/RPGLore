package com.rpglore.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpglore.RpgLoreMod;
import com.rpglore.config.LoreBookRegistry;
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
        List<LoreBookDefinition> matching = LoreBookRegistry.getMatchingBooks(ctx);
        if (matching.isEmpty()) return generatedLoot;

        // Calculate effective global drop chance
        double globalChance = ServerConfig.GLOBAL_DROP_CHANCE.get();
        if (ServerConfig.LOOT_SCALING.get() && player != null) {
            int lootingLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MOB_LOOTING,
                    player.getMainHandItem());
            globalChance += lootingLevel * 0.01;
        }

        RandomSource random = context.getRandom();
        int maxBooks = ServerConfig.MAX_BOOKS_PER_KILL.get();
        boolean useWeights = ServerConfig.ENABLE_PER_BOOK_WEIGHTS.get();

        // H1: Split candidates into those with and without base_chance overrides.
        // Books with base_chance use their own chance exclusively (truly overrides global).
        // Books without base_chance use the global chance.
        List<LoreBookDefinition> withOverride = new ArrayList<>();
        List<LoreBookDefinition> withoutOverride = new ArrayList<>();
        for (LoreBookDefinition def : matching) {
            if (def.dropCondition().baseChance() != null) {
                withOverride.add(def);
            } else {
                withoutOverride.add(def);
            }
        }

        // Process books without base_chance override: roll global chance, then select
        if (!withoutOverride.isEmpty() && random.nextDouble() < globalChance) {
            List<LoreBookDefinition> selected = selectBooks(withoutOverride, random, maxBooks, useWeights);
            for (LoreBookDefinition def : selected) {
                addBookToLoot(def, player, generatedLoot, victim);
            }
        }

        // Process books with base_chance override: each book rolls its own chance independently
        for (LoreBookDefinition def : withOverride) {
            if (random.nextDouble() < def.dropCondition().baseChance()) {
                addBookToLoot(def, player, generatedLoot, victim);
            }
        }

        return generatedLoot;
    }

    /**
     * Validates per-player copy limits and adds a book to the loot list.
     * H2: Recording happens AFTER the stack is added to loot.
     */
    private void addBookToLoot(LoreBookDefinition def, Player player,
                               ObjectArrayList<ItemStack> generatedLoot, LivingEntity victim) {
        // Per-player copy limit check
        if (player != null && def.dropCondition().maxCopiesPerPlayer() >= 0) {
            if (!LoreBookRegistry.canPlayerReceive(player.getUUID(), def.id(),
                    def.dropCondition().maxCopiesPerPlayer())) {
                return;
            }
        }

        RpgLoreMod.LOGGER.debug("Dropping lore book '{}' from {}", def.id(),
                victim.getType().getDescriptionId());

        generatedLoot.add(LoreBookItem.createStack(def));

        // H2: Record AFTER successful addition to loot
        if (player != null && def.dropCondition().maxCopiesPerPlayer() >= 0) {
            LoreBookRegistry.recordPlayerReceived(player.getUUID(), def.id());
        }
    }

    // M7: Short-circuit when all candidates fit, regardless of useWeights
    private static List<LoreBookDefinition> selectBooks(
            List<LoreBookDefinition> candidates, RandomSource random, int max, boolean useWeights) {

        if (candidates.size() <= max) {
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
