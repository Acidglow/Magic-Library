package com.acidglow.magiclibrary.util;

public final class RomanNumeralUtil {
    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private RomanNumeralUtil() {}

    public static String toRoman(int value) {
        if (value <= 0) {
            return "0";
        }

        StringBuilder builder = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < VALUES.length; i++) {
            while (remaining >= VALUES[i]) {
                builder.append(SYMBOLS[i]);
                remaining -= VALUES[i];
            }
        }
        return builder.toString();
    }
}
