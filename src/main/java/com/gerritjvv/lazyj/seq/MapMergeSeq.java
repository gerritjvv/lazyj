package com.gerritjvv.lazyj.seq;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.gerritjvv.lazyj.Seq;

/**
 * Class that merges multiple mappings operations
 */
public class MapMergeSeq<FROM, TO> extends AbstractSeqStream<TO> {

    private final Function<? super FROM, ? extends TO> mapper;
    private final Seq<FROM> seq;

    private volatile TO first;

    public MapMergeSeq(Runnable closeHandler, Function<? super FROM, ? extends TO> mapper, Seq<FROM> seq) {
        super(closeHandler);
        this.mapper = mapper;
        this.seq = seq;
    }

    @Override
    public <R> Seq<R> map(Function<? super TO, ? extends R> mapper) {
        return new MapMergeSeq<>(closeHandler, mapper.compose(this.mapper), seq);
    }

    @Override
    public Seq<TO> filter(Predicate<? super TO> predicate) {
        return new FilterMergeSeq<>(closeHandler, mapper, seq, predicate);
    }

    @Override
    public TO first() {
        //memoise the mapper apply so that subsequent calls don't call mapper
        if (first == null) {
            synchronized (this) {
                if (first == null) {
                    FROM from = seq.first();

                    if (from == null)
                        return null;

                    TO to = mapper.apply(from);
                    this.first = to;

                    return to;
                }
            }
        }

        return first;
    }

    @Override
    public Seq<TO> take(long n) {
        return new TakeMergeSeq<>(closeHandler, this, n);
    }

    @Override
    public Seq<TO> next() {
        return new MapMergeSeq<>(closeHandler, mapper, seq.next());
    }

    @Override
    public Seq<TO> cons(TO o) {
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
    public Seq<TO> onClose(Runnable closeHandler) {
        return new MapMergeSeq<>(mergeOnClose(this.closeHandler, closeHandler), mapper, seq);
    }
}
