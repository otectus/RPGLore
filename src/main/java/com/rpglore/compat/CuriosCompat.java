package com.rpglore.compat;

import com.rpglore.RpgLoreMod;
import com.rpglore.codex.LoreCodexItem;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Isolated Curios API calls. This class is ONLY loaded when Curios is confirmed present.
 * All callers must check {@link #isLoaded()} before calling any method.
 */
public final class CuriosCompat {

    public static boolean isLoaded() {
        return ModList.get().isLoaded("curios");
    }

    /**
     * Creates an ICapabilityProvider that attaches ICurio capability to the Codex item.
     */
    public static ICapabilityProvider createCurioProvider(ItemStack stack) {
        return new ICapabilityProvider() {
            private final LazyOptional<ICurio> curio = LazyOptional.of(() -> new ICurio() {
                @Override
                public ItemStack getStack() {
                    return stack;
                }
            });

            @Nonnull
            @Override
            public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
                return CuriosCapability.ITEM.orEmpty(cap, curio);
            }
        };
    }

    /**
     * Finds a LoreCodexItem in the player's Curios slots.
     * @return The Codex ItemStack, or ItemStack.EMPTY if not found
     */
    public static ItemStack findCodexInCurios(Player player) {
        try {
            AtomicReference<ItemStack> result = new AtomicReference<>(ItemStack.EMPTY);
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                handler.findFirstCurio(stack -> stack.getItem() instanceof LoreCodexItem)
                        .ifPresent(slotResult -> result.set(slotResult.stack()));
            });
            return result.get();
        } catch (NoClassDefFoundError e) {
            RpgLoreMod.LOGGER.warn("Curios API unavailable despite being detected, disabling Curios integration");
            return ItemStack.EMPTY;
        }
    }

    /**
     * Removes a LoreCodexItem from the player's Curios slots, remembering which slot it came from.
     * @return The removed stack plus its slot identifier/index, or null if no Codex was equipped
     */
    @Nullable
    public static ExtractedCurio extractCodexFromCurios(Player player) {
        try {
            AtomicReference<ExtractedCurio> result = new AtomicReference<>();
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                handler.findFirstCurio(stack -> stack.getItem() instanceof LoreCodexItem)
                        .ifPresent(slotResult -> {
                            SlotContext ctx = slotResult.slotContext();
                            ItemStack removed = slotResult.stack().copy();
                            handler.setEquippedCurio(ctx.identifier(), ctx.index(), ItemStack.EMPTY);
                            result.set(new ExtractedCurio(removed, ctx.identifier(), ctx.index()));
                        });
            });
            return result.get();
        } catch (NoClassDefFoundError e) {
            RpgLoreMod.LOGGER.warn("Curios API unavailable despite being detected, disabling Curios integration");
            return null;
        }
    }

    /**
     * Puts a Codex back into a Curios slot, preferring the index it was taken from
     * and falling back to any free slot of the same type.
     * @return true if the stack was placed
     */
    public static boolean equipCodexInCurios(Player player, ItemStack stack, String slotId, int preferredIndex) {
        try {
            return CuriosApi.getCuriosInventory(player).resolve()
                    .flatMap(handler -> handler.getStacksHandler(slotId))
                    .map(stacksHandler -> {
                        IDynamicStackHandler stacks = stacksHandler.getStacks();
                        if (preferredIndex >= 0 && preferredIndex < stacks.getSlots()
                                && stacks.getStackInSlot(preferredIndex).isEmpty()) {
                            stacks.setStackInSlot(preferredIndex, stack);
                            return true;
                        }
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            if (stacks.getStackInSlot(i).isEmpty()) {
                                stacks.setStackInSlot(i, stack);
                                return true;
                            }
                        }
                        return false;
                    })
                    .orElse(false);
        } catch (NoClassDefFoundError e) {
            RpgLoreMod.LOGGER.warn("Curios API unavailable despite being detected, disabling Curios integration");
            return false;
        }
    }

    private CuriosCompat() {}

    /** A Codex removed from a Curios slot, with the slot it should go back into. */
    public record ExtractedCurio(ItemStack stack, String slotId, int index) {}
}
