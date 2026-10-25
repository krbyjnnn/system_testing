package com.flowershop.flowershop.util;

import com.flowershop.flowershop.model.User;

import java.util.EnumSet;
import java.util.Set;

// Single place that decides who can do what:
//   OWNER   -> everything (dashboard, POS, inventory, suppliers, disputes, returns, users, audit, backups)
//   MANAGER -> Inventory Management (full), Manage Suppliers, approves voids
//   STAFF   -> Process Sales (POS), Inventory (view only)
// Owners and Managers can approve a void done at the POS (with the void passkey).
public final class Permissions {

    public enum Feature {
        REPORTS,         // dashboard and PDF reports
        POS,             // process sales
        INVENTORY_VIEW,  // see products, components and categories
        INVENTORY_EDIT,  // add / edit / make / restock inventory
        SUPPLIERS,
        DISPUTES,
        RETURNS,
        USERS,
        AUDIT,
        BACKUPS,         // create full and weekly backups
        APPROVE_VOID     // may enter their login to approve a void at the POS
    }

    private Permissions() {
    }

    private static Set<Feature> featuresOf(String role) {
        if (role == null) {
            return EnumSet.noneOf(Feature.class);
        }
        switch (role.trim().toUpperCase()) {
            case "OWNER":
                return EnumSet.of(Feature.REPORTS, Feature.POS, Feature.INVENTORY_VIEW, Feature.INVENTORY_EDIT,
                        Feature.SUPPLIERS, Feature.DISPUTES, Feature.RETURNS, Feature.USERS,
                        Feature.AUDIT, Feature.BACKUPS, Feature.APPROVE_VOID);
            case "MANAGER":
                return EnumSet.of(Feature.INVENTORY_VIEW, Feature.INVENTORY_EDIT, Feature.SUPPLIERS,
                        Feature.APPROVE_VOID);
            case "STAFF":
                return EnumSet.of(Feature.POS, Feature.INVENTORY_VIEW);
            default:
                return EnumSet.noneOf(Feature.class);
        }
    }

    public static boolean can(User user, Feature feature) {
        return user != null && featuresOf(user.getRole()).contains(feature);
    }

    // Used by the services so a rule is still enforced even if a screen is wrong
    public static void require(User user, Feature feature) {
        if (!can(user, feature)) {
            String who = user == null ? "Unknown user" : user.getRole();
            throw new SecurityException(who + " is not allowed to use " + feature);
        }
    }
}