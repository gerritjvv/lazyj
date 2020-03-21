package org.gerritjvv.lazyj;

import java.util.concurrent.Callable;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

/**
 * Class representing a delayed operation.<br/>
 * Modeled after https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/Delay.java
 */
public class Delay<T> implements Callable<T> {

    private T val;
    private Throwable exception;
    private Callable<T> fn;

    public Delay(Callable<T> fn) {
        this.fn = Preconditions.checkNotNull(fn);
    }

    /**
     * True is deref was called at least once
     */
    public synchronized boolean isRealised() {
        return fn == null;
    }

    /**
     * Only calls fn once and then return its returned value every time get is called.<br/>
     */
    public synchronized T deref() {
        if (fn != null) {
            try {
                this.val = fn.call();
            } catch (Throwable t) {
                this.exception = t;
            }
            fn = null;
        }

        if (exception != null)
            throw Throwables.propagate(exception);

        return val;
    }

    @Override
    public T call() throws Exception {
        return deref();
    }

    public static final <T> Delay<T> create(Callable<T> fn) {
        return new Delay<>(fn);
    }
}
