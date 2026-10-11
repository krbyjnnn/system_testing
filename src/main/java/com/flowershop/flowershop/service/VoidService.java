package com.flowershop.flowershop.service;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.UserRepository;
import com.flowershop.flowershop.util.Permissions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

// Handles voids at the POS. Staff cannot void on their own: a Manager has to
// enter their void passkey, and every void (and every refused attempt) goes to the audit trail.
@Service
public class VoidService {

    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final long LOCK_MILLIS = 60_000;
    private static final int ITERATIONS = 100_000;
    private static final Pattern PASSKEY_FORMAT = Pattern.compile("\\d{4,8}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Map<Long, Integer> failedAttempts = new HashMap<>();
    private final Map<Long, Long> lockedUntil = new HashMap<>();

    public VoidService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    // Checks the manager's passkey. Returns the approving user, or throws with a message that can be shown.
    public User authorize(User cashier, String passkey) {
        Permissions.require(cashier, Permissions.Feature.POS);
        checkNotLocked(cashier);

        if (passkey == null || passkey.isEmpty()) {
            throw new IllegalArgumentException("Enter the passkey of a Manager.");
        }

        for (User manager : userRepository.findAll()) {
            if (!Permissions.can(manager, Permissions.Feature.APPROVE_VOID)
                    || !"ACTIVE".equalsIgnoreCase(manager.getStatus())) {
                continue;
            }
            if (matches(passkey, manager.getVoidPasskey())) {
                clearFailures(cashier);
                return manager;
            }
        }

        boolean locked = registerFailure(cashier);
        auditService.log(cashier, "Void denied", "POS", "Wrong void passkey entered");
        if (locked) {
            auditService.log(cashier, "Void locked", "POS",
                    "Too many wrong passkeys, voiding locked for " + (LOCK_MILLIS / 1000) + " seconds");
            throw new IllegalArgumentException("Too many wrong attempts. Try again in "
                    + (LOCK_MILLIS / 1000) + " seconds.");
        }
        throw new IllegalArgumentException("Invalid passkey.");
    }

    @Transactional
    public void setPasskey(User owner, User manager, String passkey) {
        Permissions.require(owner, Permissions.Feature.USERS);
        if (manager == null || !Permissions.can(manager, Permissions.Feature.APPROVE_VOID)) {
            throw new IllegalArgumentException("Only a Manager can have a void passkey.");
        }
        if (passkey == null || !PASSKEY_FORMAT.matcher(passkey).matches()) {
            throw new IllegalArgumentException("The passkey must be 4 to 8 digits.");
        }

        for (User other : userRepository.findAll()) {
            if (other.getUserId().equals(manager.getUserId())) {
                continue;
            }
            if (matches(passkey, other.getVoidPasskey())) {
                throw new IllegalArgumentException("Another manager already uses that passkey.");
            }
        }

        User target = userRepository.findById(manager.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        target.setVoidPasskey(hash(passkey));
        userRepository.save(target);
        auditService.log(owner, "Void passkey set", "USERS", target.getUsername());
    }

    @Transactional
    public void logVoid(User cashier, User approver, String productName, int quantity, BigDecimal amount) {
        auditService.log(cashier, "Item voided", "POS",
                productName + " x" + quantity + ", amount ₱" + String.format("%,.2f", amount)
                        + ", cashier " + cashier.getUsername()
                        + ", approved by " + approver.getUsername() + " (" + approver.getRole() + ")");
    }

    private synchronized void checkNotLocked(User cashier) {
        Long until = lockedUntil.get(cashier.getUserId());
        if (until == null) {
            return;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining > 0) {
            throw new IllegalArgumentException("Voiding is locked. Try again in "
                    + ((remaining + 999) / 1000) + " seconds.");
        }
        lockedUntil.remove(cashier.getUserId());
        failedAttempts.remove(cashier.getUserId());
    }

    private synchronized boolean registerFailure(User cashier) {
        int count = failedAttempts.merge(cashier.getUserId(), 1, Integer::sum);
        if (count >= MAX_FAILED_ATTEMPTS) {
            lockedUntil.put(cashier.getUserId(), System.currentTimeMillis() + LOCK_MILLIS);
            failedAttempts.remove(cashier.getUserId());
            return true;
        }
        return false;
    }

    private synchronized void clearFailures(User cashier) {
        failedAttempts.remove(cashier.getUserId());
        lockedUntil.remove(cashier.getUserId());
    }

    private String hash(String passkey) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(derive(passkey, salt));
    }

    private boolean matches(String passkey, String stored) {
        if (stored == null || !stored.contains(":")) {
            return false;
        }
        String[] parts = stored.split(":", 2);
        try {
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            return MessageDigest.isEqual(expected, derive(passkey, salt));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] derive(String passkey, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(passkey.toCharArray(), salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Could not process the passkey.", e);
        }
    }
}