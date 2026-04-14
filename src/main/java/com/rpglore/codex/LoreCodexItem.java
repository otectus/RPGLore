package com.rpglore.codex;

import com.rpglore.compat.CuriosCompat;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.config.ServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public class LoreCodexItem extends Item {

    public LoreCodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Lore Codex").withStyle(
                Style.EMPTY.withBold(true).withColor(ChatFormatting.DARK_PURPLE));
    }

    /**
     * Attach Curios ICurioItem capability if the Curios mod is loaded.
     */
    @Nullable
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        if (CuriosCompat.isLoaded()) {
            return CuriosCompat.createCurioProvider(stack);
        }
        return null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!ServerConfig.CODEX_ENABLED.get()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("rpg_lore.codex.disabled"), false);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            CodexService service = CodexService.get();
            if (service != null) {
                service.resyncPlayer(serverPlayer);
            }
        }

        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> LoreCodexClientHelper.openCodexScreen());
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            int collected = tag.getInt("codex_collected_count");
            int total = tag.getInt("codex_total_count");
            tooltip.add(Component.translatable("rpg_lore.codex.tooltip.collected", collected, total)
                    .withStyle(ChatFormatting.GRAY));

            if (tag.getBoolean("codex_prevent_duplicates")) {
                tooltip.add(Component.translatable("rpg_lore.codex.tooltip.duplicates_blocked")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
            }
        }

        tooltip.add(Component.literal("----------").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("rpg_lore.codex.tooltip.description")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.AQUA));
        tooltip.add(Component.translatable("rpg_lore.codex.tooltip.hint")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.AQUA));
    }

    /**
     * Syncs the Codex item's cached NBT from server-side tracking data.
     */
    public static void syncItemNbt(ItemStack codexStack, CodexTrackingData data, java.util.UUID playerUuid) {
        CompoundTag tag = codexStack.getOrCreateTag();

        Set<String> collected = data.getCollectedBooks(playerUuid);
        Set<String> eligibleIds = LoreBookRegistry.getCodexEligibleIds();

        // Count only collected books that are still codex-eligible
        int collectedEligible = 0;
        for (String id : collected) {
            if (eligibleIds.contains(id)) collectedEligible++;
        }

        tag.putInt("codex_collected_count", collectedEligible);
        tag.putInt("codex_total_count", eligibleIds.size());
        tag.putBoolean("codex_prevent_duplicates", data.isPreventDuplicates(playerUuid));
    }

    /**
     * Finds the player's Codex in their inventory or Curios slots.
     */
    public static ItemStack findCodex(Player player) {
        // Check main inventory first
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof LoreCodexItem) {
                return stack;
            }
        }
        // Check Curios slots if loaded
        if (CuriosCompat.isLoaded()) {
            ItemStack curioCodex = CuriosCompat.findCodexInCurios(player);
            if (!curioCodex.isEmpty()) {
                return curioCodex;
            }
        }
        return ItemStack.EMPTY;
    }
}
