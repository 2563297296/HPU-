package cn.edu.hpu.powerwidget;

import java.nio.charset.StandardCharsets;

final class Obf {
    private static final byte[] K = {
            0x3A, 0x71, (byte) 0xC2, 0x4F, (byte) 0x8B, 0x1D, (byte) 0xE5, (byte) 0x96
    };

    private Obf() {}

    static String d(String hex) {
        int n = hex.length() / 2;
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            b[i] = (byte) ((hi << 4) | lo);
        }
        for (int i = 0; i < n; i++) b[i] ^= K[i % K.length];
        return new String(b, StandardCharsets.UTF_8);
    }
}
