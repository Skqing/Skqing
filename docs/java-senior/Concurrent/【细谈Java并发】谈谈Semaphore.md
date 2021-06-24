### 【细谈Java并发】谈谈Semaphore

转载自[【细谈Java并发】谈谈Semaphore](https://benjaminwhx.com/2018/05/03/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88Semaphore/)

# 1、简介

在Java中，使用了synchronized关键字和Lock锁实现了资源的并发访问控制，在同一时间只允许唯一了线程进入临界区访问资源(读锁除外)，这样子控制的主要目的是为了解决多个线程并发同一资源造成的数据不一致的问题。在另外一种场景下，一个资源有多个副本可供同时使用，比如打印机房有多个打印机、厕所有多个坑可供同时使用，这种情况下，Java提供了另外的并发访问控制–资源的多副本的并发访问控制，今天学习的信号量Semaphore即是其中的一种。

# 2、使用场景

假若一个工厂有5台机器，但是有8个工人，一台机器同时只能被一个工人使用，只有使用完了，其他工人才能继续使用。那么我们就可以通过Semaphore来实现：

```java
public class SemaphoreTest {

    // 初始化5个资源（机器）
    private final static Semaphore SEMAPHORE = new Semaphore(5);

    public static void main(String[] args) {
        // 8个工人争夺资源
        for (int i = 0; i < 8; i++) {
            final String name = "工人" + i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        SEMAPHORE.acquire();
                        System.out.println(name + "占用一个机器在生产...");
                        Thread.sleep(2000);
                        System.out.println(name + "释放出机器");
                        SEMAPHORE.release();
                    } catch (InterruptedException e) {
                        System.out.println(name + "争夺机器失败");
                    }
                }
            }).start();
        }
    }
}
```

输出结果

```java
工人0占用一个机器在生产...
工人4占用一个机器在生产...
工人3占用一个机器在生产...
工人2占用一个机器在生产...
工人1占用一个机器在生产...
工人4释放出机器
工人2释放出机器
工人0释放出机器
工人6占用一个机器在生产...
工人3释放出机器
工人1释放出机器
工人7占用一个机器在生产...
工人5占用一个机器在生产...
工人7释放出机器
工人6释放出机器
工人5释放出机器
```

# 3、原理分析

我们会先从构造函数开始到AQS的子类Sync开始分析，接着分析NonfairSync和FairSync，以及类里的重要方法，一步步的揭开它神秘的面纱。

建议阅读本文之前先熟悉AQS的原理，关于AQS可以移步：[【细谈Java并发】谈谈AQS](./%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88AQS/)

## 3.1、构造函数

```java
public Semaphore(int permits) {
    sync = new NonfairSync(permits);
}

public Semaphore(int permits, boolean fair) {
    sync = fair ? new FairSync(permits) : new NonfairSync(permits);
}
```

这个构造函数和ReentrantLock很相似，默认都是非公平锁，唯一的区别就是Semaphore需要提供一个默认permits。

## 3.2、Sync

```java
abstract static class Sync extends AbstractQueuedSynchronizer {
    private static final long serialVersionUID = 1192457210091910933L;

    Sync(int permits) {
        setState(permits);
    }

    final int getPermits() {
        return getState();
    }

    // release会调用
    protected final boolean tryReleaseShared(int releases) {
        for (;;) {    // 自旋CAS
            int current = getState();
            // 释放资源，增加state值，并进行CAS替换
            int next = current + releases;
            if (next < current) // overflow
                throw new Error("Maximum permit count exceeded");
            if (compareAndSetState(current, next))
                return true;
        }
    }

    // 减少许可值
    final void reducePermits(int reductions) {
        for (;;) {    // 自旋CAS
            int current = getState();
            // 减少资源的state值，并进行CAS替换
            int next = current - reductions;
            if (next > current) // underflow
                throw new Error("Permit count underflow");
            if (compareAndSetState(current, next))
                return;
        }
    }

    // 清空许可值
    final int drainPermits() {
        for (;;) {
            int current = getState();
            if (current == 0 || compareAndSetState(current, 0))
                return current;
        }
    }
}
```

Sync里的方法都很简单，都是通过自旋CAS来设置许可值。

### NonfairSync和FairSync

```java
static final class NonfairSync extends Sync {
    private static final long serialVersionUID = -2694183684443567898L;

    NonfairSync(int permits) {
        super(permits);
    }

    /**
     * 可用资源 < 0 直接返回
     * 可用资源 >= 0 CAS替换
     */
    protected int tryAcquireShared(int acquires) {
        for (;;) {
            int available = getState();
            int remaining = available - acquires;
            if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                return remaining;
        }
    }
}

static final class FairSync extends Sync {
    private static final long serialVersionUID = 2014338818796000944L;

    FairSync(int permits) {
        super(permits);
    }

    /**
     * Sync队列前有
     * 可用资源 < 0 直接返回
     * 可用资源 >= 0 CAS替换
     */
    protected int tryAcquireShared(int acquires) {
        for (;;) {
            if (hasQueuedPredecessors())
                return -1;
            int available = getState();
            int remaining = available - acquires;
            if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                return remaining;
        }
    }
}
```

实现公平性的主要就是`hasQueuedPredecessors()` 这个方法，它用来返回队列中是否有比当前线程等待更久的线程。这也是和NonfairSync的唯一区别的地方。

## 3.3、acquire相关方法

```java
// 共享阻塞获取资源，interrupt后抛出异常
public void acquire() throws InterruptedException {
    sync.acquireSharedInterruptibly(1);
}

// 共享获取资源，interrupt后不会抛出异常
public void acquireUninterruptibly() {
    sync.acquireShared(1);
}

// 共享阻塞获取资源，interrupt后抛出异常，可以指定获取的资源值
public void acquire(int permits) throws InterruptedException {
    if (permits < 0) throw new IllegalArgumentException();
    sync.acquireSharedInterruptibly(permits);
}

// 共享获取资源，interrupt后不会抛出异常，可以指定获取的资源值
public void acquireUninterruptibly(int permits) {
    if (permits < 0) throw new IllegalArgumentException();
    sync.acquireShared(permits);
}

// 尝试获取资源，如果能够获取到返回true，否则返回false
public boolean tryAcquire() {
    return sync.nonfairTryAcquireShared(1) >= 0;
}

// 在timeout里尝试获取资源，如果能够获取到返回true，否则返回false
public boolean tryAcquire(long timeout, TimeUnit unit)
        throws InterruptedException {
    return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
}

// 尝试获取指定资源，如果能够获取到返回true，否则返回false
public boolean tryAcquire(int permits) {
    if (permits < 0) throw new IllegalArgumentException();
    return sync.nonfairTryAcquireShared(permits) >= 0;
}

// 在timeout里尝试获取指定资源，如果能够获取到返回true，否则返回false
public boolean tryAcquire(int permits, long timeout, TimeUnit unit)
        throws InterruptedException {
    if (permits < 0) throw new IllegalArgumentException();
    return sync.tryAcquireSharedNanos(permits, unit.toNanos(timeout));
}
```

## 3.4、release相关方法

```java
// 共享释放资源
public void release() {
    sync.releaseShared(1);
}

// 共享释放指定资源
public void release(int permits) {
    if (permits < 0) throw new IllegalArgumentException();
    sync.releaseShared(permits);
}
```

## 3.5、其他方法

```java
// 获取可用资源
public int availablePermits() {
    return sync.getPermits();
}

// 清空资源值
public int drainPermits() {
    return sync.drainPermits();
}

// 减少指定资源值
protected void reducePermits(int reduction) {
    if (reduction < 0) throw new IllegalArgumentException();
    sync.reducePermits(reduction);
}
```