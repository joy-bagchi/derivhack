package com.algorand.utils;

import java.util.Base64;
import java.util.UUID;

public class UniqueIdentifierGenerator {
    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static String randomAlphaNumeric(int count) {
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            int character = (int)(Math.random()*ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }

    public static String randomHash()
    {
        return Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes());
    }
}
