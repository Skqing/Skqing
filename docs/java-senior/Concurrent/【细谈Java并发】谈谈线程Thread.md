### 【细谈Java并发】谈谈线程Thread

转载自[【细谈Java并发】谈谈线程Thread](https://benjaminwhx.com/2018/05/19/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88%E7%BA%BF%E7%A8%8BThread/)

# 1、线程与多线程

线程（Thread）是一个对象（Object）。用来干什么？Java 线程（也称 JVM 线程）是 Java 进程内允许多个同时进行的任务（这里说的同时进行并不代表同一时间并不代表真的有多个线程在一起执行，单核的计算机同一时间只能有一个线程在运行，只不过CPU切换的速度很快，让人们以为是同时运行而已），该进程内并发的任务成为线程（Thread），一个进程里至少一个线程。

那为什么使用多线程？

- 适合多核处理器。一个线程运行在一个处理器核心上，那么多线程可以分配到多个处理器核心上，更好地利用多核处理器。
- 防止阻塞。将数据一致性不强的操作使用多线程技术（或者消息队列）加快代码逻辑处理，缩短响应时间。

聊到多线程，多半会聊并发与并行，咋理解并区分这两个的区别呢？

- 类似单个 CPU ，通过 CPU 调度算法等，处理多个任务的能力，叫并发，并发是在同一实体上的多个事件。
- 类似多个 CPU ，同时并且处理相同多个任务的能力，叫做并行，并行是在不同实体上的多个事件。

# 2、线程的运行与创建

线程表示一条单独的执行流，它有自己的程序执行计数器，有自己的栈。在Java中创建线程对象有两种方法：

- 继承 Thread 类创建线程对象
- 实现 Runnable 接口类创建线程对象

创建完线程后，我们可以通过调用 `start` 方法来启动线程，那么 `start` 方法和 `run` 方法有什么区别呢？

> start表示启动该线程，使其成为一条单独的执行流，背后，操作系统会分配线程相关的资源，每个线程会有单独的程序执行计数器和栈，操作系统会把这个线程作为一个独立的个体进行调度，分配时间片让它执行，执行的起点就是run方法。而run表示在创建线程的主线程中执行任务。

```java
public synchronized void start() {
    if (threadStatus != 0)
        throw new IllegalThreadStateException();

    boolean started = false;
    try {
        start0();
        started = true;
    } finally {
        try {
            if (!started) {
                group.threadStartFailed(this);
            }
        } catch (Throwable ignore) {}
    }
}

private native void start0();
```

那么怎么确认代码是在哪个线程中执行的呢？Thread有一个静态方法currentThread能够获取到当前代码执行的线程，再通过 `getId()` 和 `getName()` 可以获取到线程的id和名称。id是一个递增的整数，每创建一个线程就加一，name的默认值是`Thread-` 后跟一个编号，name可以在Thread的构造方法中进行指定，也可以通过setName方法进行设置，给Thread设置一个友好的名字，可以方便调试。
​
线程中还有一个优先级的概念，在Java中，优先级从1到10，默认为5。高优先级的线程总是大部分先执行完，但不代表高优先级的线程全部先执行完。
​
不要把线程的优先级与运行结果的顺序作为衡量的标准，优先级较高的线程并不一定每一次都先执行完run方法中的任务，也就是说线程优先级与打印顺序无关，不要将这两者的关系相关联，它们的关系具有不确定性和随机性。

```java
public final void setPriority(int newPriority){...};
public final int getPriority(){...};
```

# 3、线程的状态

线程的状态实现通过 Thread.State 常量类实现，有 6 种线程状态：new（新建）、runnnable（可运行）、blocked（阻塞）、waiting（等待）、time waiting （定时等待）和 terminated（终止）。状态转换图如下：

![img](https://benjaminwhx.com/images/threadState.png)

线程状态流程大致如下：

- 线程创建后，进入 new 状态。
- 调用 start 或者 run 方法，进入 runnable 状态
- JVM 按照线程优先级及时间分片等执行 runnable 状态的线程。开始执行时，进入 running 状态
- 如果线程执行 sleep、wait、join，或者进入 IO 阻塞等。进入 wait 或者 blocked 状态。
- 线程执行完毕后，线程被线程队列移除。最后为 terminated 状态。

这里会发现，Thread用了3个状态来代表阻塞的情况，下面我们就来说说阻塞方法 `interrupt`。

# 4、线程的中断

Thread提供了4个方法来提供中断标志位设置、中断状态查询等功能。

## 4.1、什么是中断线程？

线程的 `thread.interrupt()` 方法是中断线程，将会设置该线程的中断状态位，即设置为true，中断的结果线程是死亡、还是等待新的任务或是继续运行至下一步，就取决于这个程序本身。线程会不时地检测这个中断标示位，以判断线程是否应该被中断（中断标示值是否为true）。它并不像stop方法那样会中断一个正在运行的线程。

判断某个线程是否已被发送过中断请求，请使用 `Thread.currentThread().isInterrupted()` 方法（因为它将线程中断标示位设置为true后，不会立刻清除中断标示位，即不会将中断标设置为false），而不要使用 `thread.interrupted()`（该方法调用后会将中断标示位清除，即重新设置为false）方法来判断，下面是线程在循环中时的中断方式：

```java
while(!Thread.currentThread().isInterrupted() && more work to do){
    do more work
}
```

## 4.2、源码分析

下面分析一下各个中断方法的源码：

```java
// 中断主方法
public void interrupt() {
    if (this != Thread.currentThread())
        checkAccess();

    synchronized (blockerLock) {
        Interruptible b = blocker;
        if (b != null) {
            interrupt0();           // 设置中断标识位
            b.interrupt(this);
            return;
        }
    }
    interrupt0();
}

public static boolean interrupted() {
    // 返回当前线程是否被中断，会重置标识位
    return currentThread().isInterrupted(true);
}

public boolean isInterrupted() {
    // 返回当前线程是否被中断，不会重置标识位
    return isInterrupted(false);
}

/**
 * 返回当前线程是否被中断
 * 根据传入的ClearInterrupted来判断要不要清除中断标识位
 */
private native boolean isInterrupted(boolean ClearInterrupted);
```

## 4.3、中断应用

首先，我们先来想一下，如何停止线程？一般来说，我们有3种方式来实现：

1. 使用退出标志，使线程正常退出，也就是当run方法完成后线程终止。
2. 使用stop方法强制终止线程，但是不推荐使用这个方法，因为stop和suspend及resume一样，都是作废过期的方法，使用它们可能产生不可预料的结果。
3. 使用interrupt方法中断线程。

### 4.3.1、使用中断信号量中断非阻塞状态的线程

中断线程最好的，最受推荐的方式是，使用共享变量（shared variable）发出信号，告诉线程必须停止正在运行的任务。线程必须周期性的核查这一变量，然后有秩序地中止任务。Example2描述了这一方式：

```java
class Example2 extends Thread {
    volatile boolean stop = false;// 线程中断信号量

    public static void main(String args[]) throws Exception {
        Example2 thread = new Example2();
        System.out.println("Starting thread...");
        thread.start();
        Thread.sleep(3000);
        System.out.println("Asking thread to stop...");
        // 设置中断信号量
        thread.stop = true;
        Thread.sleep(3000);
        System.out.println("Stopping application...");
    }

    public void run() {
        // 每隔一秒检测一下中断信号量
        while (!stop) {
            System.out.println("Thread is running...");
            long time = System.currentTimeMillis();
            /*
             * 使用while循环模拟 sleep 方法，这里不要使用sleep，否则在阻塞时会 抛
             * InterruptedException异常而退出循环，这样while检测stop条件就不会执行，
             * 失去了意义。
             */
            while ((System.currentTimeMillis() - time < 1000)) {}
        }
        System.out.println("Thread exiting under request...");
    }
}
```

### 4.3.2、使用thread.interrupt()中断非阻塞状态线程

虽然Example2该方法要求一些编码，但并不难实现。同时，它给予线程机会进行必要的清理工作。这里需注意一点的是需将共享变量定义成 `volatile` 类型或将对它的一切访问封入同步的块/方法（synchronized blocks/methods）中。上面是中断一个非阻塞状态的线程的常见做法，但对非检测 `isInterrupted()` 条件会更简洁:

```java
class Example2 extends Thread {
    public static void main(String args[]) throws Exception {
        Example2 thread = new Example2();
        System.out.println("Starting thread...");
        thread.start();
        Thread.sleep(3000);
        System.out.println("Asking thread to stop...");
        // 发出中断请求
        thread.interrupt();
        Thread.sleep(3000);
        System.out.println("Stopping application...");
    }

    public void run() {
        // 每隔一秒检测是否设置了中断标示
        while (!Thread.currentThread().isInterrupted()) {
            System.out.println("Thread is running...");
            long time = System.currentTimeMillis();
            // 使用while循环模拟 sleep
            while ((System.currentTimeMillis() - time < 1000) ) {
            }
        }
        System.out.println("Thread exiting under request...");
    }
}
```

### 4.3.3、使用thread.interrupt()中断阻塞状态线程

`Thread.interrupt()` 方法不会中断一个正在运行的线程。这一方法实际上完成的是，设置线程的中断标示位，在线程受到阻塞的地方（如调用sleep、wait、join等地方）抛出一个异常InterruptedException，并且中断状态也将被清除，这样线程就得以退出阻塞的状态。下面是具体实现：

```java
class Example3 extends Thread {
    public static void main(String args[]) throws Exception {
        Example3 thread = new Example3();
        System.out.println("Starting thread...");
        thread.start();
        Thread.sleep(3000);
        System.out.println("Asking thread to stop...");
        thread.interrupt();// 等中断信号量设置后再调用
        Thread.sleep(3000);
        System.out.println("Stopping application...");
    }

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            System.out.println("Thread running...");
            try {
                /*
                 * 如果线程阻塞，将不会去检查中断信号量stop变量，所 以thread.interrupt()
                 * 会使阻塞线程从阻塞的地方抛出异常，让阻塞线程从阻塞状态逃离出来，并
                 * 进行异常块进行 相应的处理
                 */
                Thread.sleep(1000);// 线程阻塞，如果线程收到中断操作信号将抛出异常
            } catch (InterruptedException e) {
                System.out.println("Thread interrupted...");
                /*
                 * 如果线程在调用 Object.wait()方法，或者该类的 join() 、sleep()方法
                 * 过程中受阻，则其中断状态将被清除
                 */
                System.out.println(this.isInterrupted());// false

                //中不中断由自己决定，如果需要真真中断线程，则需要重新设置中断位，如果
                //不需要，则不用调用
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Thread exiting under request...");
    }
}
```

> 总结来说，如果需要中断的代码块里面没有会让线程阻塞的地方，最好使用共享变量的方式来让线程退出，否则推荐使用3.3所说的方式。

# 5、其他的一些方法

## 5.1、sleep

Sleep意味着线程主动告诉操作系统自己要休息 n 毫秒。

1. **Thread.sleep(0)** 进入就绪状态：如果n=0时，意味着当前线程的时间片没有用完，主动放弃自己剩下的时间片，进入就绪状态。这种情况下只能调度优先级相等或更高的线程，意味着优先级低的线程很难获得时间片，很可能永远都调用不到。当没有符合条件的线程，会一直占用 CPU 时间片，造成 CPU 100%占用率。
2. **Thread.sleep(1)** 进入阻塞状态：如果n>0，会强制当前线程放弃剩余时间片，并休息n秒（因为不是实时操作系统，时间无法保证精确，一般可能会滞后几毫秒或一个时间片），进入阻塞状态。这种情况下所有其它就绪状态的线程都有机会竞争时间片，而不用在乎优先级。无论有没有符合的线程，都会放弃 CPU 时间，因此 CPU 占用率较低。

## 5.2、yield

Yield 的中文翻译为 “让步，让位”，这里意思是当前线程主动让出时间片，并让操作系统调度其它就绪态的线程使用时间片。

1. 如果调用 Yield，只是把当前线程放入到就绪队列中，而不是阻塞队列
2. 如果没有找到其它就绪态的线程，则当前线程继续运行
3. 比 Thread.Sleep(0) 速度要快，可以让低于当前优先级的线程得以运行

## 5.3、守护线程

守护线程是一种特殊的线程，它的特性有陪伴的含义，当线程中不存在非守护线程了，则守护线程自动销毁。典型的守护线程就是垃圾回收线程。

```java
public final void setDaemon(boolean on);
public final boolean isDaemon();
```

## 5.4、join

join的作用是等待线程对象销毁：原理就是一直检查线程的isAlive状态。方法join(long)的功能在内部是使用wait(long)方法来实现的，所以join(long)方法具有释放锁的特点。

```java
public final synchronized void join(long millis) throws InterruptedException {
    long base = System.currentTimeMillis();
        long now = 0;

        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (millis == 0) {
            while (isAlive()) {
                wait(0);
            }
        } else {
            // 一直检查是否alive，如果不是alive，说明线程执行完成了
            while (isAlive()) {
                long delay = millis - now;
                if (delay <= 0) {
                    break;
                }
                wait(delay);
                now = System.currentTimeMillis() - base;
            }
        }
}

public final synchronized void join(long millis, int nanos);

public final void join() throws InterruptedException {
    join(0);
}
```