package org.gerritjvv.lazyj;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;
import org.gerritjvv.lazyj.seq.Cons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test that the lazy sequence implementation is sound and work lazily and without blowing the stack.
 */
public class LazySeqTests {

    @Test
    public void testCloseableStreamsAsSeq() {
        AtomicBoolean closed1 = new AtomicBoolean(false);

        AtomicBoolean closed2 = new AtomicBoolean(false);

        Seq<Integer> seq = SeqUtil.seq(
                Lists.newArrayList(1, 2, 3, 4)
                        .stream()
                        .onClose(() -> closed1.set(true)))
                .onClose(() -> closed2.set(true));

        seq.close();

        assertTrue(closed1.get());
        assertTrue(closed2.get());
    }

    @Test
    public void testEquals() {
        assertTrue(lazyNumbers(10).equals(lazyNumbers(10)));
        assertFalse(lazyNumbers(10).equals(lazyNumbers(5)));
        assertFalse(lazyNumbers(5).equals(lazyNumbers(10)));

        assertFalse(lazyNumbers(5).equals(lazyNumbers(5).cons(-1).cons(-2)));

        assertTrue(Cons.EMPTY.equals(Cons.EMPTY));

        assertTrue(Cons.EMPTY.equals(null));

        assertFalse(lazyNumbers(10).equals(null));
    }

    @Test
    public void testDropMoreThanSeqLength() {
        Seq<Integer> seq = lazyNumbers(5).drop(1000);

        assertEquals(0, seq.count());
    }

    @Test
    public void testTakeMoreThanSeqLength() {
        Seq<Integer> seq = lazyNumbers(5).take(1000);

        assertEquals(5, seq.count());
    }

    /**
     * Both Handler 1 and 2 should be called<br/>
     * Handler2 should throw the last exception into the caller which is this method, and is caught in a try catch.
     */
    public void testCloseHandlerExceptions() {
        AtomicBoolean closed1 = new AtomicBoolean(false);
        AtomicBoolean closed2 = new AtomicBoolean(false);

        Runnable closeHandler1 = () ->
        {
            closed1.set(true);
            throw new RuntimeException();
        };
        Runnable closeHandler2 = () ->
        {
            closed2.set(true);
            throw new RuntimeException();
        };

        Seq<Integer> seq = lazyNumbers(len1())
                .onClose(closeHandler1)
                .map(v -> v + 1)
                .onClose(closeHandler2)
                .filter(v -> true)
                .take(10)
                .drop(1);

        try {
            seq.close();
            assertTrue("Exception expected from handler2", false);
        } catch (RuntimeException rte) {
            assertTrue(true);
        }

        assertTrue(closed1.get());
        assertTrue(closed2.get());
    }

    @Test
    public void testMultipleCloseHandler() {
        AtomicBoolean closed1 = new AtomicBoolean(false);
        AtomicBoolean closed2 = new AtomicBoolean(false);

        Runnable closeHandler1 = () -> closed1.set(true);
        Runnable closeHandler2 = () -> closed2.set(true);

        lazyNumbers(len1())
                .onClose(closeHandler1)
                .map(v -> v + 1)
                .onClose(closeHandler2)
                .filter(v -> true)
                .take(10)
                .drop(1)
                .cons(1)
                .concat(SeqUtil.seq(4, 5))
                .take(10)
                .distinct()
                .unordered()
                .sorted()
                .close();

        assertTrue(closed1.get());
        assertTrue(closed2.get());
    }

    @Test
    public void testCloseHandlerMapFilterTakeDrop() {
        AtomicBoolean closed = new AtomicBoolean(false);
        Runnable closeHandler = () -> closed.set(true);

        Seq<Integer> seq = lazyNumbers(len1()).onClose(closeHandler).map(v -> v + 1).filter(v -> true).take(10).drop(1);
        seq.close();

        assertTrue(closed.get());
    }

    @Test
    public void testCloseHandler() {
        try {
            AtomicBoolean closed = new AtomicBoolean(false);
            Runnable closeHandler = () -> closed.set(true);

            Seq<Integer> seq = lazyNumbers(len1()).onClose(closeHandler);
            seq.close();

            assertTrue(closed.get());
        } catch (StackOverflowError err) {
            err.printStackTrace();
            throw err;
        }
    }

    @Test
    public void testUnordered() {
        Seq<Integer> seq = lazyNumbers(len1());

        assertTrue(seq == seq.unordered());
    }


    @Test
    public void testPeek() {
        Set<Integer> seen = new HashSet<>();
        Seq<Integer> seq = lazyNumbers(len1());

        seq.peek(v -> seen.add(v)).count();

        seq.forEach(v -> assertTrue(seen.contains(v)));
    }

    @Test
    public void testMapToDouble() {
        int len1 = len1();
        Seq<Integer> seq = lazyNumbers(len1);

        assertEquals(seq.count(), seq.mapToDouble(Integer::doubleValue).count());

        double doubles[] = new double[(int) seq.count()];
        AtomicInteger counter = new AtomicInteger(0);
        seq.forEach(i -> doubles[counter.getAndIncrement()] = i.doubleValue());

        assertTrue(Arrays.equals(doubles, seq.mapToDouble(Integer::doubleValue).toArray()));
    }


    @Test
    public void testMapToLong() {
        int len1 = len1();
        Seq<Integer> seq = lazyNumbers(len1);

        assertEquals(seq.count(), seq.mapToLong(Integer::longValue).count());

        long longs[] = new long[(int) seq.count()];
        AtomicInteger counter = new AtomicInteger(0);
        seq.forEach(i -> longs[counter.getAndIncrement()] = i.longValue());

        assertTrue(Arrays.equals(longs, seq.mapToLong(Integer::longValue).toArray()));
    }

    @Test
    public void testMapToInt() {
        int len1 = len1();
        Seq<Integer> seq = lazyNumbers(len1);

        assertEquals(seq.count(), seq.mapToInt(Integer::intValue).count());

        int ints[] = new int[(int) seq.count()];
        AtomicInteger counter = new AtomicInteger(0);
        seq.forEach(i -> ints[counter.getAndIncrement()] = i.intValue());

        assertTrue(Arrays.equals(ints, seq.mapToInt(Integer::intValue).toArray()));
    }

    @Test
    public void testMatch() {
        int len1 = len1();

        Seq<Integer> nums = lazyNumbers(len1);
        List<Integer> ints = nums.toList();
        Collections.shuffle(ints);

        int itemToMatch = ints.get(0);

        assertTrue(nums.anyMatch(v -> v == itemToMatch));
        assertFalse(nums.anyMatch(v -> v == len1 + 1));

        assertTrue(nums.noneMatch(v -> v == len1 + 1));
        assertFalse(nums.noneMatch(v -> v == itemToMatch));

        assertFalse(nums.allMatch(v -> v == itemToMatch));
        assertTrue(nums.allMatch(v -> v < len1 + 1));
    }

    @Test
    public void testStreamMinMax() {
        int len1 = 10;

        List<Integer> ints = lazyNumbers(len1).toList();
        Collections.shuffle(ints);

        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;

        for (Integer i : ints) {
            max = Math.max(max, i);
            min = Math.min(min, i);
        }

        Seq<Integer> seq = SeqUtil.seq(ints);

        assertEquals(max, seq.max(Integer::compare).get().intValue());
        assertEquals(min, seq.min(Integer::compare).get().intValue());
    }


    @Test
    public void testStreamCollect() {
        int len1 = len1();

        Set<Integer> set = lazyNumbers(len1).collect(Collectors.toSet());

        assertEquals(len1, set.size());
    }

    @Test
    public void testFilterMapIndexed() {
        int len1 = len1();
        Seq<Integer> inputSeq = lazyNumbers(len1);

        Seq<Pair<Integer, Long>> seq =
                Functional.mapIndexed(
                        inputSeq,
                        (v, i) -> true,
                        (v, i) -> Pair.create(v, i),
                        0L);

        assertEquals(len1, inputSeq.count());

        // -- value decrements and checks the lazyseq created values,
        // -- index increments and checks the mapIndexed sequences
        AtomicInteger index = new AtomicInteger(0);
        AtomicInteger value = new AtomicInteger(len1);

        seq.forEach(v ->
        {
            assertEquals(index.getAndIncrement(), v.right.intValue());
            assertEquals(value.getAndDecrement(), v.left.intValue());
        });
    }

    @Test
    public void testFilterMapIndexedFilter() {
        int len1 = len1();
        Seq<Integer> inputSeq = Functional.repeatedly(() -> ThreadLocalRandom.current().nextInt(0, 1)).take(len1);

        long numberOfOnes = inputSeq.filter(v -> v == 1).count();

        Seq<Pair<Integer, Long>> seq =
                Functional.mapIndexed(
                        inputSeq,
                        (v, i) -> v == 1,
                        (v, i) -> Pair.create(v, i),
                        0L);

        assertEquals(numberOfOnes, seq.count());
    }

    @Test
    public void testMultipleTakes() {
        int len1 = len1();
        Seq<Integer> seq =
                lazyNumbers(len1)
                        .take(10)
                        .take(4)
                        .take(2);

        assertEquals(2, seq.count());
        assertEquals(seq.toList(), Lists.newArrayList(len1, len1 - 1));
    }

    @Test
    public void testMultipleMappings() {
        int len1 = 5;

        Object EVENT_1 = new Object();
        Object EVENT_2 = new Object();
        Object EVENT_3 = new Object();

        Set<Object> events = new HashSet<>();

        Function<Object, Function<Integer, Integer>> mapper = (event) -> (Integer i) ->
        {
            events.add(event);
            return Integer.valueOf(i + 1);
        };

        long result = lazyNumbers(len1)
                .map(mapper.apply(EVENT_1))
                .map(mapper.apply(EVENT_2))
                .map(mapper.apply(EVENT_3))
                .count();

        assertEquals(len1, result);
        assertEquals(Sets.newHashSet(EVENT_1, EVENT_2, EVENT_3), events);
    }

    @Test
    public void testDistinct() {
        int len1 = ThreadLocalRandom.current().nextInt(5, 20);

        //we create a sequence where each number is repeated number times, i.e 2 => [2,2] 3 => [3,3,3] ...
        Seq<Integer> seq = lazyNumbers(len1).mapcat(i -> constantlyN(i, i)).distinct();

        assertEquals(len1, seq.count());

        //explicitly test that all items are unique
        Set<Integer> seen = new HashSet<>();
        seq.forEach(v ->
        {
            assertTrue(!seen.contains(v));
            seen.add(v);
        });
    }

    @Test
    public void testSeq() {
        int len1 = Math.max(100, len1());

        List<String> buff = new ArrayList<>();
        for (int i = 0; i < len1; i++)
            buff.add(String.valueOf(i));

        Seq<String> seq = SeqUtil.seq(buff);

        assertEquals(len1, seq.count());

        AtomicInteger counter = new AtomicInteger(0);
        seq.forEach(v -> assertEquals(v, String.valueOf(counter.getAndIncrement())));
    }

    @Test
    public void testToList() {
        int len1 = len1();
        List<Integer> list = lazyNumbers(len1).toList();

        assertEquals(len1, list.size());

        AtomicInteger counter = new AtomicInteger(len1);
        list.forEach(i -> assertEquals(counter.getAndDecrement(), i.intValue()));
    }


    @Test
    public void testSortComparator() {
        int len1 = len1();
        Seq<Integer> seq = lazyNumbers(len1).sorted((a, b) -> a.compareTo(b));

        assertEquals(len1, seq.count());

        AtomicInteger counter = new AtomicInteger(1);
        seq.forEach(i -> assertEquals(counter.getAndIncrement(), i.intValue()));
    }

    @Test
    public void testSort() {
        int len1 = len1();
        Seq<Integer> seq = lazyNumbers(len1).sorted();

        assertEquals(len1, seq.count());
        AtomicInteger counter = new AtomicInteger(1);

        seq.forEach(i -> assertEquals(counter.getAndIncrement(), i.intValue()));
    }

    @Test
    public void testReduceEmpty() {
        assertEquals(0, lazyNumbers(0).reduce(0, (s, i) -> 1000).intValue());
    }

    @Test
    public void testReduce() {
        int len1 = len1();
        int sum = 0;

        for (int i = len1; i > 0; i--)
            sum += i;

        assertEquals(sum,
                lazyNumbers(len1).reduce(0, (s, i) -> s + i).intValue());
    }

    @Test
    public void testFilter() {
        int len1 = len1();

        //only return even numbers
        Seq<Integer> seq = lazyNumbers(len1).filter(i -> i % 2 == 0);

        //the total number of even or odd numbers between n and m where m > n => [(n ... m) | m > n ]
        //is half the length
        assertEquals(len1 / 2, seq.count());
    }

    @Test
    public void testTake() {
        int len1 = len1();
        int takeN = ThreadLocalRandom.current().nextInt(10, len1 - 10);

        Seq<Integer> seq = lazyNumbers(len1).take(takeN);

        assertEquals(takeN, seq.count());

        AtomicInteger counter = new AtomicInteger(len1);
        seq.forEach(v -> assertEquals(counter.getAndDecrement(), v.longValue()));
    }

    @Test
    public void testDrop() {
        int len1 = len1();
        int dropN = ThreadLocalRandom.current().nextInt(10, len1 - 10);

        Seq<Integer> seq = lazyNumbers(len1).drop(dropN);

        assertEquals(len1 - dropN, seq.count());

        AtomicInteger counter = new AtomicInteger(len1);

        seq.forEach(v -> assertEquals(counter.getAndDecrement() - dropN, v.longValue()));
    }

    @Test
    public void testMapCat() {
        int len1 = ThreadLocalRandom.current().nextInt(5, 20);

        //we create a sequence where each number is repeated number times, i.e 2 => [2,2] 3 => [3,3,3] ...
        Seq<Integer> seq = lazyNumbers(len1).mapcat(i -> constantlyN(i, i));

        Map<Integer, AtomicInteger> vals = new HashMap<>();

        //group by key which means [2,2] becomes 2 and Counter(), 3 becomes 3 and Counter and so on.
        seq.forEach(v -> vals.computeIfAbsent(v, i -> new AtomicInteger(0)).getAndIncrement());

        assertEquals(len1, vals.size());
        vals.forEach((k, v) -> assertEquals(k.longValue(), v.get()));
    }

    @Test
    public void testMap() {
        int len1 = len1();
        Seq<String> seq = lazyNumbers(len1).map(i -> String.valueOf(i));

        assertEquals(seq.count(), len1);

        AtomicInteger counter = new AtomicInteger(len1);
        seq.forEach(v -> assertEquals(v, (String.valueOf(counter.getAndDecrement()))));
    }

    @Test
    public void testConcat() {

        int len1 = len1();
        Seq<Integer> nums1 = lazyNumbers(len1);
        Seq<Integer> nums2 = lazyNumbers(len1);

        Seq<Integer> nums3 = nums1.concat(nums2);

        assertEquals(len1 * 2, nums3.count());

        AtomicInteger counter = new AtomicInteger(len1);
        nums3.forEach(i ->
        {
            assertEquals(i.longValue(), counter.getAndDecrement());

            if (counter.get() == 0)
                counter.set(len1);
        });
    }

    @Test
    public void testCons() {
        int len1 = 10;
        int len2 = 1000;
        Seq<Integer> nums = lazyNumbers(len1);

        Seq<Integer> nums2 = nums;

        for (int i = 0; i < len2; i++)
            nums2 = nums2.cons(len1 + i + 1);

        assertEquals(len1 + len2, nums2.count());

        AtomicInteger counter = new AtomicInteger(len1 + len2);

        nums2.forEach((Integer i) -> assertEquals(i.longValue(), counter.getAndDecrement()));
    }

    @Test
    public void testLazySeqLazyEval() {
        Object v = Functional.lazySeq(1, () ->
        {
            throw new RuntimeException("NOOP");
        }).first();

        assertEquals(1, v);
    }


    @Test
    public void testLazySeqConsNotBlowStack() {
        Set<Integer> ints = new HashSet<>();

        int len = 1000000;
        for (int i : lazyNumbers(len))
            ints.add(i);

        assertEquals(len, ints.size());
    }

    @Test
    public void testLazySeqAsStream() {
        int len = 1000;
        Stream<Integer> s = lazyNumbers(len).stream();

        assertEquals(len, s.count());
    }

    @Test
    public void testLazySeqAsStreamAsSeq() {
        int len = 1000;
        Seq<Integer> s = SeqUtil.seq(lazyNumbers(len).stream());
        assertEquals(len, s.stream().count());
    }


    @Test
    public void testLazySeqReduceSum() {
        int len = 10;
        int testsum = 0;

        for (int i = 0; i < len + 1; i++)
            testsum += i;

        int sum = lazyNumbers(len).<Integer>reduce(0, (Integer acc, Integer i) -> acc + i);

        assertEquals(testsum, sum);
    }

    private static Seq<Integer> constantlyN(int n, int v) {
        if (n > 0)
            return Functional.lazySeq(v, () -> constantlyN(n - 1, v));
        else
            return null;
    }

    /**
     * A lazy sequence of numbers
     */
    private static Seq<Integer> lazyNumbers(int i) {
        if (i > 0)
            return Functional.lazySeq(i, () -> lazyNumbers(i - 1));
        else
            return Functional.lazySeqEmpty();
    }

    private static final int len1() {
        return ThreadLocalRandom.current().nextInt(100, 1000);
    }
}
