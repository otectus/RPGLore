package com.rpglore.codex;

import com.rpglore.config.LoreBookRegistry;
import com.rpglore.network.ModNetwork;
import com.rpglore.network.ClientboundCodexSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Server: send sync packet to client
            CodexTrackingData codexData = CodexTrackingData.getInstance();
            if (codexData != null) {
                ClientboundCodexSyncPacket packet = ClientboundCodexSyncPacket.create(
                        serverPlayer.getUUID(), codexData);
                ModNetwork.sendToPlayer(packet, serverPlayer);
            }
        }

        if (level.isClientSide) {
            // Client: open the Codex screen
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
            tooltip.add(Component.literal("Collected: " + collected + " / " + total)
                    .withStyle(ChatFormatting.GRAY));

            if (tag.getBoolean("codex_prevent_duplicates")) {
                tooltip.add(Component.literal("Blocking duplicate pickups")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
            }
        }
    }

    /**
     * Syncs the Codex item's cached NBT from server-side tracking data.
     */
    public static void syncItemNbt(ItemStack codexStack, CodexTrackingData data, java.util.UUID playerUuid) {
        CompoundTag tag = codexStack.getOrCreateTag();

        Set<String> collected = data.getCollectedBooks(playerUuid);
        ListTag collectedList = new ListTag();
        for (String id : collected) {
            collectedList.add(StringTag.valueOf(id));
        }
        tag.put("codex_collected", collectedList);
        tag.putInt("codex_collected_count", collected.size());
        tag.putInt("codex_total_count", LoreBookRegistry.getCodexEligibleCount());
        tag.putBoolean("codex_prevent_duplicates", data.isPreventDuplicates(playerUuid));
    }
}
