package com.rpglore.config;

import com.rpglore.lore.LoreValidationMessage;

import java.util.List;

/**
 * Outcome of one lore book reload: what changed in the catalog and every diagnostic
 * gathered while parsing.
 *
 * @param catastrophic true when the scan itself failed and the previous catalog was kept
 */
public record LoreReloadReport(
        int loaded,
        int added,
        int changed,
        int removed,
        int warnings,
        int errors,
        int overrides,
        boolean catastrophic,
        List<LoreValidationMessage> messages
) {

    public static LoreReloadReport catastrophic(int loaded, List<LoreValidationMessage> messages) {
        int warnings = (int) messages.stream()
                .filter(m -> m.severity() == LoreValidationMessage.Severity.WARNING).count();
        int errors = (int) messages.stream()
                .filter(m -> m.severity() == LoreValidationMessage.Severity.ERROR).count();
        return new LoreReloadReport(loaded, 0, 0, 0, warnings, errors, 0, true, messages);
    }
}
