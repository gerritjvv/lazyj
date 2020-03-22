package com.github.gerritjvv.lazyj;

public class Exceptions {
    public static Throwable unwrap(Throwable t) {
        Throwable cause = t.getCause();

        if (cause == null) {
            return t;
        }

        return cause;
    }
}
