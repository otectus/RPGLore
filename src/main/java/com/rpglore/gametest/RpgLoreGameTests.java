package com.rpglore.gametest;

import com.mojang.authlib.GameProfile;
import com.rpglore.RpgLoreMod;
import com.rpglore.codex.CodexService;
import com.rpglore.codex.CodexTrackingData;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.lore.DropCondition;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookParser;
import com.rpglore.lore.acquisition.AcquisitionRule;
import com.rpglore.data.LoreTrackingData;
import com.rpglore.lore.acquisition.AcquisitionRule;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
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
    private static Map<String, LoreBookDefinition> installBook(LoreBookDefinition book) {
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
    private static LoreBookDefinition bookWith(String id, AcquisitionRule extra) {
        LoreBookDefinition base = testBook(id);
        List<AcquisitionRule> rules = new java.util.ArrayList<>(base.acquisition());
        rules.add(extra);
        return new LoreBookDefinition(base.id(), base.title(), base.author(), base.generation(),
                base.weight(), List.copyOf(rules), base.pages(), base.titleColor(), base.authorColor(),
                base.description(), base.descriptionColor(), base.hideGeneration(), base.showGlint(),
                base.category(), base.codexExclude(), base.tags(), base.discoveryHint(),
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
    private static String loreIdOf(ItemStack stack) {
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

    /** The per-player copy tracking the inventory-delivery cap relies on. */
    private static void requireTrackingData(GameTestHelper helper) {
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
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "rpg-lore-gametest"));
        // Tests share one level, so a player left at the level spawn sits in the same spot as
        // every other test's player, possibly outside any loaded chunk. Move it into this
        // test's own structure: anything it drops is then inside testBounds(helper).
        Vec3 centre = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.moveTo(centre.x, centre.y, centre.z, 0.0F, 0.0F);
        return player;
    }

    /** The test structure's absolute bounds, padded for items that skitter off a block edge. */
    private static AABB testBounds(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(0, 0, 0));
        BlockPos max = helper.absolutePos(new BlockPos(3, 3, 3));
        return new AABB(min, max).inflate(2.0);
    }

    /** The gametest server may not have run the normal startup path; init on demand. */
    private static CodexService requireService(GameTestHelper helper) {
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

    private static LoreBookDefinition testBook(String id) {
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
