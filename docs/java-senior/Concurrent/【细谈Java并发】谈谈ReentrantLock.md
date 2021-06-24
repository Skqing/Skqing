### 【细谈Java并发】谈谈ReentrantLock

转载自[【细谈Java并发】谈谈ReentrantLock](https://benjaminwhx.com/2018/05/02/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88ReentrantLock/)

在Java中通常实现锁有两种方式，一种是synchronized关键字，另一种是Lock。二者其实并没有什么必然联系，但是各有各的特点，在使用中可以进行取舍的使用。具体的区别本文就不讲了，主要讲讲Lock的实现类ReentrantLock。

# 1、简介

ReentrantLock是基于AQS（AbstractQueuedSynchronized）来实现的，本文很多方法都是基于AQS来实现的，这里也就不再赘述相关的方法，所以建议阅读本文前先理解什么是AQS：[【细谈Java并发】谈谈AQS](http://benjaminwhx.com/2018/04/30/【细谈Java并发】谈谈AQS/)。

阅读本文之前还需要了解几个概念：

1. 可重入锁：指的是同一线程外层函数获得锁之后 ，内层递归函数仍然有获取该锁的代码。也就是同一线程可以获得多个锁。不然就会发生死锁问题。
2. 可中断锁：在某些条件下可以相应中断的锁。在Java中，synchronized就不是可中断锁，而 Lock是可中断锁。
3. 公平锁：公平锁即尽量以请求锁的顺序来获取锁。比如同是有多个线程在等待一个锁，当这个锁被释放时，等待时间最久的线程（最先请求的线程）会获得该所，这种就是公平锁。
4. 非公平锁：无法保证锁的获取是按照请求锁的顺序进行的。有可能锁刚被释放，正好新来了个线程请求锁，这样后面等待的线程就不能获得锁。在极端情况下，可能导致一直无法获取锁。

# 2、框架

ReentrantLock中提供了一个Sync类重写了AQS的几个重要方法，并且基于Sync提供了FairSync和NonfairSync两个类。

![img](https://benjaminwhx.com/images/reentrantLock.png)

```java
public ReentrantLock() {
    sync = new NonfairSync();
}

public ReentrantLock(boolean fair) {
    sync = fair ? new FairSync() : new NonfairSync();
}
```

可以通过构造函数看到，默认使用的是非公平锁，当然我们也可以通过参数指定使用哪个类。

在ReentrantLock中，我们最常用的也就两个方法：lock和unlock，其他方法本文就不说了。

# 3、源码分析

## 3.1、Sync

Sync继承自AQS，提供了许多加锁解锁的方法，而公平锁（FairSync）和非公平锁（NonfairSync）主要就是在加锁的逻辑控制这块稍有不同。

```java
static final class FairSync extends ReentrantLock.Sync {
    private static final long serialVersionUID = -3000897897090466540L;

    final void lock() {
        acquire(1);
    }

    /**
     * 获取公平锁
     * 1. 当前无锁，并且没有等待更久的线程的话，给当前线程上锁
     * 2. 当前有锁，但是加锁的线程是当前线程，增加状态值
     * 其他情况返回false
     */
    protected final boolean tryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();
        if (c == 0) {
            if (!hasQueuedPredecessors() &&
                    compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(current);
                return true;
            }
        }
        else if (current == getExclusiveOwnerThread()) {
            int nextc = c + acquires;
            if (nextc < 0)
                throw new Error("Maximum lock count exceeded");
            setState(nextc);
            return true;
        }
        return false;
    }
}

static final class NonfairSync extends Sync {
    private static final long serialVersionUID = 7316153563782823691L;

    final void lock() {
        if (compareAndSetState(0, 1))
            setExclusiveOwnerThread(Thread.currentThread());
        else
            acquire(1);
    }

    /**
     * 获取非公平锁
     * 1. 当前无锁，给当前线程上锁
     * 2. 当前有锁，但是加锁的线程是当前线程，增加状态值
     * 其他情况返回false
     */
    protected final boolean tryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();
        if (c == 0) {
            if (compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(current);
                return true;
            }
        }
        else if (current == getExclusiveOwnerThread()) {
            int nextc = c + acquires;
            if (nextc < 0) // overflow
                throw new Error("Maximum lock count exceeded");
            setState(nextc);
            return true;
        }
        return false;
    }
}
```

对比代码可以看出，实现公平性的方式主要就是`hasQueuedPredecessors()` 这个方法，它用来返回队列中是否有比当前线程等待更久的线程。这也是和下面说的NonfairSync的唯一区别的地方。

下面举一个公平锁和非公平锁的例子

```java
public class FairAndUnfairTest {
    private static Lock fairLock = new ReentrantLock2(true);
    private static Lock unfairLock = new ReentrantLock2(false);

    private void testLock(Lock lock) {
        for (int i = 0; i < 5; i++) {
            Job job = new Job(lock);
            job.setName("线程" + (i + 1));
            job.start();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        FairAndUnfairTest test = new FairAndUnfairTest();
//        test.testLock(fairLock);
        test.testLock(unfairLock);
    }

    private static class Job extends Thread {
        private Lock lock;
        public Job(Lock lock) {
            this.lock = lock;
        }

        @Override
        public void run() {
            for (int i = 0; i < 2; i++) {
                lock.lock();
                try {
                    Collection<Thread> threads = ((ReentrantLock2)lock).getQueuedThreads();
                    Thread.sleep(1000);
                    System.out.println("获取锁的当前线程[" + Thread.currentThread().getName() + "], 同步队列中的线程" + getThreadName(threads));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private static String getThreadName(Collection<Thread> threads) {
        StringBuilder sb = new StringBuilder();
        if (threads != null) {
            for (Thread thread : threads) {
                sb.append(thread.getName()).append(",");
            }
        }
        String waitingThreads = null;
        if (sb.length() > 0) {
            waitingThreads = sb.substring(0, sb.length() - 1);
        }
        return waitingThreads;
    }

    private static class ReentrantLock2 extends ReentrantLock {
        public ReentrantLock2(boolean fair) {
            super(fair);
        }

        @Override
        public Collection<Thread> getQueuedThreads() {
            List<Thread> arrayList = new ArrayList<>(super.getQueuedThreads());
            Collections.reverse(arrayList);
            return arrayList;
        }
    }
}
```

输出结果为：

```sql
// 打开test.testLock(fairLock)的输出结果
获取锁的当前线程[线程1], 同步队列中的线程null
获取锁的当前线程[线程2], 同步队列中的线程线程3,线程4,线程5,线程1
获取锁的当前线程[线程3], 同步队列中的线程线程4,线程5,线程1,线程2
获取锁的当前线程[线程4], 同步队列中的线程线程5,线程1,线程2,线程3
获取锁的当前线程[线程5], 同步队列中的线程线程1,线程2,线程3,线程4
获取锁的当前线程[线程1], 同步队列中的线程线程2,线程3,线程4,线程5
获取锁的当前线程[线程2], 同步队列中的线程线程3,线程4,线程5
获取锁的当前线程[线程3], 同步队列中的线程线程4,线程5
获取锁的当前线程[线程4], 同步队列中的线程线程5
获取锁的当前线程[线程5], 同步队列中的线程null

// 打开test.testLock(unfairLock)的输出结果
获取锁的当前线程[线程1], 同步队列中的线程null
获取锁的当前线程[线程1], 同步队列中的线程线程2,线程3,线程4,线程5
获取锁的当前线程[线程2], 同步队列中的线程线程3,线程4,线程5
获取锁的当前线程[线程2], 同步队列中的线程线程3,线程4,线程5
获取锁的当前线程[线程3], 同步队列中的线程线程4,线程5
获取锁的当前线程[线程3], 同步队列中的线程线程4,线程5
获取锁的当前线程[线程4], 同步队列中的线程线程5
获取锁的当前线程[线程4], 同步队列中的线程线程5
获取锁的当前线程[线程5], 同步队列中的线程null
获取锁的当前线程[线程5], 同步队列中的线程null
```

显然，试验结果与我们的预期相符。在以非公平锁的方式获取锁，当一个线程在获取锁又释放锁，但又立即获取锁的时候，这个时候这个线程有很大的概率会成功（只是很大概率，试验结果也有可能不连续两次获取锁）。而公平锁则不一样，哪怕是同一个线程连续两次获取锁和释放锁，在第一次获取锁释放锁过后接着准备第二次获取锁时，这个时候当前线程会被加入到同步队列的队尾。

那么，公平锁和非公平锁的性能谁高谁低呢？上面的例子我们可以发现非公平锁的一个线程连续两次获取锁和释放锁的工程中，是没有做上下文切换的，也就是一共只做了5次上下文切换。而公平锁实际上做了10次上下文切换。性能测试的例子可以参考：[【试验局】ReentrantLock中非公平锁与公平锁的性能测试](http://www.cnblogs.com/yulinfeng/p/6899316.html)

> 结论：**非公平锁的性能因其系统上下文的切换较少，其性能一般要优于公平锁。**

## 3.2、lock()

```java
public void lock() {
    sync.lock();
}
```

lock方法很简单，直接调用的AQS的lock方法。sync的lock方法会根据公平性选择不同的lock方法，上面我们看过了FairSync和NonfairSync类，再来重温一下它的方法。

```java
// NonfairSync
final void lock() {
    if (compareAndSetState(0, 1))
        setExclusiveOwnerThread(Thread.currentThread());
    else
        acquire(1);
}

// FairSync
final void lock() {
    acquire(1);
}
```

NonfairSync会判断当前是否有线程持有锁，如果没有，直接获得锁返回。不然会和公平锁一样去调AQS的acquire方法。

```java
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}
```

首先会用tryAcquire方法来判断是否能够获取锁，这个方法也在FairSync和NonfairSync中进行了重写。在3.1节中已经说过了这个方法，这里就不再赘述。

## 3.3、unlock()

```java
public void unlock() {
    sync.release(1);
}
```

和lock一样，unlock也是调用的AQS的release方法，所以我们直接看tryRelease的实现就行。因为该方法公平性和非公平性是一样的，所以直接看Sync里的tryRelease()方法。

```java
protected final boolean tryRelease(int releases) {
    int c = getState() - releases;    // 释放state
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    boolean free = false;
    if (c == 0) {    // 释放成功，返回true
        free = true;
        setExclusiveOwnerThread(null);
    }
    setState(c);
    return free;
}
```

方法很简单，先释放状态，再判断状态是不是0，0的话意味着该线程所有的锁都释放了，返回true，唤醒Sync队列后面的线程来争夺锁。

## 3.4、其他方法解释

- getHoldCount()：查询当前线程保持此锁定的个数，也就是调用lock()方法的次数。
- getQueueLength()：返回正等待获取此锁定的线程估计数。
- getWaitQueueLength()：返回等待与此锁定相关的给定条件Condition的线程估计数。比如有5个线程，每个线程都执行了同一个condition对象的await方法，则调用getWaitQueueLength(Condition condition)方法时返回5。
- hasQueuedThread(Thread thread)：查询指定的线程是否正在等待获取此锁定。
- hasQueueThreads()：查询是否有线程正在等待获取此锁定。
- hasWaiters(Condition condition)：查询是否有线程正在等待与此锁定有关的condition条件。
- isFair()：判断是不是公平锁。
- isHeldByCurrentThread()：查询当前线程是否保持此锁定。
- isLocked()：查询此锁定是否由任意线程保持。
- lockInterruptibly()：如果当前线程未被中断，则获取锁定，如果已经被中断则出现异常。
- tryLock()：仅在调用时锁定未被另一个线程保持的情况下，才获取该锁定。
- tryLock(long timeout, TimeUnit unit)：如果锁定在给定等待时间内没有被另一个线程保持，且当前线程未被中断，则获取该锁定。
- awaitUninterruptibly()：只有当不被中断的时候await，一旦发生中断，不会抛出异常，会直接结束。
- awaitUntil()：等待到指定时间停止等待，期间可以被其他线程提前唤醒。

## 3.5、小结

ReentrantLock里的方法实现要结合AQS来看，主要方法和原理都在AQS中，ReentrantLock只是做了一个简单的实现而已。

# 4、使用

我们可以使用Lock来实现生产和消费。

```java
public class ProducerConsumerTest {
    private Lock lock = new ReentrantLock();
    private Condition condition = lock.newCondition();
    private boolean hasValue = false;

    public void produce() {
        try {
            lock.lock();
            while (hasValue) {
                condition.await();
            }
            System.out.println("生产");
            hasValue = true;
            condition.signal();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    public void consume() {
        try {
            lock.lock();
            while (!hasValue) {
                condition.await();
            }
            System.out.println("消费");
            hasValue = false;
            condition.signal();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        final ProducerConsumerTest producerConsumerTest = new ProducerConsumerTest();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100; i++) {
                    producerConsumerTest.produce();
                }
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 100; i++) {
                    producerConsumerTest.consume();
                }
            }
        }).start();
    }
}
```