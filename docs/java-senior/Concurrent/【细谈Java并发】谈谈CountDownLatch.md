【细谈Java并发】谈谈CountDownLatch

转载自[【细谈Java并发】谈谈CountDownLatch](https://benjaminwhx.com/2018/05/03/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88CountDownLatch/)

# 1、简介

CountDownLatch也叫闭锁，它是J.U.C包中基于AQS实现的一个很简单的类，它允许一个或多个线程等待其他线程完成操作后再执行。

建议阅读CountDownLatch源码前，先深入研究一下AQS的原理，搞清楚什么是独占锁，什么是共享锁。这部分可以看我之前的文章：[【细谈Java并发】谈谈AQS](./%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88AQS/)。

CountDownLatch内部会维护一个资源数量为初始化值为的计数器，当A线程调用await方法后，A线程会在计数器大于0的时候一直阻塞等待。当一个线程完成任务后，计数器的值会减1。当计数器变为0时，表示所有的线程已经完成任务，等待的主线程被唤醒继续执行。

![img](https://benjaminwhx.com/images/countdownlatch1.png)

# 2、使用场景

在一些应用场合中，需要等待某个条件达到要求后才能做后面的事情。比如：主线程需要等待所有子线程处理完任务后需要拿到返回值继续执行，这时候就用到了CountDownLatch。

```java
public class CountDownLatchTest {

    private final static CountDownLatch countDownLatch = new CountDownLatch(5);
    private final static ExecutorService executorService = Executors.newFixedThreadPool(5);

    public static void main(String[] args) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 模拟执行任务
                        Thread.sleep(1000);
                        System.out.println(Thread.currentThread().getName() + "执行完任务");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
        }
        countDownLatch.await();
        System.out.println("主线程等待子线程执行任务完毕，继续执行");
    }
}
```

输出结果：

```java
pool-1-thread-1执行完任务
pool-1-thread-5执行完任务
pool-1-thread-2执行完任务
pool-1-thread-4执行完任务
pool-1-thread-3执行完任务
主线程等待子线程执行任务完毕，继续执行
```

# 3、原理分析

上面的例子里，我们首先构造的时候传递了5个资源数量，并在主线程进行await，而每个子线程执行完了调用countDown方法，我们来看看这三个方法。

```java
public CountDownLatch(int count) {
    if (count < 0) throw new IllegalArgumentException("count < 0");
    this.sync = new Sync(count);
}

public void await() throws InterruptedException {
    sync.acquireSharedInterruptibly(1);
}

public void countDown() {
    sync.releaseShared(1);
}
```

这几个方法啥都没做，所有的处理都在Sync这个类里，我们来看看这个AQS的子类吧。

```java
private static final class Sync extends AbstractQueuedSynchronizer {
    private static final long serialVersionUID = 4982264981922014374L;

    Sync(int count) {
        setState(count);
    }

    int getCount() {
        return getState();
    }

    protected int tryAcquireShared(int acquires) {
        // 只有资源变为0才会获取到锁，否则进入队列阻塞等待
        return (getState() == 0) ? 1 : -1;
    }

    protected boolean tryReleaseShared(int releases) {
        // Decrement count; signal when transition to zero
        for (;;) {
            int c = getState();
            if (c == 0)
                return false;
            int nextc = c-1;
            if (compareAndSetState(c, nextc))
                return nextc == 0;
        }
    }
}
```

因为构造方法里面我们设置了资源值，所以在await的时候会调用tryAcquireShared返回-1进行阻塞等待。

而countDown方法则每次调用tryReleaseShared(1)进行资源-1的操作，当资源变为0时，唤醒Sync队列里的节点进行资源获取的操作，从而让阻塞的主线程又活跃起来。