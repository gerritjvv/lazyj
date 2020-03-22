package com.github.gerritjvv.lazyj.seq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.github.gerritjvv.lazyj.Seq;

/**
 *
 */
public abstract class AbstractSeq<T> implements Seq<T> {
    protected final Runnable closeHandler;


    public AbstractSeq(Runnable closeHandler) {
        this.closeHandler = closeHandler;
    }


    protected abstract <R> Seq<R> createNew(Supplier<Seq<R>> fn);

    protected abstract <R> Seq<R> createNew(R v, Seq<R> seq);

    public abstract <R> Seq<R> empty();

    /**
     * A basic map implementation, {@link MapMergeSeq} is used in LazySeq and Cons
     * to optimize multiple mappings.
     */
    @Override
    public <R> Seq<R> map(Function<? super T, ? extends R> mapper) {
        return createNew(() ->
        {
            T first = first();
            if (first == null)
                return empty();

            return createNew(mapper.apply(first), next().map(mapper));
        });
    }

    /**
     * A basic filter implementation, {@link FilterMergeSeq} is used in LazySeq and Cons
     * to optimize multiple filters.
     */
    @Override
    public Seq<T> filter(Predicate<? super T> predicate) {
        Seq<T> seqParent = this;

        return createNew(() ->
        {
            Seq<T> seq = seqParent;

            T first = null;

            while ((first = seq.first()) != null) {
                seq = seq.next();
                if (predicate.test(first))
                    break;
            }

            return seq == null ? Cons.create(first, null) : createNew(first, seq.filter(predicate));
        });
    }

    @Override
    public Seq<T> take(long n) {
        return createNew(() ->
        {
            T first = null;
            if (n == 0 || (first = first()) == null)
                return empty();

            return createNew(first, next().take(n - 1));
        });
    }

    @Override
    public Seq<T> drop(long n) {
        return createNew(() ->
        {

            long i = 0;
            Seq<T> seq = this;

            T first = null;

            while ((first = seq.first()) != null && i++ < n)
                seq = seq.next();

            return seq;
        });
    }

    @Override
    public Seq<T> concat(Seq<T> seq) {
        return createNew(() ->
        {
            T first = first();
            if (first == null)
                return seq;

            return createNew(first, next().concat(seq));
        });
    }

    @Override
    public <R> Seq<R> mapcat(Function<T, Seq<R>> mapper) {
        return mapcat(empty(), mapper, this);
    }

    @Override
    public <R> R reduce(R init, BiFunction<R, T, R> reducer) {
        R result = init;

        T first = null;
        Seq<T> seq = this;

        while ((first = seq.first()) != null) {
            result = reducer.apply(result, first);
            seq = seq.next();
        }

        return result;
    }

    @Override
    public long count() {
        Seq<T> seq = this;

        long count = 0;

        while ((seq.first()) != null) {
            count++;
            seq = seq.next();
        }

        return count;
    }

    @Override
    public Stream<T> stream() {
        return this;
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        T first = null;
        Seq<T> seq = this;

        while ((first = seq.first()) != null) {
            action.accept(first);
            seq = seq.next();
        }
    }

    @Override
    public Iterator<T> iterator() {
        Seq<T> seq = this;

        return new Iterator<T>() {

            Seq<T> itSeq = seq;
            T first;

            @Override
            public boolean hasNext() {
                return itSeq != null && (first = itSeq.first()) != null;
            }

            @Override
            public T next() {
                itSeq = itSeq.next();
                return first;
            }
        };
    }

    @Override
    public <R> Seq<R> seq(Iterable<? extends R> it) {
        if (it instanceof Seq)
            return ((Seq<R>) it);

        if (it instanceof Stream)
            return (Seq<R>) seq(it.iterator()).onClose(() -> ((Stream) it).close());

        return seq(it.iterator());
    }

    protected <R> Seq<R> seq(Iterator<? extends R> it) {
        return createNew(() -> {
            if (it.hasNext()) {
                try {
                    return createNew(it.next(), seq(it));
                } catch (NoSuchElementException e) {
                    return empty();
                }
            }
            // else
            return empty();

        });
    }


    @Override
    public List<T> toList() {
        T first = null;
        Seq<T> seq = this;
        List<T> list = new ArrayList<T>();

        while ((first = seq.first()) != null) {
            list.add(first);
            seq = seq.next();
        }

        return list;
    }

    @Override
    public Seq<T> sorted() {
        List list = toList();
        Collections.sort(list);

        return seq(list);
    }

    @Override
    public Seq<T> sorted(Comparator<? super T> comparator) {
        List list = toList();
        list.sort(comparator);

        return seq(list);
    }

    @Override
    public Seq<T> distinct() {
        return distinct(new HashSet<T>());
    }

    @Override
    public Seq<T> distinct(Set<T> seen) {
        return createNew(() ->
        {
            T first = null;
            Seq<T> seq = this;

            while ((first = seq.first()) != null && seen.contains(first))
                seq = seq.next();

            if (first == null)
                return empty();

            seen.add(first);

            return createNew(first, seq.distinct(seen));
        });
    }

    @Override
    public void close() {
        if (closeHandler != null)
            closeHandler.run();
    }


    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Seq)
            return equals((Seq) obj);

        return false;
    }

    @Override
    public boolean equals(Seq<? extends T> seq) {
        T a, b = null;
        Seq<T> seq1 = this;
        Seq<? extends T> seq2 = seq == null ? empty() : seq;

        while (true) {
            a = seq1.first();
            b = seq2.first();

            if (a == null || b == null)
                break;

            if (!a.equals(b))
                return false;

            seq1 = seq1.next();
            seq2 = seq2.next();
        }

        return a == null && b == null;
    }

    public static <T, R> Seq<R> mapcat(Seq<R> EMPTY, Function<T, Seq<R>> mapper, Seq<T> seq) {
        return LazySeq.create(() ->
        {

            T first = seq.first();
            if (first == null)
                return EMPTY;

            return mapper.apply(first).concat(seq.next().mapcat(mapper));
        });
    }

    /**
     * Implements the Stream onClose exception logic
     * @param a Runnable
     * @param b Runnable
     * @return a new runnable that will call a and b
     */
    protected Runnable mergeOnClose(Runnable a, Runnable b) {
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        } else if (a == b) {
            return a;
        } else {
            return () ->
            {
                try {
                    a.run();
                } catch (Throwable e1) {
                    try {
                        b.run();
                    } catch (Throwable e2) {
                        try {
                            e1.addSuppressed(e2);
                        } catch (Throwable ignore) {
                        }
                    }
                    throw e1;
                }
                b.run();
            };
        }
    }
}
