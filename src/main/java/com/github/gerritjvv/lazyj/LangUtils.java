package com.github.gerritjvv.lazyj;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import com.google.common.base.Throwables;

/**
 * common java utility functions (lang for java.lang.* )<br>
 */
public class LangUtils {
    /**
     * Add an object to Set
     * @param list The list to add to
     * @param obj The object to add to list
     * @param <T> type in Set
     * @return The set with obj added is returned
     */
    public static <T> Set<T> append(Set<T> list, T obj) {
        list.add(obj);
        return list;
    }

    /**
     * Add an object to List and return the list
     * @param list The list to add do
     * @param obj The object to add to the list
     * @param <T> The type of obj
     * @return the list with obj added
     */
    public static <T> List<T> append(List<T> list, T obj) {
        list.add(obj);
        return list;
    }

    /**
     * @param arr The array to test
     * @param <T> The type  of array
     * @return true if arr is null or length == 0
     */
    public static final <T> boolean nullOrEmpty(T[] arr) {
        return arr == null || arr.length == 0;
    }

    /**
     *
     * @param future The future to get the value from
     * @param <T> The type of the object returned if any
     * @return The value of the future.
     */
    public static final <T> T futureGet(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
