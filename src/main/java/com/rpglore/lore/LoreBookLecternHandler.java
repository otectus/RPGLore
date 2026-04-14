package com.rpglore.lore;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Allows LoreBookItem to be placed into empty vanilla lecterns.
 *
 * Vanilla LecternBlock#isBook is hardcoded to Items.WRITTEN_BOOK / WRITABLE_BOOK,
 * with no tag or Forge hook. We intercept the right-click interaction and
 * replicate the placement logic vanilla runs in LecternBlock.placeBook.
 *
 * Reading, dropping, and comparator output all work unchanged once the book
 * is stored, because LoreBookItem extends WrittenBookItem and writes standard
 * "pages" NBT — the lectern BE only stores an ItemStack and never revalidates.
 */
public final class LoreBookLecternHandler {

    private LoreBookLecternHandler() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof LoreBookItem)) return;

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
}
