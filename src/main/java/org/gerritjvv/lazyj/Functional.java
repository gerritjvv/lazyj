package org.gerritjvv.lazyj;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;

import org.gerritjvv.lazyj.seq.Cons;
import org.gerritjvv.lazyj.seq.LazySeq;
import org.gerritjvv.lazyj.seq.MapIndexSeq;
import org.gerritjvv.lazyj.seq.ObjLongFunction;

/**
 * Implement functional features that java 1.8 doesn't
 */
public class Functional {
    public static final Runnable NOOP_CALLBACK = () -> {
    };

    public static <T> Optional<T> emptyOnError(Supplier<T> s, Consumer<Throwable> onError) {
        try {
            return Optional.ofNullable(s.get());
        } catch (Throwable t) {
            onError.accept(t);
        }

        return Optional.empty();
    }

    public static <T> void forEach(T[] arr, ConsumerCanThrow<T> consumer) {
        try {

            for (int i = 0; i < arr.length; i++) {
                consumer.accept(arr[i]);
            }
        } catch (Throwable t) {
            throw Throwables.propagate(t);
        }
    }

    public static final <K, V, STATE> STATE reduceKV(Map<K, V> m, STATE init, KVReducerFN<STATE, K, V> fn) {
        STATE state = init;

        for (Map.Entry<K, V> entry : m.entrySet())
            state = fn.apply(state, entry.getKey(), entry.getValue());

        return state;
    }

    public static <T, R> R reduce(Iterable<T> it, R init_state, BiFunction<R, T, R> reducer) {
        if (it == null)
            return init_state;

        return reduce(it.iterator(), init_state, reducer);
    }

    public static <T, R> R reduce(Stream<T> stream, R init_state, BiFunction<R, T, R> reducer) {
        if (stream == null)
            return init_state;

        return reduce(stream.iterator(), init_state, reducer);
    }


    /**
     * A more functional reduce than streams, the function is called with (fn state item): state
     */
    public static <T, R> R reduce(Iterator<T> it, R init_state, BiFunction<R, T, R> reducer) {
        if (it == null)
            return init_state;

        R state = init_state;

        while (it.hasNext())
            state = reducer.apply(state, it.next());

        return state;
    }

    /**
     * Return an infinite lazy sequence of values supplied by calling fn each time.
     */
    public static <T> Seq<T> repeatedly(Supplier<T> fn) {
        return lazySeq(() -> Cons.create(fn.get(), repeatedly(fn)));
    }

    /**
     * Return a lazy sequence from a function that produces lazy sequences
     */
    public static <T> Seq<T> lazySeq(Supplier<Seq<T>> fn) {
        return LazySeq.create(fn);
    }

    /**
     * Return a lazy sequence from a function that produces lazy sequences, adding o as the head of the sequence
     */
    public static <T> Seq<T> lazySeq(T o, Supplier<Seq<T>> fn) {
        return Cons.create(o, LazySeq.create(fn));
    }

    /**
     * Return a lazy sequence from a function that produces lazy sequences, adding o as the head of the sequence
     */
    public static <T> Seq<T> lazySeq(T o) {
        return Cons.create(o, null);
    }

    /**
     * Create a lazy sequence of two items
     */
    public static <T> Seq<T> lazySeq(T o1, T o2) {
        return Cons.create(o1, Cons.create(o2, null));
    }

    public static <T> Seq<T> lazySeqEmpty() {
        return Cons.create(null, null);
    }

    /**
     * Memoize a function using the google guava cache provided.
     */
    public static <T, R> Function<T, R> memoize(Cache<T, R> cache, Function<T, R> f) {
        return x ->
        {
            try {
                return cache.get(x, () -> f.apply(x));
            } catch (ExecutionException e) {
                throw Throwables.propagate(e);
            }
        };
    }

    /**
     * Memoize a function using the cache provided.
     */
    public static <T, R> Function<T, R> memoize(Map<T, R> cache, Function<T, R> f) {
        return x -> cache.computeIfAbsent(x, f);
    }

    /**
     * Return a predicate function that will filter null objects
     */
    public static <T> Predicate<T> notNull() {
        return x -> x != null;
    }

    /**
     * Call consumer with each item in the collection and its corresponding index
     */
    public static <T> void forEachIndexed(Collection<T> coll, ObjIntConsumer<T> consumer) {
        int i = 0;
        for (T v : coll)
            consumer.accept(v, i++);
    }

    public static <T> Seq<T> filter(Predicate<T> fn, Seq<T> seq) {
        return seq.filter(fn);
    }

    /**
     * True if any of the items in coll matches the predicate, otherwise false.
     * Note if coll is empty false is returned.
     */
    public static <T> Optional<T> find(Collection<T> coll, Predicate<T> p) {
        return find(coll, Function.identity(), p);
    }

    /**
     * Apply transform first then test and return the transformed value.
     */
    public static <T, R> Optional<R> find(Collection<T> coll, Function<T, R> transform, Predicate<R> p) {
        for (T item : coll) {
            R itemT = transform.apply(item);
            if (p.test(itemT))
                return Optional.ofNullable(itemT);
        }

        return Optional.empty();
    }

    public static <T> boolean any(T[] arr, Predicate<T> p) {
        return any(Arrays.asList(arr), p);
    }

    /**
     * True if any of the items in coll matches the predicate, otherwise false.
     * Note if coll is empty false is returned.
     */
    public static <T> boolean any(Collection<T> coll, Predicate<T> p) {
        for (T item : coll) {
            if (p.test(item))
                return true;
        }

        return false;
    }

    /**
     * Call fn len times passing in the index value i, the return value is ignored.
     */
    public static void doTimes(int len, IntConsumer fn) {
        for (int i = 0; i < len; i++)
            fn.accept(i);
    }

    /**
     * Call fn len times passing in the index value i, the return value is ignored.
     */
    public static void doTimes(int len, Runnable fn) {
        for (int i = 0; i < len; i++)
            fn.run();
    }

    public static <T> Seq<T> take(int n, Seq<T> s) {
        return s.take(n);
    }

    public static <T, R> Seq<R> mapIndexed(Seq<T> seq, ObjLongFunction<T, Boolean> filter, ObjLongFunction<T, R> mapper) {
        return mapIndexed(seq, filter, mapper, 0L);
    }

    public static <T, R> Seq<R> mapIndexed(Seq<T> seq, ObjLongFunction<T, Boolean> filter, ObjLongFunction<T, R> mapper, long start) {
        return new MapIndexSeq<>(null, filter, mapper, seq, start);
    }

    public static <T, R> R[] map(T[] t, Function<T, R> fn) {
        R[] r = (R[]) new Object[t.length];

        for (int i = 0; i < t.length; i++) {
            r[i] = fn.apply(t[i]);
        }

        return r;
    }

    public static <K, V> Map<K, V> filterByKey(Map<K, V> map, Predicate<K> predicate) {
        return map.entrySet().stream()
                .filter((e) -> predicate.test(e.getKey()))
                .collect(entriesToMapCollector());
    }

    public static <K, V> Map<K, V> filterByValue(Map<K, V> map, Predicate<V> predicate) {
        return map.entrySet().stream()
                .filter((e) -> predicate.test(e.getValue()))
                .collect(entriesToMapCollector());
    }

    public static Stream<?> flatten(Object... objs) {
        return ((objs.length == 1 && objs[0] instanceof Stream) ? (Stream) objs[0] : Stream.of(objs))
                .flatMap(s ->
                {
                    if (s instanceof Stream)
                        return flatten((Stream) s);
                    else if (s instanceof Object[])
                        return flatten((Object[]) s);
                    else if (s instanceof Collection)
                        return flatten(((Collection) s).stream());
                    else if (s instanceof Iterator)
                        return flatten(StreamSupport.stream(Spliterators.spliteratorUnknownSize((Iterator) s, Spliterator.ORDERED), false));
                    else if (s instanceof Iterable)
                        return flatten(StreamSupport.stream(((Iterable) s).spliterator(), false));
                    else
                        return Stream.of(s);
                });
    }

    public static <K, V> Collector<Map.Entry<K, V>, ?, Map<K, V>> entriesToMapCollector() {
        return Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public interface ConsumerCanThrow<T> {
        void accept(T t) throws Throwable;
    }

    @FunctionalInterface
    public interface KVReducerFN<STATE, K, V> {
        public STATE apply(STATE state, K k, V v);
    }
}
