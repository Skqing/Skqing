### 【细谈Java并发】谈谈Copy-on-Write容器

转载自[【细谈Java并发】谈谈Copy-on-Write容器](https://benjaminwhx.com/2018/05/03/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88Copy-On-Write%E5%AE%B9%E5%99%A8/)

# 1、简介

Copy-On-Write简称COW，是一种用于程序设计中的优化策略。其基本思路是，从一开始大家都在共享同一个内容，当某个人想要修改这个内容的时候，才会真正把内容Copy出去形成一个新的内容然后再改，这是一种延时懒惰策略。从JDK1.5开始Java并发包里提供了两个使用CopyOnWrite机制实现的并发容器,它们是CopyOnWriteArrayList和CopyOnWriteArraySet。CopyOnWrite容器非常有用，可以在非常多的并发场景中使用到。

CopyOnWrite容器即写时复制的容器。通俗的理解是当我们往一个容器添加元素的时候，不直接往当前容器添加，而是先将当前容器进行Copy，复制出一个新的容器，然后新的容器里添加元素，添加完元素之后，再将原容器的引用指向新的容器。这样做的好处是我们可以对CopyOnWrite容器进行并发的读，而不需要加锁，因为当前容器不会添加任何元素。所以CopyOnWrite容器也是一种读写分离的思想，读和写不同的容器。

# 2、CopyOnWriteArrayList的实现原理

在使用CopyOnWriteArrayList之前，我们先阅读其源码了解下它是如何实现的。以下代码是向ArrayList里添加元素，可以发现在添加的时候是需要加锁的，否则多线程写的时候会Copy出N个副本出来。

```java
public boolean add(E e) {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        Object[] elements = getArray();
        int len = elements.length;
        // 复制出新数组
        Object[] newElements = Arrays.copyOf(elements, len + 1);
        // 把新元素添加到新数组里
        newElements[len] = e;
        // 把原数组引用指向新数组
        setArray(newElements);
        return true;
    } finally {
        lock.unlock();
    }
}
```

读的时候不需要加锁，如果读的时候有多个线程正在向ArrayList添加数据，读还是会读到旧的数据，因为写的时候不会锁住旧的ArrayList，因为数组对象是用volatile修饰的，所以多线程之间，数组会进行共享，当有一个线程修改了其值，会立即同步到主存中，当下一次再get的时候会发现和上一次get的值不同，因此读操作会发生脏读。

```java
private E get(Object[] a, int index) {
    return (E) a[index];
}

public E get(int index) {
    return get(getArray(), index);
}
```

我们再来看看它的迭代器

```java
static final class COWIterator<E> implements ListIterator<E> {
    private final Object[] snapshot;
    private int cursor;

    private COWIterator(Object[] elements, int initialCursor) {
        cursor = initialCursor;
        snapshot = elements;
    }

    public boolean hasNext() { return cursor < snapshot.length; }
    public boolean hasPrevious() { return cursor > 0; }

    @SuppressWarnings("unchecked")
    public E next() {
        if (! hasNext())
            throw new NoSuchElementException();
        return (E) snapshot[cursor++];
    }

    @SuppressWarnings("unchecked")
    public E previous() {
        if (! hasPrevious())
            throw new NoSuchElementException();
        return (E) snapshot[--cursor];
    }

    public int nextIndex() { return cursor; }
    public int previousIndex() { return cursor-1; }

    public void remove() { throw new UnsupportedOperationException(); }
    public void set(E e) { throw new UnsupportedOperationException(); }
    public void add(E e) { throw new UnsupportedOperationException(); }
}
```

我们知道ArrayList是非线性安全的集合，它使用迭代器时有可能会抛出ConcurrentModificationException。而且并发的时候进行插入操作时，由于没有进行同步操作，容易丢失数据。

可以看到，在COWIterator的迭代中，不能直接增删改，避免了ConcurrentModificationException。

# 3、CopyOnWriteArraySet的实现原理

通过源码可以看到，CopyOnWriteArraySet内部维护着一个CopyOnWriteArrayList，所有的操作都是通过它去完成的。

```java
private final CopyOnWriteArrayList<E> al;

/**
 * Creates an empty set.
 */
public CopyOnWriteArraySet() {
    al = new CopyOnWriteArrayList<E>();
}
```

我们主要看看add(E)方法的实现，它调用了CopyOnWriteArrayList的addIfAbsent方法

```java
public boolean addIfAbsent(E e) {
    // 当前数组的一个快照
    Object[] snapshot = getArray();
    // 如果传入的元素存在，直接返回false，否则增加
    return indexOf(e, snapshot, 0, snapshot.length) >= 0 ? false :
        addIfAbsent(e, snapshot);
}

private boolean addIfAbsent(E e, Object[] snapshot) {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        Object[] current = getArray();
        int len = current.length;
        // 传入的数组和当前数组不一样
        if (snapshot != current) {
            // Optimize for lost race to another addXXX operation
            int common = Math.min(snapshot.length, len);
            for (int i = 0; i < common; i++)
                if (current[i] != snapshot[i] && eq(e, current[i]))
                    return false;
            if (indexOf(e, current, common, len) >= 0)
                    return false;
        }
        // 创建一个新数组，长度为原数组长度+1
        Object[] newElements = Arrays.copyOf(current, len + 1);
        newElements[len] = e;
        setArray(newElements);
        return true;
    } finally {
        lock.unlock();
    }
}
```

# 4、自己实现CopyOnWriteMap

JDK中并没有提供CopyOnWriteMap，我们可以参考CopyOnWriteArrayList来实现一个，基本代码如下：

```java
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class CopyOnWriteMap<K, V> implements Map<K, V>, Cloneable {
    private volatile Map<K, V> internalMap;

    public CopyOnWriteMap() {
        internalMap = new HashMap<K, V>();
    }

    public V put(K key, V value) {

        synchronized (this) {
            Map<K, V> newMap = new HashMap<K, V>(internalMap);
            V val = newMap.put(key, value);
            internalMap = newMap;
            return val;
        }
    }

    public V get(Object key) {
        return internalMap.get(key);
    }

    public void putAll(Map<? extends K, ? extends V> newData) {
        synchronized (this) {
            Map<K, V> newMap = new HashMap<K, V>(internalMap);
            newMap.putAll(newData);
            internalMap = newMap;
        }
    }
}
```

实现很简单，只要了解了CopyOnWrite机制，我们可以实现各种CopyOnWrite容器，并且在不同的应用场景中使用。

# 5、CopyOnWrite的应用场景

CopyOnWrite并发容器用于读多写少的并发场景。比如白名单，黑名单，商品类目的访问和更新场景，假如我们有一个搜索网站，用户在这个网站的搜索框中，输入关键字搜索内容，但是某些关键字不允许被搜索。这些不能被搜索的关键字会被放在一个黑名单当中，黑名单每天晚上更新一次。当用户搜索时，会检查当前关键字在不在黑名单当中，如果在，则提示不能搜索。实现代码如下：

```java
package com.github.book;

import java.util.Map;

import com.github.book.forkjoin.CopyOnWriteMap;

/**
 * 黑名单服务
 */
public class BlackListServiceImpl {

    private static CopyOnWriteMap<String, Boolean> blackListMap = new CopyOnWriteMap<String, Boolean>(1000);

    public static boolean isBlackList(String id) {
        return blackListMap.get(id) == null ? false : true;
    }

    public static void addBlackList(String id) {
        blackListMap.put(id, Boolean.TRUE);
    }

    /**
     * 批量添加黑名单
     *
     * @param ids
     */
    public static void addBlackList(Map<String,Boolean> ids) {
        blackListMap.putAll(ids);
    }

}
```

代码很简单，但是使用CopyOnWriteMap需要注意两件事情：

1. 减少扩容开销。根据实际需要，初始化CopyOnWriteMap的大小，避免写时CopyOnWriteMap扩容的开销。
2. 使用批量添加。因为每次添加，容器每次都会进行复制，所以减少添加次数，可以减少容器的复制次数。如使用上面代码里的addBlackList方法。

# 6、CopyOnWrite的缺点

CopyOnWrite容器有很多优点，但是同时也存在两个问题，即内存占用问题和数据一致性问题。所以在开发的时候需要注意一下。

**内存占用问题**。因为CopyOnWrite的写时复制机制，所以在进行写操作的时候，内存里会同时驻扎两个对象的内存，旧的对象和新写入的对象（注意:在复制的时候只是复制容器里的引用，只是在写的时候会创建新对象添加到新容器里，而旧容器的对象还在使用，所以有两份对象内存）。如果这些对象占用的内存比较大，比如说200M左右，那么再写入100M数据进去，内存就会占用300M，那么这个时候很有可能造成频繁的Yong GC和Full GC。之前我们系统中使用了一个服务由于每晚使用CopyOnWrite机制更新大对象，造成了每晚15秒的Full GC，应用响应时间也随之变长。

针对内存占用问题，可以通过压缩容器中的元素的方法来减少大对象的内存消耗，比如，如果元素全是10进制的数字，可以考虑把它压缩成36进制或64进制。或者不使用CopyOnWrite容器，而使用其他的并发容器，如ConcurrentHashMap。

**数据一致性问题**。CopyOnWrite容器只能保证数据的最终一致性，不能保证数据的实时一致性。所以如果你希望写入的的数据，马上能读到，请不要使用CopyOnWrite容器。

# 7、对比Collections.synchronizedList

CopyOnWriteArrayList和Collections.synchronizedList是实现线程安全的列表的两种方式。两种实现方式分别针对不同情况有不同的性能表现。

因为CopyOnWriteArrayList的写操作不仅有lock锁，还在内部进行了数组的copy，所以性能比Collections.synchronizedList要低。

而读操作CopyOnWriteArrayList直接取的数组的值，Collections.synchronizedList却有synchronized修饰，所以读性能CopyOnWriteArrayList略胜一筹。

因此在不同的应用场景下，应该选择不同的多线程安全实现类。

> 总结：CopyOnWriteArrayList，发生修改时候做copy，新老版本分离，保证读的高性能，适用于以读为主，读操作远远大于写操作的场景中使用，比如缓存。而Collections.synchronizedList则可以用在CopyOnWriteArrayList不适用，但是有需要同步列表的地方，读写操作都比较均匀的地方。

参考：[JAVA中的COPYONWRITE容器](https://coolshell.cn/articles/11175.html)