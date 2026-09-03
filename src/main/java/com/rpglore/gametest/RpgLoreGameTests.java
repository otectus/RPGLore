package com.rpglore.gametest;

import com.mojang.authlib.GameProfile;
import com.rpglore.RpgLoreMod;
import com.rpglore.codex.CodexService;
import com.rpglore.codex.CodexTrackingData;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.lore.DropCondition;
import com.rpglore.lore.LoreBookDefinition;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
        return new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "rpg-lore-gametest"));
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
        return new LoreBookDefinition(
                TEST_BOOK_ID,
                "GameTest Book",
                "GameTest",
                0,
                1.0,
                DropCondition.defaultCondition(),
                List.of("A single page."),
                null,
                null,
                null,
                null,
                false,
                true,
                null,
                false
        );
    }

    private RpgLoreGameTests() {}
}
