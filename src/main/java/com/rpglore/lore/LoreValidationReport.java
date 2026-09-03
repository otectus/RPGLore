package com.rpglore.lore;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Result of parsing one lore book source: the definition when it loaded, plus every
 * diagnostic gathered along the way.
 */
public record LoreValidationReport(
        @Nullable LoreBookDefinition definition,
        List<LoreValidationMessage> messages
) {

    public boolean isLoaded() {
        return definition != null;
    }

    public boolean hasErrors() {
        return errorCount() > 0;
    }

    public int warningCount() {
        return (int) messages.stream()
                .filter(m -> m.severity() == LoreValidationMessage.Severity.WARNING).count();
    }

    public int errorCount() {
        return (int) messages.stream()
                .filter(m -> m.severity() == LoreValidationMessage.Severity.ERROR).count();
    }
}
