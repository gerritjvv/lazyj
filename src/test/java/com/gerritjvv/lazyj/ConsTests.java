package com.gerritjvv.lazyj;

import java.util.Iterator;

import com.gerritjvv.lazyj.seq.LazySeq;
import org.junit.Test;

import com.gerritjvv.lazyj.seq.Cons;

import static org.junit.Assert.assertEquals;

/**
 * Test that the different implementations of Cons and Seq works by adding an item to the start of a sequence.
 */
public class ConsTests {

    @Test
    public void testCons() {
        Seq<Integer> cell = Cons.create(null, null);
        int len = 10;

        //add cons cells of numbers 9 - 0
        for (int i = 0; i < len; i++)
            cell = cell.cons(i);


        //test we have 9 - 0
        int i = 9;
        for (Integer el : cell)
            assertEquals(i--, el.intValue());
    }

    @Test
    public void testIteratorSeqCons() {
        Seq<Integer> cell = Cons.create(null, null);
        int len = 10;

        //add cons cells of numbers 9 - 0
        for (int i = 0; i < len; i++)
            cell = cell.cons(i);


        //we explicitly create a IteratorSeq here to test its cons operator
        Iterator<Integer> itseq = cell.iterator();

        //we add another value 10
        //then check we have cons seq 10-0
        Seq<Integer> updated_cell = SeqUtil.seq(itseq, null).cons(10);

        int i = 10;
        for (Integer el : updated_cell)
            assertEquals(i--, el.intValue());
    }

    @Test
    public void testLazySeqCons() {
        Seq<Integer> cell = LazySeq.create(() -> Functional.lazySeq(10)).cons(9).cons(8);

        assertEquals(8, cell.first().intValue());
        assertEquals(9, cell.next().first().intValue());
        assertEquals(10, cell.next().next().first().intValue());
    }

}
