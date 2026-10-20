package com.localshare.security;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/* JADX INFO: loaded from: classes4.dex */
public final class PasswordHasher {
    private static final int HASH_LEN = 32;
    private static final int ITERATIONS = 210000;
    private static final SecureRandom RND = new SecureRandom();
    private static final int SALT_LEN = 16;

    public static byte[] generateSalt() {
        byte[] s = new byte[16];
        RND.nextBytes(s);
        return s;
    }

    public static byte[] hash(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] h = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return h;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String encode(byte[] salt, byte[] hash) {
        return Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verify(char[] password, String stored) {
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 2) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            byte[] actual = hash(password, salt);
            boolean eq = constantTimeEquals(expected, actual);
            Arrays.fill(actual, (byte) 0);
            return eq;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
