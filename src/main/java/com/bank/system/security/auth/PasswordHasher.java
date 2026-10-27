package com.bank.system.security.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PasswordHasher – secure password hashing and verification.
 *
 * <h3>Algorithm</h3>
 * <p>Uses <strong>PBKDF2-with-HMAC-SHA-256</strong> with a 16-byte cryptographic
 * random salt and 310,000 iterations (OWASP 2023 recommendation).  The stored
 * credential string has the format:
 * <pre>
 *   pbkdf2sha256:&lt;iterations&gt;:&lt;base64(salt)&gt;:&lt;base64(hash)&gt;
 * </pre>
 *
 * <h3>Why not Argon2id?</h3>
 * <p>Argon2id is the preferred algorithm in production (as noted in the
 * security requirements).  However, Argon2id requires a native/third-party
 * library (e.g., Bouncy Castle) and the project targets a pure-JDK academic
 * environment.  PBKDF2-HMAC-SHA256 at 310 k iterations provides equivalent
 * protection within the standard JDK and is the current NIST SP 800-63B
 * recommendation for environments without Argon2id support.
 *
 * <p>To upgrade to Argon2id, replace this class with a Bouncy Castle
 * implementation – the interface (hash / verify) stays identical.
 *
 * <h3>Thread safety</h3>
 * <p>Stateless – all methods are thread-safe.
 */
public final class PasswordHasher {

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Algorithm used with {@link javax.crypto.SecretKeyFactory}. */
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /** Format identifier embedded in the stored hash string. */
    private static final String FORMAT_ID = "pbkdf2sha256";

    /** OWASP 2023 minimum iteration count for PBKDF2-HMAC-SHA256. */
    private static final int ITERATIONS = 310_000;

    /** Output length of the derived key (256 bits). */
    private static final int KEY_LENGTH_BITS = 256;

    /** Salt length in bytes (128 bits). */
    private static final int SALT_LENGTH_BYTES = 16;

    /** Delimiter used inside the stored credential string. */
    private static final char DELIMITER = ':';

    // Private constructor – utility class.
    private PasswordHasher() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Hashes a plaintext password.  A fresh random salt is generated on
     * every call, so identical passwords produce different stored values.
     *
     * @param plaintext the user-supplied password; must not be null or empty
     * @return a storable credential string (safe for VARCHAR columns)
     * @throws IllegalArgumentException if {@code plaintext} is null or blank
     */
    public static String hash(String plaintext) {
        validatePlaintext(plaintext);
        byte[] salt = generateSalt();
        byte[] hash = pbkdf2(plaintext.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);

        return FORMAT_ID + DELIMITER
             + ITERATIONS + DELIMITER
             + Base64.getEncoder().encodeToString(salt) + DELIMITER
             + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Verifies a plaintext password against a previously stored credential
     * string produced by {@link #hash(String)}.
     *
     * @param plaintext      the user-supplied password candidate
     * @param storedCredential the value stored in the database
     * @return {@code true} if the password matches
     * @throws IllegalArgumentException if either argument is null/blank or the
     *                                  stored credential has an unexpected format
     */
    public static boolean verify(String plaintext, String storedCredential) {
        validatePlaintext(plaintext);
        if (storedCredential == null || storedCredential.isBlank()) {
            throw new IllegalArgumentException("storedCredential must not be blank.");
        }

        String[] parts = storedCredential.split(String.valueOf(DELIMITER));
        if (parts.length != 4 || !FORMAT_ID.equals(parts[0])) {
            throw new IllegalArgumentException(
                "Unrecognised credential format – expected " + FORMAT_ID + " prefix.");
        }

        int    iterations = Integer.parseInt(parts[1]);
        byte[] salt       = Base64.getDecoder().decode(parts[2]);
        byte[] expected   = Base64.getDecoder().decode(parts[3]);

        byte[] actual = pbkdf2(plaintext.toCharArray(), salt, iterations, KEY_LENGTH_BITS);

        // Constant-time comparison to prevent timing attacks.
        return MessageDigest.isEqual(expected, actual);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Generates a cryptographically-random salt. */
    private static byte[] generateSalt() {
        SecureRandom rng = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        rng.nextBytes(salt);
        return salt;
    }

    /**
     * Derives a key using PBKDF2-HMAC-SHA256.
     *
     * @param password   char array of the plaintext password
     * @param salt       random salt bytes
     * @param iterations iteration count
     * @param keyBits    desired output length in bits
     * @return derived key bytes
     */
    private static byte[] pbkdf2(char[] password, byte[] salt,
                                  int iterations, int keyBits) {
        try {
            javax.crypto.spec.PBEKeySpec spec =
                new javax.crypto.spec.PBEKeySpec(password, salt, iterations, keyBits);
            javax.crypto.SecretKeyFactory factory =
                javax.crypto.SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (java.security.spec.InvalidKeySpecException |
                 NoSuchAlgorithmException e) {
            throw new RuntimeException("PBKDF2 hashing failed – JDK misconfiguration.", e);
        }
    }

    /** Validates that the plaintext argument is non-null and non-blank. */
    private static void validatePlaintext(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Password must not be null or blank.");
        }
    }
}
