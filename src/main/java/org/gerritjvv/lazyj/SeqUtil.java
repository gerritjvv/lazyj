package org.gerritjvv.lazyj;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.gerritjvv.lazyj.seq.Cons;
import org.gerritjvv.lazyj.seq.LazySeq;

/**
 * Utility support functions common to all sequences.
 */
public class SeqUtil {
    private static final Seq EMPTY_SEQ = Cons.create(null, null);

    /**
     * Create a sequence from the iterable.
     * Streams and closeable iterables are attached using onClose.
     * @param it
     * @param <R>
     * @return
     */
    public static <R> Seq<R> seq(Iterable<? extends R> it) {
        if (it instanceof Seq)
            return ((Seq<R>) it);

        return seq(it.iterator(), asRunnableOrNull(it));
    }

    public static <R> Seq<R> seq(Iterator<? extends R> it) {
        return seq(it, asRunnableOrNull(it));
    }

    /**
     * Create a stream from the iterator and with the close handler attached.
     */
    public static <R> Seq<R> seq(Iterator<? extends R> it, Runnable closeHandler) {
        return LazySeq.create(() -> {
                    if (!it.hasNext()) {
                        return empty();
                    }

                    try {
                        return Cons.create(it.next(), seq(it, closeHandler));
                    } catch (NoSuchElementException e) {
                        return empty();
                    }
                }
                , closeHandler);
    }

    /**
     * Return a sequence from the array
     */
    public static <T> Seq<T> seq(T... it) {
        return seq(it, 0);
    }

    /**
     * Return a sequence from the array starting at the array index i.
     */
    public static <T> Seq<T> seq(T[] it, int i) {
        if (it == null)
            return SeqUtil.empty();

        return LazySeq.create(
                () -> i < it.length
                        ? Cons.create(it[i], seq(it, i + 1))
                        : empty());
    }

    /**
     * Return an empty sequence
     */
    public static <R> Seq<R> empty() {
        return EMPTY_SEQ;
    }

    /**
     * Converts a Stream into a lazy seq
     */
    public static <R> Seq<R> seq(Stream<? extends R> s) {
        if (s instanceof Seq)
            return ((Seq<R>) s);

        return seq(s.iterator(), () -> s.close());
    }


    /**
     * Converts a lazy seq into a java Stream
     */
    public static <T> Stream<T> stream(Seq<T> seq) {
        return seq;
    }

    /**
     * Return an iterator for a Seq.
     */
    public static <T> Iterator<T> iterator(Seq<T> seq) {
        return seq.iterator();
    }

    public static <T> Seq<T> treeSeq(Predicate<T> isBranch, Function<T, Seq<T>> children, T root) {
        return treeSeq(isBranch, children, root, null);
    }

    /**
     * Flatten out a tree structure as a lazy sequence
     */
    public static <T> Seq<T> treeSeq(Predicate<T> isBranch, Function<T, Seq<T>> children, T root, Runnable closeHandler) {
        if (root == null)
            return Cons.create(null, null, closeHandler);

        return LazySeq.create(
                () -> isBranch.test(root)
                        ? Cons.create(root, children.apply(root).mapcat(node -> treeSeq(isBranch, children, node, closeHandler)))
                        : Cons.create(root, Cons.create(null, null, closeHandler)),
                closeHandler);
    }

    private static Runnable asRunnableOrNull(Object obj) {
        if (obj instanceof Stream)
            return asRunnable((Stream) obj);
        else if (obj instanceof Closeable)
            return asRunnable((Closeable) obj);
        else
            return null;
    }

    private static Runnable asRunnable(Closeable closeable) {
        return () ->
        {
            try {
                closeable.close();
            } catch (IOException e) {
                //  the closeable  should handle exceptions, we cannot  do much more than print the error here.
                e.printStackTrace();
            }
        };
    }

    private static Runnable asRunnable(Stream s) {
        return () ->
        {
            s.close();
        };
    }
}
