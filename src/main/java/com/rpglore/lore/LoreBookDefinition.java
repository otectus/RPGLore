package com.rpglore.lore;

import com.rpglore.lore.acquisition.AcquisitionRule;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public record LoreBookDefinition(
        String id,
        String title,
        String author,
        int generation,
        double weight,
        List<AcquisitionRule> acquisition,
        List<String> pages,
        @Nullable String titleColor,
        @Nullable String authorColor,
        @Nullable String description,
        @Nullable String descriptionColor,
        boolean hideGeneration,
        boolean showGlint,
        @Nullable String category,
        boolean codexExclude,
        List<String> tags,
        @Nullable String discoveryHint,
        @Nullable String series,
        int seriesOrder,
        int formatVersion
) {

    public List<AcquisitionRule.EntityDropAcquisition> entityDropRules() {
        return rulesOf(AcquisitionRule.EntityDropAcquisition.class);
    }

    public List<AcquisitionRule.LootTableAcquisition> lootTableRules() {
        return rulesOf(AcquisitionRule.LootTableAcquisition.class);
    }

    public List<AcquisitionRule.AdvancementAcquisition> advancementRules() {
        return rulesOf(AcquisitionRule.AdvancementAcquisition.class);
    }

    private <T extends AcquisitionRule> List<T> rulesOf(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (AcquisitionRule rule : acquisition) {
            if (type.isInstance(rule)) out.add(type.cast(rule));
        }
        return out;
    }
}
