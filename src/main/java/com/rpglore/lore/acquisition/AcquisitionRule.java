package com.rpglore.lore.acquisition;

import com.rpglore.lore.DropCondition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One way a lore book can be acquired. A book carries a list of these; the rules are
 * additive, so the same book can drop from mobs, appear in chests and be granted by an
 * advancement at the same time.
 */
public sealed interface AcquisitionRule
        permits AcquisitionRule.EntityDropAcquisition,
                AcquisitionRule.LootTableAcquisition,
                AcquisitionRule.AdvancementAcquisition {

    /** How an advancement rule hands the book over. */
    enum Delivery { CODEX, INVENTORY }

    /** Mob-kill drop, filtered by the pre-2.2.0 drop condition shape. */
    record EntityDropAcquisition(DropCondition condition) implements AcquisitionRule {}

    /** Injection into named loot tables (chests, fishing, gameplay tables). */
    record LootTableAcquisition(List<ResourceLocation> lootTables, double chance, double weight)
            implements AcquisitionRule {}

    /** Grant on earning any of the listed advancements. */
    record AdvancementAcquisition(List<ResourceLocation> advancements, Delivery delivery)
            implements AcquisitionRule {}
}
