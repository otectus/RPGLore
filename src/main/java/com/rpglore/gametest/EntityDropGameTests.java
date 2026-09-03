package com.rpglore.gametest;

import com.rpglore.RpgLoreMod;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.lore.DropCondition;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.acquisition.AcquisitionRule;
import com.rpglore.loot.LoreBookLootModifier;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Entity-drop conditions, driven through the real loot modifier and run headless by
 * {@code runGameTestServer}. Nothing here may touch a client class.
 *
 * <p>Every test kills a mob type of its own. The tests share one level and one book
 * registry, so a book restricted to a unique mob can never be picked up by a kill
 * another test is making in the same tick.
 *
 * <p>Everything runs against the default server config — globalDropChance 0.05,
 * maxBooksPerKill 1, onlyHostileMobs true, allowNonPlayerKills false — so each test
 * book carries an explicit {@code base_chance} (which overrides the global chance)
 * and every kill is credited to a mock player.
 */
@GameTestHolder(RpgLoreMod.MODID)
@PrefixGameTestTemplate(false)
public class EntityDropGameTests {

    private static final String TEMPLATE = "empty";

    // --- Mob filter ---

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void matchingMobTypeDrops(GameTestHelper helper) {
        String id = "rpg_lore:gametest_drop_zombie";
        Map<String, LoreBookDefinition> previous = installBooks(
                dropBook(id, condition(EntityType.ZOMBIE, null, null, null, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, 1.0, -1)));
        try {
            helper.assertTrue(countIn(runKill(helper, EntityType.ZOMBIE), id) == 1,
                    "a book naming this mob at base_chance 1.0 should have dropped");
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void nonMatchingMobTypeDoesNotDrop(GameTestHelper helper) {
        String id = "rpg_lore:gametest_drop_husk_only";
        Map<String, LoreBookDefinition> previous = installBooks(
                dropBook(id, condition(EntityType.HUSK, null, null, null, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, 1.0, -1)));
        try {
            helper.assertTrue(countIn(runKill(helper, EntityType.STRAY), id) == 0,
                    "a book restricted to another mob must not drop from this kill");
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
        helper.succeed();
    }

    // --- Place filters ---

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void biomeRestrictionMatchesOnlyTheBiomeAtTheKill(GameTestHelper helper) {
        String here = "rpg_lore:gametest_drop_biome_here";
        String elsewhere = "rpg_lore:gametest_drop_biome_elsewhere";

        LivingEntity victim = spawnVictim(helper, EntityType.CREEPER);
        ResourceLocation biome = biomeAt(helper, victim.blockPosition());
        ResourceLocation otherBiome = biome.equals(new ResourceLocation("minecraft:mushroom_fields"))
                ? new ResourceLocation("minecraft:ice_spikes")
                : new ResourceLocation("minecraft:mushroom_fields");

        Map<String, LoreBookDefinition> previous = installBooks(
                dropBook(here, condition(EntityType.CREEPER, List.of(biome), null, null, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, 1.0, -1)),
                dropBook(elsewhere, condition(EntityType.CREEPER, List.of(otherBiome), null, null, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, 1.0, -1)));
        try {
            ObjectArrayList<ItemStack> loot = runModifier(helper, victim);
            helper.assertTrue(countIn(loot, here) == 1,
                    "the book naming " + biome + " should have dropped there");
            helper.assertTrue(countIn(loot, elsewhere) == 0,
                    "the book naming " + otherBiome + " must not drop in " + biome);
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void dimensionRestrictionMatchesOnlyThisDimension(GameTestHelper helper) {
        String overworld = "rpg_lore:gametest_drop_overworld";
        String nether = "rpg_lore:gametest_drop_nether";

        Map<String, LoreBookDefinition> previous = installBooks(
                dropBook(overworld, condition(EntityType.SPIDER, null,
                        List.of(new ResourceLocation("minecraft:overworld")), null, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, 1.0, -1)),
                dropBook(nether, condition(EntityType.SPIDER, null,
                        List.of(new ResourceLocation("minecraft:the_nether")), null, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, 1.0, -1)));
        try {
            ObjectArrayList<ItemStack> loot = runKill(helper, EntityType.SPIDER);
            helper.assertTrue(countIn(loot, overworld) == 1,
                    "the overworld-only book should have dropped in the overworld");
            helper.assertTrue(countIn(loot, nether) == 0,
                    "the nether-only book must not drop in the overworld");
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void yRangeFiltersOnTheVictimHeight(GameTestHelper helper) {
        String inRange = "rpg_lore:gametest_drop_y_in_range";
        String tooHigh = "rpg_lore:gametest_drop_y_too_high";

        LivingEntity victim = spawnVictim(helper, EntityType.SILVERFISH);
        int y = (int) Math.floor(victim.getY());

        Map<String, LoreBookDefinition> previous = installBooks(
                dropBook(inRange, condition(EntityType.SILVERFISH, null, null, y - 5, y + 5,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, 1.0, -1)),
                dropBook(tooHigh, condition(EntityType.SILVERFISH, null, null, y + 10, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, 1.0, -1)));
        try {
            ObjectArrayList<ItemStack> loot = runModifier(helper, victim);
            helper.assertTrue(countIn(loot, inRange) == 1,
                    "a y range straddling the victim should have dropped its book");
            helper.assertTrue(countIn(loot, tooHigh) == 0,
                    "a min_y above the victim must not drop");
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
        helper.succeed();
    }

    // --- World state filters ---

    /** Own batch: this moves the level-wide day time. */
    @GameTest(template = TEMPLATE, timeoutTicks = 600, batch = "drop_time")
    public static void timeFilterFollowsTheLevelDayTime(GameTestHelper helper) {
        String dayBook = "rpg_lore:gametest_drop_day";
        String nightBook = "rpg_lore:gametest_drop_night";

        ServerLevel level = helper.getLevel();
        long previousTime = level.getDayTime();
        Map<String, LoreBookDefinition> previous = installBooks(
                dropBook(dayBook, condition(EntityType.SKELETON, null, null, null, null,
                        DropCondition.TimeFilter.DAY_ONLY, DropCondition.WeatherFilter.ANY, 1.0, -1)),
                dropBook(nightBook, condition(EntityType.SKELETON, null, null, null, null,
                        DropCondition.TimeFilter.NIGHT_ONLY, DropCondition.WeatherFilter.ANY, 1.0, -1)));

        level.setDayTime(1000L);
        // isDay() reads the sky darkening the level recomputes on tick, not the raw day time,
        // and rain darkens the sky enough to read as night. Clear the weather the previous
        // batch may have left behind and give the rain level the ~100 ticks it takes to fall.
        restoreClearWeather(level);
        helper.runAfterDelay(130, () -> {
            try {
                ObjectArrayList<ItemStack> byDay = runKill(helper, EntityType.SKELETON);
                helper.assertTrue(countIn(byDay, dayBook) == 1, "the day_only book should drop by day");
                helper.assertTrue(countIn(byDay, nightBook) == 0, "the night_only book must not drop by day");
            } catch (RuntimeException e) {
                level.setDayTime(previousTime);
                LoreBookRegistry.setBooks(previous);
                throw e;
            }

            level.setDayTime(18000L);
            helper.runAfterDelay(5, () -> {
                try {
                    ObjectArrayList<ItemStack> byNight = runKill(helper, EntityType.SKELETON);
                    helper.assertTrue(countIn(byNight, nightBook) == 1,
                            "the night_only book should drop by night");
                    helper.assertTrue(countIn(byNight, dayBook) == 0,
                            "the day_only book must not drop by night");
                    helper.succeed();
                } finally {
                    level.setDayTime(previousTime);
                    LoreBookRegistry.setBooks(previous);
                }
            });
        });
    }

    /**
     * Own batch: this moves the level-wide weather, and the rain and thunder levels
     * only ramp by 0.01 a tick, so the assertions sit behind long delays.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 600, batch = "drop_weather")
    public static void weatherFilterFollowsTheLevelWeather(GameTestHelper helper) {
        String clearBook = "rpg_lore:gametest_drop_clear";
        String thunderBook = "rpg_lore:gametest_drop_thunder";

        ServerLevel level = helper.getLevel();
        Map<String, LoreBookDefinition> previous = installBooks(
                dropBook(clearBook, condition(EntityType.CAVE_SPIDER, null, null, null, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.CLEAR_ONLY, 1.0, -1)),
                dropBook(thunderBook, condition(EntityType.CAVE_SPIDER, null, null, null, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.THUNDER_ONLY, 1.0, -1)));

        level.setWeatherParameters(6000, 0, false, false);
        helper.runAfterDelay(120, () -> {
            try {
                ObjectArrayList<ItemStack> clear = runKill(helper, EntityType.CAVE_SPIDER);
                helper.assertTrue(countIn(clear, clearBook) == 1,
                        "the clear_only book should drop in clear weather");
                helper.assertTrue(countIn(clear, thunderBook) == 0,
                        "the thunder_only book must not drop in clear weather");
            } catch (RuntimeException e) {
                restoreClearWeather(level);
                LoreBookRegistry.setBooks(previous);
                throw e;
            }

            level.setWeatherParameters(0, 6000, true, true);
            helper.runAfterDelay(150, () -> {
                try {
                    ObjectArrayList<ItemStack> storm = runKill(helper, EntityType.CAVE_SPIDER);
                    helper.assertTrue(countIn(storm, thunderBook) == 1,
                            "the thunder_only book should drop in a thunderstorm");
                    helper.assertTrue(countIn(storm, clearBook) == 0,
                            "the clear_only book must not drop in a thunderstorm");
                    helper.succeed();
                } finally {
                    restoreClearWeather(level);
                    LoreBookRegistry.setBooks(previous);
                }
            });
        });
    }

    // --- Chance and caps ---

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void zeroBaseChanceNeverDrops(GameTestHelper helper) {
        String id = "rpg_lore:gametest_drop_never";
        Map<String, LoreBookDefinition> previous = installBooks(
                dropBook(id, condition(EntityType.WITCH, null, null, null, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, 0.0, -1)));
        try {
            LivingEntity victim = spawnVictim(helper, EntityType.WITCH);
            for (int i = 0; i < 20; i++) {
                helper.assertTrue(countIn(runModifier(helper, victim), id) == 0,
                        "a base_chance of 0.0 must never drop");
            }
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void maxBooksPerKillCapsTheDrop(GameTestHelper helper) {
        String first = "rpg_lore:gametest_drop_cap_a";
        String second = "rpg_lore:gametest_drop_cap_b";

        Map<String, LoreBookDefinition> previous = installBooks(
                dropBook(first, condition(EntityType.DROWNED, null, null, null, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, 1.0, -1)),
                dropBook(second, condition(EntityType.DROWNED, null, null, null, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, 1.0, -1)));
        try {
            ObjectArrayList<ItemStack> loot = runKill(helper, EntityType.DROWNED);
            int dropped = countIn(loot, first) + countIn(loot, second);
            helper.assertTrue(dropped == 1,
                    "two certain books under the default maxBooksPerKill of 1 should yield one drop, got "
                            + dropped);
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void maxCopiesPerPlayerStopsTheSecondDrop(GameTestHelper helper) {
        String id = "rpg_lore:gametest_drop_one_copy";
        Map<String, LoreBookDefinition> previous = installBooks(
                dropBook(id, condition(EntityType.ZOMBIE_VILLAGER, null, null, null, null,
                        DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, 1.0, 1)));
        RpgLoreGameTests.requireTrackingData(helper);
        try {
            ServerPlayer killer = RpgLoreGameTests.mockPlayer(helper);
            LivingEntity victim = spawnVictim(helper, EntityType.ZOMBIE_VILLAGER);

            helper.assertTrue(countIn(runModifier(helper, victim, killer), id) == 1,
                    "the first kill should hand out the single allowed copy");
            helper.assertTrue(countIn(runModifier(helper, victim, killer), id) == 0,
                    "max_copies_per_player of 1 must block the second copy");
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
        helper.succeed();
    }

    // --- Harness ---

    /** Spawns the victim, credits a fresh mock player with the kill, and runs the modifier. */
    private static ObjectArrayList<ItemStack> runKill(GameTestHelper helper,
                                                      EntityType<? extends LivingEntity> type) {
        return runModifier(helper, spawnVictim(helper, type));
    }

    private static ObjectArrayList<ItemStack> runModifier(GameTestHelper helper, LivingEntity victim) {
        return runModifier(helper, victim, RpgLoreGameTests.mockPlayer(helper));
    }

    /**
     * Runs the production loot modifier over an empty loot list. {@code apply} is the
     * public entry point; with no conditions attached it goes straight to {@code doApply}.
     */
    private static ObjectArrayList<ItemStack> runModifier(GameTestHelper helper, LivingEntity victim,
                                                          ServerPlayer killer) {
        ObjectArrayList<ItemStack> loot = new ObjectArrayList<>();
        return new LoreBookLootModifier(new LootItemCondition[0])
                .apply(loot, lootContext(helper, victim, killer));
    }

    /** The entity loot context a player kill produces, minus the loot table itself. */
    private static LootContext lootContext(GameTestHelper helper, LivingEntity victim,
                                           ServerPlayer killer) {
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, victim.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, victim.damageSources().playerAttack(killer))
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .create(LootContextParamSets.ENTITY);
        return new LootContext.Builder(params).create(null);
    }

    /** A victim standing inside this test's own structure. */
    private static LivingEntity spawnVictim(GameTestHelper helper,
                                            EntityType<? extends LivingEntity> type) {
        return helper.spawn(type, new BlockPos(1, 2, 1));
    }

    private static ResourceLocation biomeAt(GameTestHelper helper, BlockPos pos) {
        Holder<Biome> holder = helper.getLevel().getBiome(pos);
        ResourceLocation id = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.BIOME).getKey(holder.value());
        helper.assertTrue(id != null, "the test position has no registered biome");
        return id;
    }

    /** Books stay under one id per test, so the count is exact. */
    private static int countIn(ObjectArrayList<ItemStack> loot, String bookId) {
        int count = 0;
        for (ItemStack stack : loot) {
            if (bookId.equals(RpgLoreGameTests.loreIdOf(stack))) count++;
        }
        return count;
    }

    /** A book whose only acquisition rule is this entity drop. */
    private static LoreBookDefinition dropBook(String id, DropCondition condition) {
        return RpgLoreGameTests.bookWithOnly(id, new AcquisitionRule.EntityDropAcquisition(condition));
    }

    /** The drop condition shape these tests vary, with player kills always required. */
    private static DropCondition condition(EntityType<?> mob,
                                           @Nullable List<ResourceLocation> biomes,
                                           @Nullable List<ResourceLocation> dimensions,
                                           @Nullable Integer minY,
                                           @Nullable Integer maxY,
                                           DropCondition.TimeFilter time,
                                           DropCondition.WeatherFilter weather,
                                           double baseChance,
                                           int maxCopiesPerPlayer) {
        return new DropCondition(
                List.of(ForgeRegistries.ENTITY_TYPES.getKey(mob)), null, biomes, null, dimensions,
                minY, maxY, time, weather, true, baseChance, maxCopiesPerPlayer);
    }

    /** Installs the test's books and returns the registry contents to restore. */
    private static Map<String, LoreBookDefinition> installBooks(LoreBookDefinition... books) {
        Map<String, LoreBookDefinition> previous = null;
        for (LoreBookDefinition book : books) {
            Map<String, LoreBookDefinition> before = RpgLoreGameTests.installBook(book);
            if (previous == null) previous = before;
        }
        return previous == null ? Map.of() : previous;
    }

    private static void restoreClearWeather(ServerLevel level) {
        level.setWeatherParameters(6000, 0, false, false);
    }

    private EntityDropGameTests() {}
}
