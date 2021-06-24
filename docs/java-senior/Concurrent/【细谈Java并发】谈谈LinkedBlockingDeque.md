### 【细谈Java并发】谈谈LinkedBlockingDeque

转载自[【细谈Java并发】谈谈LinkedBlockingDeque](https://benjaminwhx.com/2018/05/12/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88LinkedBlockingDeque/)

# 1、简介

上一篇我们介绍了 `LinkedBlockingDeque` 的兄弟篇 `LinkedBlockingQueue` 。听名字也知道一个实现了 `Queue` 接口，一个实现了 `Deque` 接口，由于 `Deque` 接口又继承于 `Queue` ，所以 `LinkedBlockingDeque` 自然就有 `LinkedBlockingQueue` 的所有方法，并且还提供了双端队列的一些其他方法，不清楚队列相关类的继承关系的童鞋，请移步看我之前的文章：（图片来自之前的文章：[说说队列Queue](https://benjaminwhx.com/2018/05/05/%E8%AF%B4%E8%AF%B4%E9%98%9F%E5%88%97Queue/)，下面的这张图就是该文章中的。

![img](http://benjaminwhx.com/images/queue.png)

# 2、源码分析

## 2.1、属性

```java
/**
 * 节点类，维护了前一个元素和后一个元素，用来存储数据
 */
static final class Node<E> {
    E item;
    Node<E> prev;
    Node<E> next;
    Node(E x) {
        item = x;
    }
}

/**
 * 阻塞队列的第一个元素的节点
 */
transient Node<E> first;

/**
 * 阻塞队列的尾节点
 */
transient Node<E> last;

/** 当前阻塞队列中的元素个数 */
private transient int count;

/** 阻塞队列的大小，默认为Integer.MAX_VALUE */
private final int capacity;

/** 所有访问元素时使用的锁 */
final ReentrantLock lock = new ReentrantLock();

/** 等待take的条件对象 */
private final Condition notEmpty = lock.newCondition();

/** 等待put的条件对象 */
private final Condition notFull = lock.newCondition();
```

由这些属性，我们可以和 `LinkedBlockingQueue` 进行对比。

首先是Node节点类，不同于 `LinkedBlockingQueue` 的单向链表，`LinkedBlockingDeque` 维护的是一个双向链表。

再来看count，这里是用int来进行修饰，而 `LinkedBlockingQueue` 确实用的AtomicInteger来修饰，这里这么做是因为 `LinkedBlockingDeque` 内部的每一个操作都共用一把锁，故能保证可见性。而 `LinkedBlockingQueue` 中维护了两把锁，在添加和移除元素的时候并不能保证双方能够看见count的修改，所以使用CAS来维护可见性。

## 2.2、构造函数

```java
public LinkedBlockingDeque() {
    this(Integer.MAX_VALUE);
}

public LinkedBlockingDeque(int capacity) {
    if (capacity <= 0) throw new IllegalArgumentException();
    this.capacity = capacity;
}

public LinkedBlockingDeque(Collection<? extends E> c) {
    this(Integer.MAX_VALUE);
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        for (E e : c) {
            if (e == null)
                throw new NullPointerException();
            if (!linkLast(new Node<E>(e)))
                throw new IllegalStateException("Deque full");
        }
    } finally {
        lock.unlock();
    }
}
```

构造函数几乎和 `LinkedBlockingQueue` 一样，不过少了一句 `last = head = new Node<E>(null)` 。因为这里不存在head节点了，而用first来代替。并且添加元素的方法也进行了重写来适应 `Deque` 的方法。

## 2.3、方法

`LinkedBlockingQueue`中有的方法该类中都会出现，无外乎多了队列的两端操作。这里为了方便，我会放在一起来进行说明。

### 2.3.1、入队方法

LinkedBlockingDeque提供了多种入队操作的实现来满足不同情况下的需求，入队操作有如下几种：

- add(E e)、addFirst(E e)、addLast(E e)
- offer(E e)、offerFirst(E e)、offerLast(E e)
- offer(E e, long timeout, TimeUnit unit)、offerFirst(E e, long timeout, TimeUnit unit)、offerLast(E e, long timeout, TimeUnit unit)
- put(E e)、putFirst(E e)、putLast(E e)

#### add相关的方法

```java
public boolean add(E e) {
    addLast(e);
    return true;
}

public void addFirst(E e) {
    if (!offerFirst(e))
        throw new IllegalStateException("Deque full");
}

public void addLast(E e) {
    if (!offerLast(e))
        throw new IllegalStateException("Deque full");
}
```

add调用的其实是addLast方法，而addFirst和addLast都调用的offer的相关方法，这里直接看offer的方法。

#### offer相关的方法

```java
public boolean offer(E e) {
    return offerLast(e);
}

public boolean offerFirst(E e) {
    if (e == null) throw new NullPointerException();
    Node<E> node = new Node<E>(e);
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return linkFirst(node);
    } finally {
        lock.unlock();
    }
}

public boolean offerLast(E e) {
    if (e == null) throw new NullPointerException();
    Node<E> node = new Node<E>(e);
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return linkLast(node);
    } finally {
        lock.unlock();
    }
}
```

很明显，加锁以后调用linkFirst和linkLast这两个方法。

```java
private boolean linkFirst(Node<E> node) {
    if (count >= capacity)
        return false;
    Node<E> f = first;
    node.next = f;
    first = node;
    // 插入第一个元素的时候才需要把last指向该元素，后面所有的操作只需要把f.prev指向node
    if (last == null)
        last = node;
    else
        f.prev = node;
    ++count;
    notEmpty.signal();
    return true;
}

private boolean linkLast(Node<E> node) {
    if (count >= capacity)
        return false;
    Node<E> l = last;
    node.prev = l;
    last = node;
    if (first == null)
        first = node;
    else
        l.next = node;
    ++count;
    notEmpty.signal();
    return true;
}
```

下面给出两张图，都是队列为空的情况下，调用linkFirst和linkLast依次放入元素A和元素B的图：

![LinkedBlockingDeque1](https://benjaminwhx.com/images/LinkedBlockingDeque1.png)

offer的超时方法这里就不放出了，原理和 `LinkedBlockingQueue` 一样，利用了Condition的awaitNanos进行超时等待，并在外面用while循环控制等待时的中断问题。

#### put相关的方法

```java
public void put(E e) throws InterruptedException {
    putLast(e);
}

public void putFirst(E e) throws InterruptedException {
    if (e == null) throw new NullPointerException();
    Node<E> node = new Node<E>(e);
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        // 阻塞等待linkFirst成功
        while (!linkFirst(node))
            notFull.await();
    } finally {
        lock.unlock();
    }
}

public void putLast(E e) throws InterruptedException {
    if (e == null) throw new NullPointerException();
    Node<E> node = new Node<E>(e);
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        // 阻塞等待linkLast成功
        while (!linkLast(node))
            notFull.await();
    } finally {
        lock.unlock();
    }
}
```

lock加锁后一直阻塞等待，直到元素插入到队列中。

### 2.3.2、出队方法

入队列的方法说完后，我们来说说出队列的方法。LinkedBlockingDeque提供了多种出队操作的实现来满足不同情况下的需求，如下：

- remove()、removeFirst()、removeLast()
- poll()、pollFirst()、pollLast()
- take()、takeFirst()、takeLast()
- poll(long timeout, TimeUnit unit)、pollFirst(long timeout, TimeUnit unit)、pollLast(long timeout, TimeUnit unit)

#### remove相关的方法

```java
public E remove() {
    return removeFirst();
}

public E removeFirst() {
    E x = pollFirst();
    if (x == null) throw new NoSuchElementException();
    return x;
}

public E removeLast() {
    E x = pollLast();
    if (x == null) throw new NoSuchElementException();
    return x;
}
```

remove方法调用了poll的相关方法。

#### poll相关的方法

```java
public E poll() {
    return pollFirst();
}

public E pollFirst() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return unlinkFirst();
    } finally {
        lock.unlock();
    }
}

public E pollLast() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return unlinkLast();
    } finally {
        lock.unlock();
    }
}
```

poll方法用lock加锁后分别调用了unlinkFirst和unlinkLast方法

```java
private E unlinkFirst() {
    Node<E> f = first;
    if (f == null)
        return null;
    Node<E> n = f.next;
    E item = f.item;
    f.item = null;
    f.next = f; // help GC
    // first指向下一个节点
    first = n;
    if (n == null)
        last = null;
    else
        n.prev = null;
    --count;
    notFull.signal();
    return item;
}

private E unlinkLast() {
    Node<E> l = last;
    if (l == null)
        return null;
    Node<E> p = l.prev;
    E item = l.item;
    l.item = null;
    l.prev = l; // help GC
    // last指向下一个节点
    last = p;
    if (p == null)
        first = null;
    else
        p.next = null;
    --count;
    notFull.signal();
    return item;
}
```

poll的超时方法也是利用了Condition的awaitNanos来做超时等待。这里就不做过多说明了。

#### take相关的方法

```java
public E take() throws InterruptedException {
    return takeFirst();
}

public E takeFirst() throws InterruptedException {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        E x;
        while ( (x = unlinkFirst()) == null)
            notEmpty.await();
        return x;
    } finally {
        lock.unlock();
    }
}

public E takeLast() throws InterruptedException {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        E x;
        while ( (x = unlinkLast()) == null)
            notEmpty.await();
        return x;
    } finally {
        lock.unlock();
    }
}
```

还是一个套路，lock加锁，while循环重试移除，await阻塞等待。

### 2.3.3、获取元素方法

获取元素的方法有element和peek两种方法。

```java
public E element() {
    return getFirst();
}

public E peek() {
    return peekFirst();
}

public E getFirst() {
    E x = peekFirst();
    if (x == null) throw new NoSuchElementException();
    return x;
}

public E getLast() {
    E x = peekLast();
    if (x == null) throw new NoSuchElementException();
    return x;
}

public E peekFirst() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return (first == null) ? null : first.item;
    } finally {
        lock.unlock();
    }
}

public E peekLast() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return (last == null) ? null : last.item;
    } finally {
        lock.unlock();
    }
}
```

获取元素前加锁，防止并发问题导致数据不一致。利用first和last节点直接可以获得元素。

### 2.3.4、删除元素方法

```java
public boolean remove(Object o) {
    return removeFirstOccurrence(o);
}

public boolean removeFirstOccurrence(Object o) {
    if (o == null) return false;
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        // 从first向后开始遍历比较，找到元素后调用unlink移除
        for (Node<E> p = first; p != null; p = p.next) {
            if (o.equals(p.item)) {
                unlink(p);
                return true;
            }
        }
        return false;
    } finally {
        lock.unlock();
    }
}

public boolean removeLastOccurrence(Object o) {
    if (o == null) return false;
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
         // 从last向前开始遍历比较，找到元素后调用unlink移除
        for (Node<E> p = last; p != null; p = p.prev) {
            if (o.equals(p.item)) {
                unlink(p);
                return true;
            }
        }
        return false;
    } finally {
        lock.unlock();
    }
}

void unlink(Node<E> x) {
    Node<E> p = x.prev;
    Node<E> n = x.next;
    if (p == null) {
        unlinkFirst();
    } else if (n == null) {
        unlinkLast();
    } else {
        p.next = n;
        n.prev = p;
        x.item = null;
        // Don't mess with x's links.  They may still be in use by
        // an iterator.
        --count;
        notFull.signal();
    }
}
```

删除元素是从头/尾向两边进行遍历比较，故时间复杂度为O(n)，最后调用unlink把要移除元素的prev和next进行关联，把要移除的元素从链中脱离，等待下次GC回收。

## 3、总结

LinkedBlockingDeque和LinkedBlockingQueue的相同点在于：

1. 基于链表
2. 容量可选，不设置的话，就是Int的最大值

和LinkedBlockingQueue的不同点在于：

1. 双端链表和单链表
2. 不存在头节点
3. 一把锁+两个条件