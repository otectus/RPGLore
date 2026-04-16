package com.rpglore.lore;

import com.rpglore.codex.CodexService;
import com.rpglore.codex.LoreCodexClientHelper;
import com.rpglore.codex.LoreCodexItem;
import com.rpglore.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;

/**
 * Lectern integration for LoreBookItem and LoreCodexItem.
 *
 * <p>Vanilla LecternBlock#isBook is hardcoded to Items.WRITTEN_BOOK / WRITABLE_BOOK,
 * with no tag or Forge hook. We intercept the right-click interaction and
 * replicate the placement logic vanilla runs in LecternBlock.placeBook.
 *
 * <p>For LoreBookItem, reading / dropping / comparator output all work unchanged
 * once the book is stored, because it extends WrittenBookItem and writes standard
 * "pages" NBT — the lectern BE only stores an ItemStack and never revalidates.
 *
 * <p>LoreCodexItem extends Item directly and has no "pages" NBT, so right-clicking
 * a Codex-holding lectern would open vanilla's empty-book reader. The second
 * handler below intercepts that and opens the player's Codex GUI instead, matching
 * the in-hand use() behavior. The stored stack still drops unchanged when the
 * lectern is broken or the book is taken.
 */
public final class LoreBookLecternHandler {

    private LoreBookLecternHandler() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        if (!(item instanceof LoreBookItem) && !(item instanceof LoreCodexItem)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.LECTERN)) return;
        if (state.getValue(LecternBlock.HAS_BOOK)) return;

        Player player = event.getEntity();
        if (player.isSpectator()) return;

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof LecternBlockEntity lbe)) return;

            // Match vanilla LecternBlock.placeBook: always split 1 from the stack,
            // regardless of gamemode.
            lbe.setBook(stack.split(1));

            BlockState placed = state.setValue(LecternBlock.HAS_BOOK, Boolean.TRUE)
                    .setValue(LecternBlock.POWERED, Boolean.FALSE);
            level.setBlock(pos, placed, 3);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.updateNeighborsAt(pos.below(), placed.getBlock());
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }

    /**
     * When a lectern holds a LoreCodexItem, right-clicking it opens the player's
     * Codex GUI instead of vanilla's empty-book reader (the Codex has no "pages"
     * NBT). The displayed data is the interacting player's own synced collection,
     * not a snapshot of the placed stack — consistent with in-hand LoreCodexItem#use.
     */
    @SubscribeEvent
    public static void onLecternReadCodex(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.LECTERN)) return;
        if (!state.getValue(LecternBlock.HAS_BOOK)) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof LecternBlockEntity lbe)) return;
        if (!(lbe.getBook().getItem() instanceof LoreCodexItem)) return;

        Player player = event.getEntity();
        if (player.isSpectator()) return;

        if (!level.isClientSide) {
            if (!ServerConfig.CODEX_ENABLED.get()) {
                player.displayClientMessage(
                        Component.translatable("rpg_lore.codex.disabled"), false);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                CodexService service = CodexService.get();
                if (service != null) {
                    service.resyncPlayer(serverPlayer);
                }
            }
        } else {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> LoreCodexClientHelper.openCodexScreen());
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
