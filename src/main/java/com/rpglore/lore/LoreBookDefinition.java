package com.rpglore.lore;

import javax.annotation.Nullable;
import java.util.List;

public record LoreBookDefinition(
        String id,
        String title,
        String author,
        int generation,
        double weight,
        DropCondition dropCondition,
        List<String> pages,
        @Nullable String titleColor,
        @Nullable String authorColor,
        @Nullable String description,
        @Nullable String descriptionColor,
        boolean hideGeneration
) {}
