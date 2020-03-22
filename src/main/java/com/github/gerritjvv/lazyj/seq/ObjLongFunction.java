package com.github.gerritjvv.lazyj.seq;

import java.util.function.ObjLongConsumer;

/**
 * Improves on {@link ObjLongConsumer} by allowing to return a value.
 */
@FunctionalInterface
public interface ObjLongFunction<T, R> {
    R accept(T v, long i);
}