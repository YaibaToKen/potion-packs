package org.bitwisemadness.potionpacks.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PotionPacksAPI {

    private static final Set<String> REGISTERED_NAMESPACES = new LinkedHashSet<>();

    private PotionPacksAPI() {
    }

    public static void registerNamespace(String namespace) {
        if (namespace == null) {
            return;
        }

        String normalized = namespace.trim().toLowerCase();
        if (normalized.isBlank() || "potionpacks".equals(normalized)) {
            return;
        }

        REGISTERED_NAMESPACES.add(normalized);
    }

    public static Set<String> getRegisteredNamespaces() {
        return Collections.unmodifiableSet(REGISTERED_NAMESPACES);
    }
}
