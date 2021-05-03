package com.fasterxml.jackson.module.kotlin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// Wrapper to avoid costly calls using spread operator.
public class SpreadWrapper {
    public static <T> T newInstance(
            @NotNull Constructor<T> constructor, @NotNull Object[] args
    ) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        return constructor.newInstance(args);
    }

    // instance is null on static method
    public static Object invoke(
            @NotNull Method method, @Nullable Object instance, @NotNull Object[] args
    ) throws InvocationTargetException, IllegalAccessException {
        return method.invoke(instance, args);
    }
}
