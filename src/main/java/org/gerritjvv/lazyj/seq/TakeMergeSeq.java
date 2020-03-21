package org.gerritjvv.lazyj.seq;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.gerritjvv.lazyj.Seq;

/**
 * Class that merges multiple take operations into one.
 */
public class TakeMergeSeq<T> extends AbstractSeqStream<T> {
    private final long take;
    private final Seq<T> seq;

    private T _first;
    private Seq<T> _next;

    public TakeMergeSeq(
            Runnable closeHandler,
            Seq<T> seq,
            long take) {
        super(closeHandler);
        this.seq = seq;
        this.take = take;
    }


    private synchronized void eval() {
        if (_next == null) {
            if (take > 0) {
                _first = seq.first();
                _next = new TakeMergeSeq<>(closeHandler, seq.next(), take - 1);
            } else {
                _next = empty();
            }
        }

    }

    @Override
    public Seq<T> take(long n) {
        return new TakeMergeSeq<>(closeHandler, seq, Math.min(take, n));
    }

    @Override
    public Seq<T> filter(Predicate<? super T> predicate) {
        return new FilterMergeSeq<>(closeHandler, null, this, predicate);
    }

    @Override
    public <R> Seq<R> map(Function<? super T, ? extends R> mapper) {
        return new MapMergeSeq<>(closeHandler, mapper, this);
    }

    @Override
    public T first() {
        eval();
        return _first;
    }

    @Override
    public Seq<T> next() {
        eval();
        return _next;
    }

    @Override
    public Seq<T> cons(T o) {
        return Cons.create(o, this, closeHandler);
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
    public <R> Seq<R> empty() {
        return Cons.create(null, null, closeHandler);
    }

    @Override
    public Seq<T> onClose(Runnable closeHandler) {
        return new TakeMergeSeq<>(closeHandler, seq, take);
    }
}
