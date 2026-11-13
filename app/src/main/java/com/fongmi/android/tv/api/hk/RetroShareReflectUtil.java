package com.fongmi.android.tv.api.hk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 环43（批C3复核订正）：8.83 RetroShareReflectUtil 转写——仅收
 * RetroLoadLibrary 实际用到的 findField/findMethod 两法（官 smali
 * RetroShareReflectUtil.smali .line 28-42 / .line 74-92 原形：沿类层级
 * 逐级 getDeclaredField/getDeclaredMethod，取到后不可见则 setAccessible，
 * 全层级未命中抛 NoSuchFieldException/NoSuchMethodException 并拼明字段/
 * 方法名与宿主类）。其余 expandFieldArray 等官法咱家无消费点，不转写。
 */
public final class RetroShareReflectUtil {

    private RetroShareReflectUtil() {
    }

    public static Field findField(Object instance, String name) throws NoSuchFieldException {
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                if (!field.isAccessible()) field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }

    public static Method findMethod(Object instance, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            try {
                Method method = clazz.getDeclaredMethod(name, parameterTypes);
                if (!method.isAccessible()) method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchMethodException("Method " + name + " with parameters "
                + Arrays.asList(parameterTypes) + " not found in " + instance.getClass());
    }
}
