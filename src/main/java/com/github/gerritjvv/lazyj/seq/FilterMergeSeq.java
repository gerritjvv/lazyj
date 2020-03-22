package com.github.gerritjvv.lazyj.seq;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.github.gerritjvv.lazyj.Seq;

/**
 * Class that merges multiple filter operations.
 * Mappings are also supported, previous mappings are always applied before a filter is used.
 */
public class FilterMergeSeq<FROM, TO> extends AbstractSeqStream<TO> {

    private final Function<? super FROM, ? extends TO> mapper;
    private final Predicate<TO> filter;
    private final Seq<FROM> seq;

    private Supplier<Seq<TO>> search;

    private TO _first;
    private Seq<TO> _next;


    public FilterMergeSeq(
            Runnable closeHandler,
            Function<? super FROM, ? extends TO> mapper,
            Seq<FROM> seq,
            Predicate<? super TO> filter) {
        super(closeHandler);
        this.mapper = mapper == null ? (v) -> (TO) v : mapper;
        this.seq = seq;
        this.filter = (Predicate<TO>) filter;

        //function that will do the actual filtering and search
        search = () -> {

            Seq<FROM> currentSeq = seq;

            FROM first = null;
            TO toFirst = null;
            TO found = null;

            while ((first = currentSeq.first()) != null) {
                currentSeq = currentSeq.next();
                toFirst = this.mapper.apply(first);

                if (filter.test(toFirst)) {
                    found = toFirst;
                    break;
                }
            }

            if (found == null)
                return empty();
            else if (currentSeq == null)
                return createNew(found, null);
            else
                return createNew(found, new FilterMergeSeq<>(closeHandler, mapper, currentSeq, filter));
        };
    }

    @Override
    public Seq<TO> filter(Predicate<? super TO> predicate) {
        return new FilterMergeSeq<>(closeHandler, mapper, seq, this.filter.and(predicate));
    }

    @Override
    public Seq<TO> take(long n) {
        return new TakeMergeSeq<>(closeHandler, this, n);
    }

    @Override
    public TO first() {
        if (search != null) {
            synchronized (this) {
                if (search != null) {
                    Seq<TO> seq = search.get();
                    this._first = seq.first();
                    this._next = seq.next();
                    search = null;
                }
            }
        }

        return _first;
    }

    @Override
    public Seq<TO> next() {
        return _next;
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
        return new FilterMergeSeq<>(closeHandler, mapper, seq, filter);
    }
}
