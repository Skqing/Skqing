### 【细谈Java并发】谈谈CyclicBarrier

转载自[【细谈Java并发】谈谈CyclicBarrier](https://benjaminwhx.com/2018/05/03/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88CyclicBarrier/)

# 1、简介

CyclicBarrier是一个同步工具类，它允许一组线程在到达某个栅栏点(common barrier point)互相等待，发生阻塞，直到最后一个线程到达栅栏点，栅栏才会打开，处于阻塞状态的线程恢复继续执行.它非常适用于一组线程之间必需经常互相等待的情况。CyclicBarrier字面理解是循环的栅栏，之所以称之为循环的是因为在等待线程释放后，该栅栏还可以复用。

![img](https://benjaminwhx.com/images/cyclicbarrier1.png)

建议阅读CyclicBarrier源码前，先深入研究一下ReentrantLock的原理，搞清楚condition里await和signal的原理，这部分可以看我之前的文章：[【细谈Java并发】谈谈AQS](http://benjaminwhx.com/2018/04/30/【细谈Java并发】谈谈AQS/)、 [【细谈Java并发】谈谈ReentrantLock](http://benjaminwhx.com/2018/05/02/【细谈Java并发】谈谈ReentrantLock/)

好了，我们来看看如何使用它吧。

# 2、使用场景

我们可以简单使用CyclicBarrier来模拟一下对战平台中玩家需要完全准备好了,才能进入游戏的场景。

```java
public class CyclicBarrierTest {

    private final static ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(5);
    private final static CyclicBarrier BARRIER = new CyclicBarrier(5);

    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) {
            final String name = "玩家" + i;
            EXECUTOR_SERVICE.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2000);
                        System.out.println(name + "已准备,等待其他玩家准备...");
                        BARRIER.await();
                        Thread.sleep(1000);
                        System.out.println(name + "已加入游戏");
                    } catch (InterruptedException e) {
                        System.out.println(name + "离开游戏");
                    } catch (BrokenBarrierException e) {
                        System.out.println(name + "离开游戏");
                    }
                }
            });
        }
        EXECUTOR_SERVICE.shutdown();
    }
}
```

输出结果

```java
玩家1已准备,等待其他玩家准备...
玩家0已准备,等待其他玩家准备...
玩家2已准备,等待其他玩家准备...
玩家3已准备,等待其他玩家准备...
玩家4已准备,等待其他玩家准备...
玩家2已加入游戏
玩家3已加入游戏
玩家4已加入游戏
玩家0已加入游戏
玩家1已加入游戏
```

# 3、原理分析

## 3.1、属性

首先看看它里面的所有属性。

```java
public class CyclicBarrier {
    private static class Generation {
        boolean broken = false;
    }
    // 锁
    private final ReentrantLock lock = new ReentrantLock();
    // 通过lock得到的一个状态变量，用来await和signal
    private final Condition trip = lock.newCondition();
    // 通过构造器传入的参数，表示总的等待线程的数量
    private final int parties;
    // 当屏障正常打开后运行的程序，通过最后一个调用await的线程来执行
    private final Runnable barrierCommand;
    // 当前的Generation。每当屏障失效或者开闸之后都会自动替换掉。从而实现重置的功能
    private Generation generation = new Generation();
    // 和parties一样，每次线程await后减1
    private int count;
    ...省略后面代码
}
```

## 3.2、构造方法

```java
public CyclicBarrier(int parties, Runnable barrierAction) {
    if (parties <= 0) throw new IllegalArgumentException();
    this.parties = parties;
    this.count = parties;
    this.barrierCommand = barrierAction;
}

public CyclicBarrier(int parties) {
    this(parties, null);
}
```

1. 默认的构造方法是CyclicBarrier(int parties)，其参数表示屏障拦截的线程数量，每个线程调用await方法告诉CyclicBarrier已经到达屏障位置，线程被阻塞。
2. 另外一个构造方法CyclicBarrier(int parties, Runnable barrierAction)，其中barrierAction任务会在所有线程到达屏障后执行。

## 3.3、await()

最主要的方法就是await()方法，调用await()的线程会等待直到有足够数量的线程调用await——也就是开闸状态。

```java
public int await() throws InterruptedException, BrokenBarrierException {
    try {
        return dowait(false, 0L);
    } catch (TimeoutException toe) {
        throw new Error(toe); // cannot happen
    }
}

public int await(long timeout, TimeUnit unit)
        throws InterruptedException,
        BrokenBarrierException,
        TimeoutException {
    return dowait(true, unit.toNanos(timeout));
}
```

await()和await(long, TimeUnit)都是调用dowait方法，区别就是参数不同，我们来看看dowait方法。

```java
private int dowait(boolean timed, long nanos)
        throws InterruptedException, BrokenBarrierException,
        TimeoutException {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        final Generation g = generation;

        if (g.broken)    // 如果当前Generation是处于打破状态则传播这个BrokenBarrierExcption
            throw new BrokenBarrierException();

        if (Thread.interrupted()) {
            // 如果当前线程被中断则使得当前generation处于打破状态，重置剩余count。
            // 并且唤醒状态变量。这时候其他线程会传播BrokenBarrierException。
            breakBarrier();
            throw new InterruptedException();
        }

        int index = --count;    // 尝试降低当前count
        /**
         * 如果当前状态将为0，则Generation处于开闸状态。运行可能存在的command，
         * 设置下一个Generation。相当于每次开闸之后都进行了一次reset。
         */
        if (index == 0) {  // tripped
            boolean ranAction = false;
            try {
                final Runnable command = barrierCommand;
                if (command != null)
                    command.run();
                ranAction = true;
                nextGeneration();
                return 0;
            } finally {
                if (!ranAction)    // 如果运行command失败也会导致当前屏障被打破。
                    breakBarrier();
            }
        }

        // loop until tripped, broken, interrupted, or timed out
        for (;;) {
            try {
                if (!timed)    // 阻塞在当前的状态变量。
                    trip.await();
                else if (nanos > 0L)
                    nanos = trip.awaitNanos(nanos);
            } catch (InterruptedException ie) {
                if (g == generation && ! g.broken) {    // 如果当前线程被中断了则使得屏障被打破。并抛出异常。
                    breakBarrier();
                    throw ie;
                } else {
                    Thread.currentThread().interrupt();
                }
            }

            // 从阻塞恢复之后，需要重新判断当前的状态。
            if (g.broken)
                throw new BrokenBarrierException();

            if (g != generation)
                return index;

            if (timed && nanos <= 0L) {
                breakBarrier();
                throw new TimeoutException();
            }
        }
    } finally {
        lock.unlock();
    }
}
```

此外再看下两个小过程：

这两个小过程当然是需要锁的，但是由于这两个方法只是通过其他方法调用，所以依然是在持有锁的范围内运行的。这两个方法都是对域进行操作。

nextGeneration实际上在屏障开闸之后重置状态。以待下一次调用。
breakBarrier实际上是在屏障打破之后设定打破状态，以唤醒其他线程并通知。

```java
private void nextGeneration() {
    trip.signalAll();
    count = parties;
    generation = new Generation();
}

private void breakBarrier() {
    generation.broken = true;
    count = parties;
    trip.signalAll();
}
```

## 3.4、reset

reset方法比较简单。但是这里还是要注意一下要先打破当前屏蔽，然后再重建一个新的屏蔽。否则的话可能会导致信号丢失。

```java
public void reset() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        breakBarrier();   // break the current generation
        nextGeneration(); // start a new generation
    } finally {
        lock.unlock();
    }
}
```

# 4、CountDownLatch的区别

我用白话说的通俗点吧。

1. CountDownLatch的使用是一次性的，而CyclicBarrier可以用reset进行重用。
2. CountDownLatch是一个线程等待多个线程执行完了，再进行执行。而CyclicBarrier是多个线程等待所有线程都执行完了，再进行执行。