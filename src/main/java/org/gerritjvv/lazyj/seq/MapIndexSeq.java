package org.gerritjvv.lazyj.seq;

import java.util.function.Function;
import java.util.function.Supplier;

import org.gerritjvv.lazyj.Seq;

/**
 * Map operation that includes an index.
 * Supports an optional filter that is applied before the mapping and also merged map operations.
 */
public class MapIndexSeq<FROM, TO> extends AbstractSeqStream<TO> {

    private final ObjLongFunction<? super FROM, Boolean> filter;

    private final ObjLongFunction<? super FROM, ? extends TO> mapper;


    private final Seq<FROM> seq;

    private volatile TO _first;
    private volatile Seq<TO> _next;

    private final long index;

    public MapIndexSeq(Runnable closeHandler,
                       ObjLongFunction<? super FROM, Boolean> filter,
                       ObjLongFunction<? super FROM, ? extends TO> mapper,
                       Seq<FROM> seq,
                       long index) {
        super(closeHandler);
        this.filter = filter == null ? (v, i) -> true : filter;
        this.mapper = mapper;
        this.seq = seq;
        this.index = index;
    }


    private synchronized void eval() {
        if (_next == null) {
            FROM v;
            Seq<FROM> currentSeq = seq;
            long i = index;

            while ((v = currentSeq.first()) != null) {
                if (filter.accept(v, i))
                    break;

                i++;
                currentSeq = currentSeq.next();
            }

            if (v == null) {
                _first = null;
                _next = empty();
            } else {
                _first = mapper.accept(v, i);
                _next = new MapIndexSeq<>(closeHandler, filter, mapper, currentSeq.next(), i + 1);
            }
        }
    }

    @Override
    public <R> Seq<R> map(Function<? super TO, ? extends R> mapper) {
        return new MapIndexSeq<>(
                closeHandler,
                filter,
                (v, i) -> mapper.apply(this.mapper.accept(v, i)), //apply the current mapper with the index, then do the map operation
                seq,
                index);
    }

    @Override
    public TO first() {
        eval();
        return _first;
    }

    @Override
    public Seq<TO> next() {
        eval();
        return _next;
    }

    @Override
    public Seq<TO> take(long n) {
        return new TakeMergeSeq<>(closeHandler, this, n);
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
        return new MapIndexSeq<>(mergeOnClose(this.closeHandler, closeHandler), filter, mapper, seq, index);
    }
}
