# Lazy Functional Sequences for Java

This library contains many classes for lazy functional sequences, and have been hardened from using it in production
in cluster to cluster replication. Where data coming in has been modeled as infinite sequences.

This abstract model helped to simplify programming as a whole, even though the actual events were stored in several
files and in multiple directories.

## Usage


**Infinite Sequence**

 ```java
 private static Seq<Integer> lazyNumbers(int i){
     if (i > 0)
        return Functional.lazySeq(i, () -> lazyNumbers(i - 1));
     else
         return Functional.lazySeqEmpty();
 }
```

**Operations**

```java
lazyNumbers(100)
   .map(v -> v + 1)
   .filter(v -> v == 2)
   .take(10)
   .drop(1)
   .cons(1)
   .concat(SeqUtil.seq(4, 5))
   .take(10)
   .distinct()
   .unordered()
   .sorted();
```

```java
Seq<Integer> inputSeq = lazyNumbers(10);
Seq<Pair<Integer, Long>> seq =
   Functional.mapIndexed(
     inputSeq,
     (v, i) -> true,
     (v, i) -> Pair.create(v, i),
     0L);
```


## More examples:

For more examples see [LazySeqTests](https://github.com/gerritjvv/lazyj/blob/master/src/test/java/org/gerritjvv/lazyj/LazySeqTests.java)

## Recursive programming:

Sequences, especially LazySequence(s) allow to program recursively without blowing the stack.  
This is done by encapsulating each next call into a closure instance. Only called when the next item is iterated on.

**Merged Operations:**  

Consecutive maps are merged into one sequence instance and then same is done for filters and take operations.  
A filter operation can merge in map operations but not the other way around.

**Close Handler:**

Streams are different from Sequences in that Streams can only be used once and then closed and thrown away  
Sequences are immutable and in general has no notion of being closed or open. We support the close handler   
mechanism for Streams to allow bottom down IO operations, but when used sequences should be semantically used as<  
Streams, i.e when closed not used again.  

**Null values:**  

Null values cannot be stored in sequences, because null signals an end of sequence.  
For null values use Optional.


# Developer details

## Maven repo

Docs: https://www.rainerhahnekamp.com/en/publishing-a-java-library-to-maven-central/

See: https://issues.sonatype.org/browse/OSSRH-56033

