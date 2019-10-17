package com.algorand.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class MD5 {
    private static MessageDigest digest;

    static {
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    static String md5Hash(String  json)
    {
        return Base64.getEncoder().encodeToString(digest.digest(json.getBytes()));
    }
}
