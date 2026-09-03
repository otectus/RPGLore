package com.rpglore.gametest;

import com.mojang.authlib.GameProfile;
import com.rpglore.RpgLoreMod;
import com.rpglore.codex.CodexService;
import com.rpglore.codex.CodexTrackingData;
import com.rpglore.codex.LoreCodexItem;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.lore.DropCondition;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookParser;
import com.rpglore.lore.acquisition.AcquisitionRule;
import com.rpglore.data.LoreTrackingData;
import com.rpglore.registry.ModItems;
import com.rpglore.lore.acquisition.AcquisitionRule;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-side Codex behaviour, run headless by {@code runGameTestServer}.
 * Nothing here may touch a client class — that is half the point of the harness.
 *
 * <p>Each test installs a synthetic book in the registry, runs against a mock
 * player, and restores the previous registry contents afterwards.
 */
@GameTestHolder(RpgLoreMod.MODID)
@PrefixGameTestTemplate(false)
public class RpgLoreGameTests {

    private static final String TEST_BOOK_ID = "rpg_lore:gametest_book";
    // Per-test ids: every test shares one level, so nothing may key off a shared id.
    private static final String LOOT_TABLE_BOOK_ID = "rpg_lore:gametest_loot_table_book";
    private static final String CODEX_ADV_BOOK_ID = "rpg_lore:gametest_codex_advancement_book";
    private static final String INVENTORY_ADV_BOOK_ID = "rpg_lore:gametest_inventory_advancement_book";
    private static final String PRUNE_BOOK_ID = "rpg_lore:gametest_prune_book";
    private static final String EXCLUDE_BOOK_ID = "rpg_lore:gametest_exclude_book";
    private static final String RESET_BOOK_ID = "rpg_lore:gametest_reset_book";
    private static final String TEMPLATE = "empty";

    // --- Tests ---

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void firstCollectionIsUnreadAndStamped(GameTestHelper helper) {
        withTestBook(helper, (service) -> {
            ServerPlayer player = mockPlayer(helper);
            UUID uuid = player.getUUID();
            CodexTrackingData data = service.getTrackingData();

            helper.assertTrue(service.collectBook(player, TEST_BOOK_ID),
                    "first collection should report the book as newly added");
            helper.assertTrue(data.hasBook(uuid, TEST_BOOK_ID),
                    "book should be in the collected set");
            helper.assertTrue(!data.isRead(uuid, TEST_BOOK_ID),
                    "a freshly collected book must start unread");
            helper.assertTrue(data.getDiscoveredAt(uuid, TEST_BOOK_ID) > 0,
                    "discovery time should be stamped from the level game time");
            helper.assertTrue(data.getCopies(uuid, TEST_BOOK_ID) == 0,
                    "the master copy is not a spare");
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void duplicateBecomesSpare(GameTestHelper helper) {
        withTestBook(helper, (service) -> {
            ServerPlayer player = mockPlayer(helper);
            UUID uuid = player.getUUID();
            CodexTrackingData data = service.getTrackingData();

            service.collectBook(player, TEST_BOOK_ID);
            service.bankDuplicate(player, TEST_BOOK_ID);

            helper.assertTrue(data.getCopies(uuid, TEST_BOOK_ID) == 1,
                    "banking one duplicate should leave exactly one spare");
            helper.assertTrue(data.getCollectedCount(uuid) == 1,
                    "a duplicate must not add a second collected entry");
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void extractionWithoutSparesReportsNoCopies(GameTestHelper helper) {
        withTestBook(helper, (service) -> {
            ServerPlayer player = mockPlayer(helper);

            service.collectBook(player, TEST_BOOK_ID);

            CodexService.ExtractResult result = service.extractCopy(player, TEST_BOOK_ID);
            helper.assertTrue(result == CodexService.ExtractResult.NO_COPIES,
                    "extraction at zero spares should return NO_COPIES, got " + result);
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void favoriteAndReadRejectUncollected(GameTestHelper helper) {
        withTestBook(helper, (service) -> {
            ServerPlayer player = mockPlayer(helper);

            helper.assertTrue(!service.setFavorite(player, TEST_BOOK_ID, true),
                    "favoriting an uncollected book must be rejected");
            helper.assertTrue(!service.markRead(player, TEST_BOOK_ID),
                    "marking an uncollected book read must be rejected");
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void lootTableRuleInjectsIntoChest(GameTestHelper helper) {
        LoreBookDefinition book = bookWith(LOOT_TABLE_BOOK_ID, new AcquisitionRule.LootTableAcquisition(
                List.of(BuiltInLootTables.SIMPLE_DUNGEON), 1.0, 1.0));

        withBook(helper, book, (service) -> {
            ServerPlayer player = mockPlayer(helper);

            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, Blocks.CHEST);
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            helper.assertTrue(blockEntity instanceof ChestBlockEntity,
                    "expected a chest block entity, got " + blockEntity);

            ChestBlockEntity chest = (ChestBlockEntity) blockEntity;
            chest.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, 1234L);
            chest.unpackLootTable(player);

            boolean found = false;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (LOOT_TABLE_BOOK_ID.equals(loreIdOf(chest.getItem(i)))) {
                    found = true;
                    break;
                }
            }
            helper.assertTrue(found,
                    "a loot_table rule at chance 1.0 should have injected the book into the chest");
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void advancementCodexDeliveryCollectsOnce(GameTestHelper helper) {
        Advancement advancement = anyAdvancement(helper);
        LoreBookDefinition book = bookWith(CODEX_ADV_BOOK_ID, new AcquisitionRule.AdvancementAcquisition(
                List.of(advancement.getId()), AcquisitionRule.Delivery.CODEX));

        withBook(helper, book, (service) -> {
            ServerPlayer player = mockPlayer(helper);
            CodexTrackingData data = service.getTrackingData();

            MinecraftForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(player, advancement));
            helper.assertTrue(data.hasBook(player.getUUID(), CODEX_ADV_BOOK_ID),
                    "earning the advancement should have collected the book");

            // Re-earning must not bank a spare copy
            MinecraftForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(player, advancement));
            helper.assertTrue(data.getCopies(player.getUUID(), CODEX_ADV_BOOK_ID) == 0,
                    "a second grant must not add a spare copy");
            helper.assertTrue(data.getCollectedCount(player.getUUID()) == 1,
                    "a second grant must not add a second collected entry");
        });
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void advancementInventoryDeliveryDropsWhenFullAndOnlyOnce(GameTestHelper helper) {
        Advancement advancement = anyAdvancement(helper);
        LoreBookDefinition book = bookWith(INVENTORY_ADV_BOOK_ID, new AcquisitionRule.AdvancementAcquisition(
                List.of(advancement.getId()), AcquisitionRule.Delivery.INVENTORY));

        Map<String, LoreBookDefinition> previous = installBook(book);
        requireService(helper);
        requireTrackingData(helper);

        ServerPlayer player = mockPlayer(helper);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }

        MinecraftForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(player, advancement));

        // The dropped ItemEntity only becomes visible to an entity query once the level has
        // ticked it into its section storage, so both counts run from a delayed callback.
        helper.runAfterDelay(2, () -> {
            try {
                int firstDrop = takeDroppedBooks(helper, INVENTORY_ADV_BOOK_ID);
                helper.assertTrue(firstDrop == 1,
                        "a full inventory should have made the grant drop exactly one book, got "
                                + firstDrop);

                MinecraftForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(player, advancement));
            } catch (RuntimeException e) {
                LoreBookRegistry.setBooks(previous);
                throw e;
            }

            helper.runAfterDelay(2, () -> {
                try {
                    int secondDrop = takeDroppedBooks(helper, INVENTORY_ADV_BOOK_ID);
                    helper.assertTrue(secondDrop == 0,
                            "a second grant of the same advancement must not hand out another copy, got "
                                    + secondDrop);
                    helper.succeed();
                } finally {
                    LoreBookRegistry.setBooks(previous);
                }
            });
        });
    }

    // --- Reload pruning ---

    /**
     * Own batch: pruning walks every player's saved state, so it must not run while
     * another test sits between collecting a book and asserting it is there.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "prune_removed")
    public static void removedDefinitionIsPruned(GameTestHelper helper) {
        Map<String, LoreBookDefinition> previous = installBook(testBook(PRUNE_BOOK_ID));
        try {
            CodexService service = requireService(helper);
            ServerPlayer player = mockPlayer(helper);

            service.collectBook(player, PRUNE_BOOK_ID);
            helper.assertTrue(service.getTrackingData().hasBook(player.getUUID(), PRUNE_BOOK_ID),
                    "the book should be collected before the reload");

            // The reload drops the definition entirely
            LoreBookRegistry.setBooks(previous);
            service.pruneAndResync(helper.getLevel().getServer());

            helper.assertTrue(!service.getTrackingData().hasBook(player.getUUID(), PRUNE_BOOK_ID),
                    "a definition that no longer loads must be pruned from the collection");
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "prune_excluded")
    public static void codexExcludedDefinitionIsPruned(GameTestHelper helper) {
        LoreBookDefinition book = testBook(EXCLUDE_BOOK_ID);
        Map<String, LoreBookDefinition> previous = installBook(book);
        try {
            CodexService service = requireService(helper);
            ServerPlayer player = mockPlayer(helper);

            service.collectBook(player, EXCLUDE_BOOK_ID);
            helper.assertTrue(service.getTrackingData().hasBook(player.getUUID(), EXCLUDE_BOOK_ID),
                    "the book should be collected before the reload");

            // The reload keeps the definition but marks it codex_exclude
            installBook(codexExcluded(book));
            service.pruneAndResync(helper.getLevel().getServer());

            helper.assertTrue(!service.getTrackingData().hasBook(player.getUUID(), EXCLUDE_BOOK_ID),
                    "a definition that turned codex_exclude must be pruned from the collection");
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void resetPlayerClearsCollectionButKeepsGrantFlag(GameTestHelper helper) {
        Map<String, LoreBookDefinition> previous = installBook(testBook(RESET_BOOK_ID));
        try {
            CodexService service = requireService(helper);
            ServerPlayer player = mockPlayer(helper);
            UUID uuid = player.getUUID();
            CodexTrackingData data = service.getTrackingData();

            service.collectBook(player, RESET_BOOK_ID);
            service.markRead(player, RESET_BOOK_ID);
            service.setFavorite(player, RESET_BOOK_ID, true);
            service.bankDuplicate(player, RESET_BOOK_ID);
            data.markCodexGranted(uuid);

            service.resetPlayer(player);

            helper.assertTrue(data.getCollectedCount(uuid) == 0, "reset should clear collected books");
            helper.assertTrue(data.getCopies(uuid, RESET_BOOK_ID) == 0, "reset should clear spare copies");
            helper.assertTrue(!data.isRead(uuid, RESET_BOOK_ID), "reset should clear read marks");
            helper.assertTrue(!data.isFavorite(uuid, RESET_BOOK_ID), "reset should clear favorites");
            helper.assertTrue(data.getDiscoveredAt(uuid, RESET_BOOK_ID) == 0,
                    "reset should clear discovery stamps");
            helper.assertTrue(data.hasEverReceivedCodex(uuid),
                    "reset must not hand out a second initial Codex");
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
        helper.succeed();
    }

    // --- Granting ---

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void grantCodexDeliversOnceToAnEmptyInventory(GameTestHelper helper) {
        CodexService service = requireService(helper);
        ServerPlayer player = mockPlayer(helper);

        helper.assertTrue(service.grantCodex(player), "the first grant should be delivered");
        helper.assertTrue(countCodexes(player) == 1, "the player should be holding exactly one Codex");

        helper.assertTrue(!service.grantCodex(player), "a second grant must be refused");
        helper.assertTrue(countCodexes(player) == 1, "a second grant must not add another Codex");
        helper.succeed();
    }

    /** A player who already carries a Codex is only marked granted, never given a second. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void grantCodexSkipsAPlayerWhoAlreadyHasOne(GameTestHelper helper) {
        CodexService service = requireService(helper);
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().add(new ItemStack(ModItems.LORE_CODEX.get()));

        helper.assertTrue(!service.grantCodex(player),
                "granting to a player who already has a Codex should report no delivery");
        helper.assertTrue(countCodexes(player) == 1, "the player must still have exactly one Codex");
        helper.assertTrue(service.getTrackingData().hasEverReceivedCodex(player.getUUID()),
                "the grant flag should still be set so login never retries");
        helper.succeed();
    }

    /**
     * A full inventory falls back to the ground. The grant deliberately bypasses
     * {@code Player.drop}: that posts an ItemTossEvent, and the soul-bound guard would
     * cancel the toss and push the Codex back at the inventory that had no room for it.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void grantCodexDropsAtFeetWhenInventoryIsFull(GameTestHelper helper) {
        CodexService service = requireService(helper);
        ServerPlayer player = mockPlayer(helper);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }

        helper.assertTrue(service.grantCodex(player), "a full inventory should still count as delivered");
        helper.assertTrue(service.getTrackingData().hasEverReceivedCodex(player.getUUID()),
                "the grant flag should be set once the Codex is in the world");
        helper.assertTrue(countCodexes(player) == 0,
                "the full inventory must be left exactly as it was");

        // The ItemEntity is only visible to an entity query once the level has ticked it in.
        helper.runAfterDelay(2, () -> {
            int dropped = takeDroppedCodexes(helper);
            helper.assertTrue(dropped == 1,
                    "a full inventory should have dropped exactly one Codex at the player's feet, got "
                            + dropped);

            // Already flagged as granted, so a second call is refused before it builds anything
            helper.assertTrue(!service.grantCodex(player), "a second grant must be refused");
            helper.runAfterDelay(2, () -> {
                int again = takeDroppedCodexes(helper);
                helper.assertTrue(again == 0, "a refused grant must not drop anything, got " + again);
                helper.assertTrue(countCodexes(player) == 0,
                        "and must not touch the inventory either");
                helper.succeed();
            });
        });
    }

    // --- Death and respawn ---

    /**
     * Own batch: this flips the level-wide keepInventory rule, which the other
     * death-handling test reads.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "death_drop")
    public static void soulboundCodexSurvivesDeathAndClone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID uuid = UUID.randomUUID();
        boolean previousKeep = level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, level.getServer());

        try {
            CodexTrackingData data = requireService(helper).getTrackingData();
            ServerPlayer dying = mockPlayer(helper, uuid);
            dying.getInventory().add(new ItemStack(ModItems.LORE_CODEX.get()));

            DamageSource source = dying.damageSources().generic();
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(dying, source));

            helper.assertTrue(data.hasStashedCodex(uuid), "death should have stashed the Codex");
            helper.assertTrue(countCodexes(dying) == 0,
                    "the stashed Codex must be gone from the dying player's inventory");

            // Safety net: a Codex that still reached the drops list is taken back out of it
            List<ItemEntity> drops = new ArrayList<>();
            Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
            drops.add(new ItemEntity(level, at.x, at.y, at.z,
                    new ItemStack(ModItems.LORE_CODEX.get())));
            MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(dying, source, drops, 0, true));
            helper.assertTrue(drops.isEmpty(),
                    "the safety net should have pulled the Codex out of the drops");

            ServerPlayer respawned = mockPlayer(helper, uuid);
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, dying, true));

            helper.assertTrue(countCodexes(respawned) == 1,
                    "the respawned player should hold exactly one Codex, got " + countCodexes(respawned));
            helper.assertTrue(!data.hasStashedCodex(uuid), "the stash should be empty after the clone");
        } finally {
            level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(previousKeep, level.getServer());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "death_keep")
    public static void keepInventoryLeavesTheCodexInPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID uuid = UUID.randomUUID();
        boolean previousKeep = level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, level.getServer());

        try {
            CodexTrackingData data = requireService(helper).getTrackingData();
            ServerPlayer dying = mockPlayer(helper, uuid);
            dying.getInventory().add(new ItemStack(ModItems.LORE_CODEX.get()));

            MinecraftForge.EVENT_BUS.post(
                    new LivingDeathEvent(dying, dying.damageSources().generic()));

            helper.assertTrue(!data.hasStashedCodex(uuid),
                    "with keepInventory the Codex is carried over already — stashing would duplicate it");
            helper.assertTrue(countCodexes(dying) == 1,
                    "with keepInventory the handler must leave the inventory alone");
        } finally {
            level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(previousKeep, level.getServer());
        }
        helper.succeed();
    }

    // --- Harness ---

    /**
     * Installs the synthetic test book, runs the body against an initialized
     * {@link CodexService}, then restores the registry contents.
     */
    private static void withTestBook(GameTestHelper helper, Consumer<CodexService> body) {
        Map<String, LoreBookDefinition> previous = new HashMap<>();
        for (LoreBookDefinition def : LoreBookRegistry.getAllBooks()) {
            previous.put(def.id(), def);
        }

        Map<String, LoreBookDefinition> withTest = new HashMap<>(previous);
        withTest.put(TEST_BOOK_ID, testBook());
        LoreBookRegistry.setBooks(withTest);

        try {
            body.accept(requireService(helper));
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
    }

    /** Installs one specific book (replacing the default synthetic one) for the body. */
    private static void withBook(GameTestHelper helper, LoreBookDefinition book,
                                 Consumer<CodexService> body) {
        Map<String, LoreBookDefinition> previous = installBook(book);
        try {
            body.accept(requireService(helper));
        } finally {
            LoreBookRegistry.setBooks(previous);
        }
    }

    /**
     * Installs a book and returns the previous registry contents. Tests that assert from a
     * delayed callback need this split: the book has to stay installed past the point where
     * the test method itself returns.
     */
    static Map<String, LoreBookDefinition> installBook(LoreBookDefinition book) {
        Map<String, LoreBookDefinition> previous = new HashMap<>();
        for (LoreBookDefinition def : LoreBookRegistry.getAllBooks()) {
            previous.put(def.id(), def);
        }

        Map<String, LoreBookDefinition> withTest = new HashMap<>(previous);
        withTest.put(book.id(), book);
        LoreBookRegistry.setBooks(withTest);
        return previous;
    }

    /** The synthetic book under a test-specific id, plus one extra acquisition rule. */
    static LoreBookDefinition bookWith(String id, AcquisitionRule extra) {
        LoreBookDefinition base = testBook(id);
        List<AcquisitionRule> rules = new java.util.ArrayList<>(base.acquisition());
        rules.add(extra);
        return withRules(base, List.copyOf(rules), base.codexExclude());
    }

    /** The synthetic book under a test-specific id whose only acquisition rule is {@code only}. */
    static LoreBookDefinition bookWithOnly(String id, AcquisitionRule only) {
        return withRules(testBook(id), List.of(only), false);
    }

    /** The same book with {@code codex_exclude} flipped on, to drive the exclusion prune. */
    static LoreBookDefinition codexExcluded(LoreBookDefinition base) {
        return withRules(base, base.acquisition(), true);
    }

    /** Copies a definition with a different rule list and codex-exclusion flag. */
    private static LoreBookDefinition withRules(LoreBookDefinition base, List<AcquisitionRule> rules,
                                                boolean codexExclude) {
        return new LoreBookDefinition(base.id(), base.title(), base.author(), base.generation(),
                base.weight(), rules, base.pages(), base.titleColor(), base.authorColor(),
                base.description(), base.descriptionColor(), base.hideGeneration(), base.showGlint(),
                base.category(), codexExclude, base.tags(), base.discoveryHint(),
                base.series(), base.seriesOrder(), base.formatVersion());
    }

    /** A real advancement to drive the event with; story/root when it is loaded. */
    private static Advancement anyAdvancement(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Advancement advancement = server.getAdvancements()
                .getAdvancement(new ResourceLocation("minecraft:story/root"));
        if (advancement == null) {
            advancement = server.getAdvancements().getAllAdvancements().stream().findFirst().orElse(null);
        }
        helper.assertTrue(advancement != null, "the gametest server loaded no advancements at all");
        return advancement;
    }

    @Nullable
    static String loreIdOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("lore_id", Tag.TAG_STRING)) return null;
        return tag.getString("lore_id");
    }

    /**
     * Counts lore books of one id lying on the ground inside this test's structure, and
     * discards them so a later count in the same test only ever sees new drops.
     */
    private static int takeDroppedBooks(GameTestHelper helper, String bookId) {
        int count = 0;
        for (ItemEntity entity : helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, testBounds(helper))) {
            if (bookId.equals(loreIdOf(entity.getItem()))) {
                count++;
                entity.discard();
            }
        }
        return count;
    }

    /** Codexes carried in the player's inventory (Curios is not loaded in this run). */
    private static int countCodexes(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof LoreCodexItem) count++;
        }
        return count;
    }

    /** Counts and discards Codexes lying on the ground inside this test's structure. */
    private static int takeDroppedCodexes(GameTestHelper helper) {
        int count = 0;
        for (ItemEntity entity : helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, testBounds(helper))) {
            if (entity.getItem().getItem() instanceof LoreCodexItem) {
                count++;
                entity.discard();
            }
        }
        return count;
    }

    /** The per-player copy tracking the inventory-delivery cap relies on. */
    static void requireTrackingData(GameTestHelper helper) {
        LoreTrackingData data = LoreBookRegistry.getTrackingData();
        if (data == null) {
            LoreBookRegistry.setTrackingData(
                    LoreTrackingData.getOrCreate(helper.getLevel().getServer().overworld()));
        }
    }

    /**
     * A server player that is never placed in the player list.
     *
     * <p>{@code GameTestHelper.makeMockServerPlayerInLevel()} cannot be used here:
     * it routes through {@code PlayerList.placeNewPlayer}, whose Forge patch calls
     * {@code NetworkHooks.sendMCRegistryPackets} and dereferences the netty channel
     * of the mock Connection, which is null. Constructing the player directly gives
     * the same server-side state without touching the network layer.
     */
    static ServerPlayer mockPlayer(GameTestHelper helper) {
        return mockPlayer(helper, UUID.randomUUID());
    }

    /** As above, under a caller-chosen UUID — the death/respawn pair must share one. */
    static ServerPlayer mockPlayer(GameTestHelper helper, UUID uuid) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(
                server,
                helper.getLevel(),
                new GameProfile(uuid, "rpg-lore-gametest"));
        // Vanilla inventory code (placeItemBackInInventory, for one) sends slot packets
        // straight through player.connection. A Connection with no netty channel reports
        // itself disconnected, so those packets queue and are never written.
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        // Tests share one level, so a player left at the level spawn sits in the same spot as
        // every other test's player, possibly outside any loaded chunk. Move it into this
        // test's own structure: anything it drops is then inside testBounds(helper).
        Vec3 centre = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(centre.x, centre.y, centre.z, 0.0F, 0.0F);
        return player;
    }

    /** The test structure's absolute bounds, padded for items that skitter off a block edge. */
    static AABB testBounds(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockPos max = helper.absolutePos(new BlockPos(3, 3, 3));
        return new AABB(min, max).inflate(2.0);
    }

    /** The gametest server may not have run the normal startup path; init on demand. */
    static CodexService requireService(GameTestHelper helper) {
        CodexService service = CodexService.get();
        if (service != null) return service;

        MinecraftServer server = helper.getLevel().getServer();
        CodexTrackingData data = CodexTrackingData.getOrCreate(server.overworld());
        CodexTrackingData.setInstance(data);
        CodexService.init(data);
        return CodexService.get();
    }

    private static LoreBookDefinition testBook() {
        return testBook(TEST_BOOK_ID);
    }

    static LoreBookDefinition testBook(String id) {
        return new LoreBookDefinition(
                id,
                "GameTest Book",
                "GameTest",
                0,
                1.0,
                List.of(new AcquisitionRule.EntityDropAcquisition(DropCondition.defaultCondition())),
                List.of("A single page."),
                null,
                null,
                null,
                null,
                false,
                true,
                null,
                false,
                List.of(),
                null,
                null,
                0,
                LoreBookParser.SUPPORTED_FORMAT_VERSION
        );
    }

    private RpgLoreGameTests() {}
}
