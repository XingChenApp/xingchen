package com.example.hikerview.ui.browser.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 照官 8.83 本体逐字转写（官源 smali_classes2/com/example/hikerview/ui/browser/util/CollectionUtil.smali，
 * 全类 8 法全落）。①团按需转写：PageParser.parsePageRule 用 isNotEmpty。
 */
public class CollectionUtil {

    @SafeVarargs
    public static <T> List<T> asList(T... array) {
        ArrayList<T> list = new ArrayList<>();
        Collections.addAll(list, array);
        return list;
    }

    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isEmpty(String[] array) {
        return array == null || array.length <= 0;
    }

    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    public static String listToString(List<String> list) {
        return listToString(list, "&&");
    }

    public static String listToString(List<String> list, String separator) {
        StringBuilder sb = new StringBuilder();
        if (list == null || list.size() <= 0) {
            return "";
        }
        if (list.size() <= 1) {
            return list.get(0);
        }
        sb.append(list.get(0));
        for (int i = 1; i < list.size(); i++) {
            sb.append(separator).append(list.get(i));
        }
        return sb.toString();
    }

    public static int[] toIntArray(List<Integer> list) {
        if (list == null) {
            return new int[0];
        }
        int[] array = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i).intValue();
        }
        return array;
    }

    public static String[] toStrArray(List<String> list) {
        if (list == null) {
            return new String[0];
        }
        String[] array = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
