package com.github.gerritjvv.lazyj.seq;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.github.gerritjvv.lazyj.Seq;

/**
 *
 */
public class LazySeq<T> extends AbstractSeqStream<T> {
    private Supplier<Seq<T>> fn;

    private Seq<T> seq;

    public LazySeq(Supplier<Seq<T>> fn, Runnable closeHandler) {
        super(closeHandler);
        this.fn = fn;
    }

    protected Seq<T> eval() {
        if (fn != null) {
            synchronized (this) {
                if (fn != null) {
                    this.seq = fn.get();
                    fn = null;
                }
            }
        }

        return this.seq;
    }


    private synchronized void _seq() {
        eval();
        if (this.seq != null) {
            Seq<T> v = this.seq;

            while (v instanceof LazySeq) {
                v = ((LazySeq) v).eval();
            }
            this.seq = LazySeq.create(v, closeHandler);
        }
    }

    @Override
    public T first() {
        _seq();
        return seq == null ? null : seq.first();
    }

    @Override
    public Seq<T> next() {
        _seq();
        return seq == null ? Cons.EMPTY : seq.next();
    }

    @Override
    public Seq<T> take(long n) {
        return new TakeMergeSeq<>(closeHandler, this, n);
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

    @Override
    protected <R> Seq<R> createNew(Supplier<Seq<R>> fn) {
        return new LazySeq<>(fn, closeHandler);
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
    public Seq<T> cons(T o) {
        return Cons.create(o, this, closeHandler);
    }

    @Override
    public Seq<T> onClose(Runnable closeHandler) {
        return new LazySeq<>(
                fn == null ? () -> seq : fn,
                mergeOnClose(this.closeHandler, closeHandler));
    }

    public static <R> LazySeq<R> create(Supplier<Seq<R>> fn) {
        return create(fn, null);
    }

    public static <R> LazySeq<R> create(Supplier<Seq<R>> fn, Runnable closeHandler) {
        return new LazySeq<>(fn, closeHandler);
    }

    public static <T> Seq<T> create(Seq<T> o, Runnable closeHandler) {
        if (o instanceof AbstractSeq && ((AbstractSeq) o).closeHandler == closeHandler)
            return o;
        else
            return o.onClose(closeHandler);
    }
}
