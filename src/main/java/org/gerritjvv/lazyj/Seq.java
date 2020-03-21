package org.gerritjvv.lazyj;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import com.google.common.base.Functions;
import com.google.common.base.Throwables;

/**
 * Usage:<br/>
 * <p/>
 * <pre>
 *       private static Seq<Integer> lazyNumbers(int i)
 *       {
 *          if (i > 0)
 *              return Functional.lazySeq(i, () -> lazyNumbers(i - 1));
 *          else
 *              return Functional.lazySeqEmpty();
 *       }
 *
 * </pre>
 *
 * Recursive programming:<br/>
 * <p/>
 * Sequences, especially LazySequence(s) allow to program recursively without blowing the stack.<br/>
 * This is done by encapsulating each next call into a closure instance. Only called when the next<br/>
 * item is iterated on.
 * <p/>
 * Merged Operations:<br/>
 * <p/>
 * Consecutive maps are merged into one sequence instance and then same is done for filters and take operations.<br/>
 * A filter operation can merge in map operations but not the other way around.
 * <p/>
 * Close Handler: <br/>
 *  <p/>
 *   Streams are different from Sequences in that Streams can only be used once and then closed and thrown away<br/>
 *   Sequences are immutable and in general has no notion of being closed or open. We support the close handler<br/>
 *   mechanism for Streams to allow bottom down IO operations, but when used sequences should be semantically used as<br/>
 *   Streams, i.e when closed not used again.
 * <p/>
 *
 * Null values:<br/>
 * <p/>
 * Null values cannot be stored in sequences, because null signals an end of sequence.<br/>
 * For null values use Optional.
 */
public interface Seq<T> extends Stream<T>, Iterable<T>
{

    /**
     * Returns the current head of the sequences (head, tail).<br/>
     * This method will cause the head to be evaluated.<br/>
     * Null means empty sequence.
     */
    @Nullable
    T first();

    /**
     * If first == null is checked, next will never return null.<br/>
     * Returns the tail of the sequence, without evaluating anything.
     */
    Seq<T> next();

    /**
     * Prepends an item to the front of the sequence.<br/>
     * [2,3,4].cons(1) ==> [1,2,3,4]
     */
    Seq<T> cons(T o);

    /**
     * Returns a stream from the sequence, Sequences implement the Stream interface already, so this method
     * in general is redundant.
     */
    Stream<T> stream();

    /**
     * Apppend the seq to the current sequences.<br/>
     * [1,2].concat([3,4]) ==> [1,2,3,4]
     */
    Seq<T> concat(Seq<T> seq);

    /**
     * Mapcat is map and concat combined, and flattens out applying a map function that returnes sequences.
     * duplicate n =  [n, n]
     * <p/>
     * [1,2,3].mapcat( duplicate ) ==> [1, 1, 2, 2, 3, 3]
     */
    <R> Seq<R> mapcat(Function<T, Seq<R>> mapper);

    /**
     * Remove n items from the sequence.<br/>
     * [1,2,3,4].drop(2) ==> [3,4]
     */
    Seq<T> drop(long n);

    /**
     * Take n items from the sequence and drop the rest.<br/>
     * [1,2,3,4].take(2) ==> [1,2]
     */
    Seq<T> take(long n);

    /**
     * Only return items for which the filter returns true, or till the end of the sequence have been reached.
     *
     * [1,2,3,4].filter( even ) ==> [2,4]
     */
    Seq<T> filter(Predicate<? super T> predicate);

    void forEach(Consumer<? super T> action);

    /**
     * Return a sequence with mapper applied to the sequence.<br/>
     */
    <R> Seq<R> map(Function<? super T, ? extends R> mapper);

    /**
     * Eager non lazy operation. Do not use with inifite streams.
     * <br/>
     * [1,2,3,4].reduce(0, (a, b) -> a + b) ==> 10
     */
    <R> R reduce(R init, BiFunction<R, T, R> reducer);

    /**
     * Eager non lazy operation.
     */
    long count();

    /**
     * Eager operation that creates an intermediate list/array for sorting
     */
    Seq<T> sorted();

    /**
     * Eager operation that creates an intermediate list/array for sorting
     */
    Seq<T> sorted(Comparator<? super T> comparator);


    /**
     * Eager operation that creates an intermediate list
     */
    List<T> toList();

    /**
     * Return a sequence of the current type from the iterable, all on-close functions are passed onto the new sequence.
     */
    <R> Seq<R> seq(Iterable<? extends R> it);


    /**
     * Lazy but uses a cache of items seen of size == unique items in sequence, use with caution, as
     * this can cause a memory leak if the sequence is infinite and have an infinite number of elements.
     */
    Seq<T> distinct();

    /**
     * Lazy but uses a cache of items seen of size == unique items in sequence, use with caution, as
     * this can cause a memory leak if the sequence is infinite and have an infinite number of elements.
     */
    Seq<T> distinct(Set<T> seen);

    /**
     * Return a new sequence where all the current on-close functions will be merged with the close-handler and all handlers will be
     * called once the close function is called on the sequence.
     * <p/>
     * Note that the close handler can be called more than once.
     */
    @Override
    Seq<T> onClose(Runnable closeHandler);

    default Spliterator<T> spliterator()
    {
        return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.IMMUTABLE);
    }

    /**
     * This operation is eager and consumes both sequences.<br/>
     * Two empty sequences or an empty sequence a null are all equal.
     */
    boolean equals(Seq<? extends T> seq);

    /**
     * Force all map and filter operations on the sequence
     * @return
     */
    default Seq<T> forceEager()
    {
        count();
        return this;
    }

    /**
     * Run each item in this sequence in the exec service calling the consumer.accept(v)
     */
    default <R> R doParallel(ExecutorService exec, Function<T, R> consumer, R unitVal, BiFunction<R, R, R> combiner)
    {
        //scatter [ Th[File1] Th[File2] Th[File3] Th[File4] ... ]
        List<Future<R>> futures = reduce(
                new ArrayList<Future<R>>(),
                (list, v) -> {
                    list.add(exec.submit( () -> consumer.apply(v)));
                    return list;
                });

        //gather and wait for all threads to complete
        return SeqUtil.seq(futures)
                .reduce(
                        unitVal,
                        (R a, Future<R> f) -> combiner.apply(a, LangUtils.futureGet(f)));
    }

}
