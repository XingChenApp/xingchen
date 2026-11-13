package com.fongmi.android.tv.clash;

import java.security.MessageDigest;

/** 小工具：md5 十六进制。 */
public final class HashUtil {

    private HashUtil() {
    }

    public static String md5(String s) {
        if (s == null) s = "";
        try {
            MessageDigest d = MessageDigest.getInstance("MD5");
            byte[] b = d.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(32);
            for (byte x : b) {
                String h = Integer.toHexString(x & 0xFF);
                if (h.length() == 1) sb.append('0');
                sb.append(h);
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
