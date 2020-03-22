package com.gerritjvv.lazyj.seq;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.gerritjvv.lazyj.Functional;
import com.gerritjvv.lazyj.Seq;

/**
 * Lazy sequence implementation.<br/>
 * Heavily inspired by clojure's clojure.lang.Cons.
 * <p/>
 * Do not use directly, please use @{link {@link Functional#lazySeq(Object, Supplier)}}
 */
public class Cons<T> extends AbstractSeqStream<T> {
    public static final Cons EMPTY = new Cons(null, null, null);

    private final T _first;
    private final Seq<T> _next;

    private Cons(T _first, Seq<T> _next, Runnable closeHandler) {
        super(closeHandler);
        this._first = _first;
        this._next = _next == null ? EMPTY : _next;
    }

    @Override
    public T first() {
        return _first;
    }

    @Override
    public Seq<T> next() {
        return _next;
    }


    @Override
    public Seq<T> take(long n) {
        return new TakeMergeSeq<>(closeHandler, this, n);
    }

    @Override
    public Seq<T> cons(T o) {
        return createNew(o, this);
    }

    @Override
    public Seq<T> empty() {
        return Cons.create(null, null, closeHandler);
    }

    @Override
    protected <R> Seq<R> createNew(Supplier<Seq<R>> fn) {
        return LazySeq.create(fn, closeHandler);
    }

    @Override
    protected <R> Seq<R> createNew(R v, Seq<R> seq) {
        return Cons.create(v, seq, closeHandler);
    }

    @Override
    public Seq<T> onClose(Runnable closeHandler) {
        return Cons.create(_first, _next, mergeOnClose(this.closeHandler, closeHandler));
    }

    /**
     * Override to provide a merged mapper for efficiency
     */
    @Override
    public <R> Seq<R> map(Function<? super T, ? extends R> mapper) {
        return new MapMergeSeq<>(closeHandler, mapper, this);
    }

    @Override
    public Seq<T> filter(Predicate<? super T> predicate) {
        return new FilterMergeSeq<>(closeHandler, null, this, predicate);
    }

    public static <T> Cons<T> create(T v, Seq<T> next, Runnable closeHandler) {
        return new Cons<>(v, next, closeHandler);
    }

    public static <T> Cons<T> create(T v, Seq<T> next) {
        return create(v, next, null);
    }


}
