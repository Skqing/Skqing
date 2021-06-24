### 【细谈Java并发】谈谈ArrayBlockingQueue

转载自[【细谈Java并发】谈谈ArrayBlockingQueue](https://benjaminwhx.com/2018/05/07/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88ArrayBlockingQueue/)

# 1、简介

ArrayBlockingQueue，顾名思义：基于数组的阻塞队列。数组是要指定长度的，所以使用ArrayBlockingQueue时必须指定长度，也就是它是一个有界队列。

它实现了BlockingQueue接口，有着队列、集合以及阻塞队列的所有方法，队列类图如下图所示（图片来自之前的文章：[说说队列Queue](https://benjaminwhx.com/2018/05/05/%E8%AF%B4%E8%AF%B4%E9%98%9F%E5%88%97Queue/)：

![img](http://benjaminwhx.com/images/queue.png)

既然它在JUC包内，说明使用它是线程安全的，它内部使用ReentrantLock来保证线程安全。ArrayBlockingQueue支持对生产者线程和消费者线程进行公平的调度，默认情况下是不保证公平性的。公平性通常会降低吞吐量，但是减少了可变性和避免了线程饥饿问题。

# 2、数据结构

通常，队列的实现方式有数组和链表两种方式。对于数组这种实现方式来说，我们可以通过维护一个队尾指针，使得在入队的时候可以在O(1)的时间内完成；但是对于出队操作，在删除队头元素之后，必须将数组中的所有元素都往前移动一个位置，这个操作的复杂度达到了O(n)，效果并不是很好。如下图所示：

![ArrayBlockingQueue](https://benjaminwhx.com/images/ArrayBlockingQueue.png)

为了解决这个问题，我们可以使用另外一种逻辑结构来处理数组中各个位置之间的关系。假设现在我们有一个数组A[1…n]，我们可以把它想象成一个环型结构，即A[n]之后是A[1]，相信了解过一致性Hash算法的童鞋应该很容易能够理解。如下图所示：我们可以使用两个指针，分别维护队头和队尾两个位置，使入队和出队操作都可以在O(1)的时间内完成。当然，这个环形结构只是逻辑上的结构，实际的物理结构还是一个普通的数据。

![ArrayBlockingQueue2](https://benjaminwhx.com/images/ArrayBlockingQueue2.png)

讲完ArrayBlockingQueue的数据结构，接下来我们从源码层面看看它是如何实现**阻塞**的。

# 3、源码分析

## 3.1、属性

```java
//队列的底层结构
final Object[] items;

//队头指针
int takeIndex;
//队尾指针
int putIndex;

//队列中的元素个数
int count;

final ReentrantLock lock;

//并发时的两种状态
private final Condition notEmpty;
private final Condition notFull;
```

items是一个数组，用来存放入队的数据，count表示队列中元素的个数。takeIndex和putIndex分别代表队头和队尾指针。

## 3.2、构造函数

```java
public ArrayBlockingQueue(int capacity) {
    this(capacity, false);
}

public ArrayBlockingQueue(int capacity, boolean fair) {
    if (capacity <= 0)
        throw new IllegalArgumentException();
    this.items = new Object[capacity];
    lock = new ReentrantLock(fair);
    notEmpty = lock.newCondition();
    notFull =  lock.newCondition();
}

public ArrayBlockingQueue(int capacity, boolean fair,
                          Collection<? extends E> c) {
    this(capacity, fair);

    final ReentrantLock lock = this.lock;
    lock.lock(); // Lock only for visibility, not mutual exclusion
    try {
        int i = 0;
        try {
            for (E e : c) {
                checkNotNull(e);
                items[i++] = e;
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new IllegalArgumentException();
        }
        count = i;
        putIndex = (i == capacity) ? 0 : i;
    } finally {
        lock.unlock();
    }
}
```

第一个构造函数只需要制定队列大小，默认为非公平锁。

第二个构造函数可以手动制定公平性和队列大小。

第三个构造函数里面使用了ReentrantLock来加锁，然后把传入的集合元素按顺序一个个放入items中。这里加锁目的不是使用它的互斥性，而是让items中的元素对其他线程可见（用的是AQS里的state的volatile可见性）。

## 3.3、方法

### 3.3.1、入队方法

ArrayBlockingQueue 提供了多种入队操作的实现来满足不同情况下的需求，入队操作有如下几种：

- boolean add(E e);
- void put(E e)；
- boolean offer(E e)；
- boolean offer(E e, long timeout, TimeUnit unit)。

#### add(E e)

```java
public boolean add(E e) {
    return super.add(e);
}

//super.add(e)
public boolean add(E e) {
    if (offer(e))
        return true;
    else
        throw new IllegalStateException("Queue full");
}
```

可以看到add方法调用的是父类，也就是AbstractQueue的add方法，它实际上调用的就是offer方法。

#### offer(E e)

我们接着上面的add方法来看offer方法：

```java
public boolean offer(E e) {
    checkNotNull(e);
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        if (count == items.length)
            return false;
        else {
            enqueue(e);
            return true;
        }
    } finally {
        lock.unlock();
    }
}
```

offer方法在队列满了的时候返回false，否则调用enqueue方法插入元素，并返回true。

```java
private void enqueue(E x) {
    final Object[] items = this.items;
    items[putIndex] = x;
    // 圆环的index操作
    if (++putIndex == items.length)
        putIndex = 0;
    count++;
    notEmpty.signal();
}
```

enqueue方法首先把元素放在items的putIndex位置，接着判断在putIndex+1等于队列的长度时把putIndex设置为0，也就是上面提到的圆环的index操作。最后唤醒等待获取元素的线程。

#### offer(E e, long timeout, TimeUnit unit)

offer(E e, long timeout, TimeUnit unit)方法只是在offer(E e)的基础上增加了超时时间的概念。

```java
public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {

    checkNotNull(e);
    // 把超时时间转换成纳秒
    long nanos = unit.toNanos(timeout);
    final ReentrantLock lock = this.lock;
    // 获取一个可中断的互斥锁
    lock.lockInterruptibly();
    try {
        // while循环的目的是防止在中断后没有到达传入的timeout时间，继续重试
        while (count == items.length) {
            if (nanos <= 0)
                return false;
            // 等待nanos纳秒，返回剩余的等待时间(可被中断)
            nanos = notFull.awaitNanos(nanos);
        }
        enqueue(e);
        return true;
    } finally {
        lock.unlock();
    }
}
```

该方法利用了Condition的awaitNanos方法，等待指定时间，因为该方法可中断，所以这里利用while循环来处理中断后还有剩余时间的问题，等待时间到了以后调用enqueue方法放入队列。

#### put(E e)

```java
public void put(E e) throws InterruptedException {
    checkNotNull(e);
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    try {
        while (count == items.length)
            notFull.await();
        enqueue(e);
    } finally {
        lock.unlock();
    }
}
```

put方法在count等于items长度时，一直等待，直到被其他线程唤醒。唤醒后调用enqueue方法放入队列。

### 3.3.2、出队方法

入队列的方法说完后，我们来说说出队列的方法。ArrayBlockingQueue提供了多种出队操作的实现来满足不同情况下的需求，如下：

- E poll();
- E poll(long timeout, TimeUnit unit);
- E take()。

#### poll()

```java
public E poll() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return (count == 0) ? null : dequeue();
    } finally {
        lock.unlock();
    }
}
```

poll方法是非阻塞方法，如果队列没有元素返回null，否则调用dequeue把队首的元素出队列。

```java
private E dequeue() {
    final Object[] items = this.items;
    @SuppressWarnings("unchecked")
    E x = (E) items[takeIndex];
    items[takeIndex] = null;
    if (++takeIndex == items.length)
        takeIndex = 0;
    count--;
    if (itrs != null)
        itrs.elementDequeued();
    notFull.signal();
    return x;
}
```

dequeue会根据takeIndex获取到该位置的元素，并把该位置置为null，接着利用圆环原理，在takeIndex到达列表长度时设置为0，最后唤醒等待元素放入队列的线程。

#### poll(long timeout, TimeUnit unit)

该方法是poll()的可配置超时等待方法，和上面的offer一样，使用while循环+Condition的awaitNanos来进行等待，等待时间到后执行dequeue获取元素。

```java
public E poll(long timeout, TimeUnit unit) throws InterruptedException {
    long nanos = unit.toNanos(timeout);
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    try {
        while (count == 0) {
            if (nanos <= 0)
                return null;
            nanos = notEmpty.awaitNanos(nanos);
        }
        return dequeue();
    } finally {
        lock.unlock();
    }
}
```

#### take()

```java
public E take() throws InterruptedException {
    final ReentrantLock lock = this.lock;
    lock.lockInterruptibly();
    try {
        while (count == 0)
            notEmpty.await();
        return dequeue();
    } finally {
        lock.unlock();
    }
}
```

### 3.3.3、获取元素方法

#### peek()

```java
public E peek() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return itemAt(takeIndex); // null when queue is empty
    } finally {
        lock.unlock();
    }
}

final E itemAt(int i) {
    return (E) items[i];
}
```

这里获取元素时上锁是为了避免脏数据的产生。

### 3.3.4、删除元素方法

#### remove(Object o)

我们可以想象一下，队列中删除某一个元素时，是不是要遍历整个数据找到该元素，并把该元素后的所有元素往前移一位，也就是说，该方法的时间复杂度为O(n)。

```java
public boolean remove(Object o) {
    if (o == null) return false;
    final Object[] items = this.items;
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        if (count > 0) {
            final int putIndex = this.putIndex;
            int i = takeIndex;
             // 从takeIndex一直遍历到putIndex，直到找到和元素o相同的元素，调用removeAt进行删除
            do {
                if (o.equals(items[i])) {
                    removeAt(i);
                    return true;
                }
                if (++i == items.length)
                    i = 0;
            } while (i != putIndex);
        }
        return false;
    } finally {
        lock.unlock();
    }
}
```

remove方法比较简单，它从takeIndex一直遍历到putIndex，直到找到和元素o相同的元素，调用removeAt进行删除。我们重点来看一下removeAt方法。

```java
void removeAt(final int removeIndex) {
    final Object[] items = this.items;
    if (removeIndex == takeIndex) {
        // removing front item; just advance
        items[takeIndex] = null;
        if (++takeIndex == items.length)
            takeIndex = 0;
        count--;
        if (itrs != null)
            itrs.elementDequeued();
    } else {
        // an "interior" remove

        // slide over all others up through putIndex.
        final int putIndex = this.putIndex;
        for (int i = removeIndex;;) {
            int next = i + 1;
            if (next == items.length)
                next = 0;
            if (next != putIndex) {
                items[i] = items[next];
                i = next;
            } else {
                items[i] = null;
                this.putIndex = i;
                break;
            }
        }
        count--;
        if (itrs != null)
            itrs.removedAt(removeIndex);
    }
    notFull.signal();
}
```

removeAt的处理方式和我想的稍微有一点出入，它内部分为两种情况来考虑

- removeIndex == takeIndex
- removeIndex != takeIndex

也就是我考虑的时候没有考虑边界问题。当removeIndex == takeIndex时就不需要后面的元素整体往前移了，而只需要把takeIndex的指向下一个元素即可（还记得前面说的ArrayBlockingQueue可以类比为圆环吗）。

当removeIndex != takeIndex时，通过putIndex将removeIndex后的元素往前移一位。

# 4、总结

ArrayBlockingQueue是一个阻塞队列，内部由ReentrantLock来实现线程安全，由Condition的await和signal来实现等待唤醒的功能。它的数据结构是数组，准确的说是一个循环数组（可以类比一个圆环），所有的下标在到达最大长度时自动从0继续开始。