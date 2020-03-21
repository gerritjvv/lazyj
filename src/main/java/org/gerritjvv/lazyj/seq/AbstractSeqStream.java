package org.gerritjvv.lazyj.seq;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.gerritjvv.lazyj.Seq;

/**
 * Extends {@link AbstractSeq} with implementations for Stream.<br/>
 * Most methods in this class are not lazy and therefore eager, and should not be used
 * with infinite sequences.
 */
public abstract class AbstractSeqStream<T> extends AbstractSeq<T> {

    public AbstractSeqStream(Runnable closeHandler) {
        super(closeHandler);
    }

    @Override
    public boolean isParallel() {
        return false;
    }

    @Override
    public Stream<T> sequential() {
        return this;
    }

    @Override
    public Stream<T> parallel() {
        return this;
    }

    @Override
    public Stream<T> unordered() {
        return this;
    }

    @Override
    public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return mapcat((v) -> seq(mapper.apply(v).iterator()));
    }

    @Override
    public Stream<T> peek(Consumer<? super T> action) {
        //observer the action as each item is iterated over
        return map((v) ->
        {
            action.accept(v);
            return v;
        });
    }

    @Override
    public Object[] toArray() {
        return toList().toArray();
    }

    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        List<T> list = toList();

        return list.toArray(generator.apply(list.size()));
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        A mutableResult = collector.supplier().get();
        BiConsumer<A, ? super T> accumulator = collector.accumulator();

        forEach((v) -> accumulator.accept(mutableResult, v));

        return collector.finisher().apply(mutableResult);
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        R mutableResult = supplier.get();
        forEach((v) -> accumulator.accept(mutableResult, v));

        return mutableResult;
    }

    @Override
    public void forEachOrdered(Consumer<? super T> action) {
        sorted().forEach(action);
    }

    @Override
    public Stream<T> skip(long n) {
        return drop(n);
    }

    @Override
    public Stream<T> limit(long maxSize) {
        return take(maxSize);
    }

    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        return max((a, b) -> comparator.compare(b, a));
    }

    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        T first;
        Seq<T> seq = this;

        T max = null;

        while ((first = seq.first()) != null) {
            if (max == null)
                max = first;
            else {
                if (comparator.compare(max, first) < 0)
                    max = first;
            }

            seq = seq.next();
        }

        return Optional.ofNullable(max);
    }

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        return reduce(identity, (BiFunction<T, T, T>) accumulator);
    }

    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        T first = first();
        if (first == null)
            return Optional.empty();

        return Optional.ofNullable(next().reduce(first, accumulator));
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        return reduce(identity, (s, u) -> accumulator.apply(s, u));
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        T first = null;
        Seq<T> seq = this;

        while ((first = seq.first()) != null) {
            if (predicate.test(first))
                return true;
            seq = seq.next();
        }

        return false;
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        T first = first();
        if (first == null || !predicate.test(first))
            return false;

        Seq<T> seq = next();

        while ((first = seq.first()) != null) {
            if (!predicate.test(first))
                return false;

            seq = seq.next();
        }

        return true;
    }

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        return !filter(predicate).findAny().isPresent();
    }

    @Override
    public Optional<T> findFirst() {
        return findAny();
    }

    @Override
    public Optional<T> findAny() {
        return Optional.ofNullable(first());
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        Seq<T> seq = this;

        return StreamSupport.intStream(new Spliterator.OfInt() {

                                           Seq<T> itSeq = seq;
                                           T first = null;

                                           @Override
                                           public OfInt trySplit() {
                                               //split is not supported
                                               return null;
                                           }

                                           @Override
                                           public boolean tryAdvance(IntConsumer action) {
                                               if ((first = itSeq.first()) != null) {
                                                   action.accept(mapper.applyAsInt(first));
                                                   itSeq = itSeq.next();
                                                   return true;
                                               }
                                               return false;
                                           }

                                           @Override
                                           public long estimateSize() {
                                               //no split supported, return zero
                                               return 0;
                                           }

                                           @Override
                                           public int characteristics() {
                                               return Spliterator.IMMUTABLE;
                                           }
                                       },
                false);
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        Seq<T> seq = this;

        return StreamSupport.longStream(new Spliterator.OfLong() {

                                            Seq<T> itSeq = seq;
                                            T first = null;

                                            @Override
                                            public OfLong trySplit() {
                                                //split is not supported
                                                return null;
                                            }

                                            @Override
                                            public boolean tryAdvance(LongConsumer action) {
                                                if ((first = itSeq.first()) != null) {
                                                    action.accept(mapper.applyAsLong(first));
                                                    itSeq = itSeq.next();
                                                    return true;
                                                }
                                                return false;
                                            }

                                            @Override
                                            public long estimateSize() {
                                                //no split supported, return zero
                                                return 0;
                                            }

                                            @Override
                                            public int characteristics() {
                                                return Spliterator.IMMUTABLE;
                                            }
                                        },
                false);
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        Seq<T> seq = this;

        return StreamSupport.doubleStream(new Spliterator.OfDouble() {

                                              Seq<T> itSeq = seq;
                                              T first = null;

                                              @Override
                                              public OfDouble trySplit() {
                                                  //split is not supported
                                                  return null;
                                              }

                                              @Override
                                              public boolean tryAdvance(DoubleConsumer action) {
                                                  if ((first = itSeq.first()) != null) {
                                                      action.accept(mapper.applyAsDouble(first));
                                                      itSeq = itSeq.next();
                                                      return true;
                                                  }
                                                  return false;
                                              }

                                              @Override
                                              public long estimateSize() {
                                                  //no split supported, return zero
                                                  return 0;
                                              }

                                              @Override
                                              public int characteristics() {
                                                  return Spliterator.IMMUTABLE;
                                              }
                                          },
                false);
    }

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        T first = first();
        if (first == null)
            return IntStream.empty();

        return IntStream.concat(mapper.apply(first), next().flatMapToInt(mapper));
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        T first = first();
        if (first == null)
            return LongStream.empty();

        return LongStream.concat(mapper.apply(first), next().flatMapToLong(mapper));
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        T first = first();
        if (first == null)
            return DoubleStream.empty();

        return DoubleStream.concat(mapper.apply(first), next().flatMapToDouble(mapper));
    }

    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.IMMUTABLE);
    }
}
