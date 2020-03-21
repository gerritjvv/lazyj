package org.gerritjvv.lazyj;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import com.google.common.base.Throwables;

/**
 * common java utility functions (lang for java.lang.* )<br/>
 */
public class LangUtils {
    public static <T> Set<T> append(Set<T> list, T obj) {
        list.add(obj);
        return list;
    }

    public static <T> List<T> append(List<T> list, T obj) {
        list.add(obj);
        return list;
    }

    /**
     * true if arr is null or length == 0
     */
    public static final <T> boolean nullOrEmpty(T[] arr) {
        return arr == null || arr.length == 0;
    }

    public static final <T> T futureGet(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
