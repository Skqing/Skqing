【细谈Java并发】谈谈线程池：ThreadPoolExecutor

转载自[【细谈Java并发】谈谈线程池：ThreadPoolExecutor](https://benjaminwhx.com/2018/04/29/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88%E7%BA%BF%E7%A8%8B%E6%B1%A0%EF%BC%9AThreadPoolExecutor/)

## 1、线程池介绍

在web开发中，服务器需要接受并处理请求，所以会为一个请求来分配一个线程来进行处理。如果每次请求都新创建一个线程的话实现起来非常简便，但是存在一个问题：

> 如果并发的请求数量非常多，但每个线程执行的时间很短，这样就会频繁的创建和销毁线程，如此一来会大大降低系统的效率。可能出现服务器在为每个请求创建新线程和销毁线程上花费的时间和消耗的系统资源要比处理实际的用户请求的时间和资源更多。

所以线程池就出现了。线程池为线程生命周期的开销和资源不足问题提供了解决方案。通过对多个任务重用线程，线程创建的开销被分摊到了多个任务上。

使用线程池的好处：

- 降低资源消耗。通过重复利用已创建的线程降低线程创建和销毁造成的消耗。
- 提高响应速度。当任务到达时，任务可以不需要等到线程创建就能立即执行。
- 提高线程的可管理性。线程是稀缺资源，如果无限制的创建，不仅会消耗系统资源，还会降低系统的稳定性，使用线程池可以进行统一的分配，调优和监控。

Java中的线程池是用ThreadPoolExecutor类来实现的. 本文就结合JDK 1.8的源码来分析一下这个类内部对于线程的创建, 管理以及后台任务的调度等方面的执行原理。

## 2、继承关系

我们首先来看一下线程池的类图：
![img](https://benjaminwhx.com/images/threadPoolExecutor1.png)

**Executor接口**

```java
public interface Executor {
    /**
     * 在将来的某个时候执行传入的命令，执行命令可以在实现类里通过新创建的线程、线程池、当前线程来完成。
     */
    void execute(Runnable command);
}
```

**ExecutorService接口**

```java
public interface ExecutorService extends Executor {

    /**
     * 启动先前提交的任务被执行的有序关闭，但不接受新的任务。 如果已经关闭，则调用没有其他影响。
     */
    void shutdown();

    /**
     * 尝试停止所有正在执行的任务，停止等待任务的处理，并返回正在等待执行的任务的列表。
     * 该方法不能等待之前提交的任务执行完，如果需要等待执行，可以使用{@link #awaitTermination awaitTermination}
     * 从这个方法返回后，这些任务从任务队列中排出（移除）。 除了竭尽全力地停止处理主动执行任务之外，没有任何保证。 
     */
    List<Runnable> shutdownNow();

    /**
     * 线程池有没有被关闭，关闭返回true，否则false
     */
    boolean isShutdown();

    /**
     * 如果所有任务在关闭后都完成了。返回true
     * 提示：如果没有在调用该方法前调用shutdown或者shutdownNow方法，此方法永远不会返回true
     */
    boolean isTerminated();

    /**
     * 在指定时间内阻塞等待任务全部完成，完成了返回true，否则false
     */
    boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException;

    /**
     * 提交一个有返回值的任务
     */
    <T> Future<T> submit(Callable<T> task);

    /**
     * 提交一个任务来执行，返回一个有返回值的结果，返回值为传入的result
     */
    <T> Future<T> submit(Runnable task, T result);

    /**
     * 提交一个任务来执行，返回一个有返回值的结果，返回值为null
     */
    Future<?> submit(Runnable task);

    /**
     * 执行一批有返回值的任务
     * 返回的结果调用{@link Future#isDone}都是true
     */
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException;
    /**
     * 执行给定的任务，当全部完成或者超时返回一个有状态和结果的Future集合。
     * 返回的结果调用{@link Future#isDone}都是true
     * 返回时，尚未完成的任务将被取消。
     * 如果在进行此操作时修改了给定的集合，则此方法的结果是不确定的。
     */
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                  long timeout, TimeUnit unit)
        throws InterruptedException;

    /**
     * 执行给定的任务，返回一个成功完成任务的结果（即，没有抛出异常），
     * 如果有的话。 在正常或异常返回时，尚未完成的任务将被取消。 
     * 如果在进行此操作时修改了给定的集合，则此方法的结果是不确定的。
     */
    <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException;

    /**
     * 执行给定的任务，返回一个成功完成任务的结果（即，没有抛出异常），
     * 如果有的话。 在正常或异常返回时，尚未完成的任务将被取消。 
     * 如果在进行此操作时修改了给定的集合，则此方法的结果是不确定的。
     * 超时没有成功结果抛出TimeoutException
     */
    <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                    long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
}
```

**AbstractExecutorService接口**

```java
protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
    return new FutureTask<T>(runnable, value);
}

protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
    return new FutureTask<T>(callable);
}

private <T> T doInvokeAny(Collection<? extends Callable<T>> tasks,
                          boolean timed, long nanos)
    throws InterruptedException, ExecutionException, TimeoutException {
    // ...
}
```

## 3、ThreadPoolExecutor分析

想要深入理解ThreadPoolExecutor，就要先理解其中最重要的几个参数：

### 核心变量与方法（状态转换）

```java
// 状态|工作数的一个32bit的值
private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
private static final int COUNT_BITS = Integer.SIZE - 3;
// 0001-1111-1111-1111-1111-1111-1111-1111
private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

// 1110-0000-0000-0000-0000-0000-0000-0000
private static final int RUNNING    = -1 << COUNT_BITS;
// 0000-0000-0000-0000-0000-0000-0000-0000
private static final int SHUTDOWN   =  0 << COUNT_BITS;
// 0010-0000-0000-0000-0000-0000-0000-0000
private static final int STOP       =  1 << COUNT_BITS;
// 0100-0000-0000-0000-0000-0000-0000-0000
private static final int TIDYING    =  2 << COUNT_BITS;
// 0110-0000-0000-0000-0000-0000-0000-0000
private static final int TERMINATED =  3 << COUNT_BITS;

// ~CAPACITY就是前3位状态位，和c进行&就能得到当前的状态
private static int runStateOf(int c)     { return c & ~CAPACITY; }
// 和c进行&就能得到当前的工作数
private static int workerCountOf(int c)  { return c & CAPACITY; }
// rs就是状态值，wc就是工作数，这两个进行或操作，就能得到ctl的值（32bit的值）
private static int ctlOf(int rs, int wc) { return rs | wc; }
```

可能很多人看到上面的写法都蒙圈了。我其实基础也不太好，所以我看到这里的时候索性写了个工具类去测试他们的输出结果，如下：

```java
public class ExecutorTest {
    private final static int COUNT_BITS = Integer.SIZE - 3;
    private final static int RUNNING    = -1 << COUNT_BITS;
    private final static int SHUTDOWN   =  0 << COUNT_BITS;
    private final static int STOP       =  1 << COUNT_BITS;
    private final static int TIDYING    =  2 << COUNT_BITS;
    private final static int TERMINATED =  3 << COUNT_BITS;
    private final static int CAPACITY   = (1 << COUNT_BITS) - 1;

    public static void main(String[] args) {
        System.out.println("状态位===");
        System.out.println(getFormatStr(RUNNING));
        System.out.println(getFormatStr(SHUTDOWN));
        System.out.println(getFormatStr(STOP));
        System.out.println(getFormatStr(TIDYING));
        System.out.println(getFormatStr(TERMINATED));
        System.out.println(getFormatStr(CAPACITY));
    }

    private static String getFormatStr(int n) {
        String integerMaxValueStr = Integer.toBinaryString(n);
        int a = 32;
        StringBuilder sb = new StringBuilder();
        int l = integerMaxValueStr.length();
        int i = 0;
        for (; a > 0; --a) {
            if (--l >= 0) {
                sb.append(integerMaxValueStr.charAt(l));
            } else {
                sb.append("0");
            }
            if (++i % 4 == 0) {
                if (a > 1) {
                    sb.append("-");
                }
                i = 0;
            }
        }
        return sb.reverse().toString();
    }
}
```

输出结果为：

```java
状态位===
1110-0000-0000-0000-0000-0000-0000-0000
0000-0000-0000-0000-0000-0000-0000-0000
0010-0000-0000-0000-0000-0000-0000-0000
0100-0000-0000-0000-0000-0000-0000-0000
0110-0000-0000-0000-0000-0000-0000-0000
0001-1111-1111-1111-1111-1111-1111-1111
```

通过上面的注释以及测试用例可以发现，源码的作者巧妙的运用一个值代表了2种意思（前3bit位是状态，后29bit是工作数），下面我们来看看线程池最重要的5种状态：

1. **RUNNING**：能接受新提交的任务，并且也能处理阻塞队列中的任务；
2. **SHUTDOWN**：关闭状态，不再接受新提交的任务，但却可以继续处理阻塞队列中已保存的任务。在线程池处于 RUNNING 状态时，调用 shutdown()方法会使线程池进入到该状态。（finalize() 方法在执行过程中也会调用shutdown()方法进入该状态）；
3. **STOP**：不能接受新任务，也不处理队列中的任务，会中断正在处理任务的线程。在线程池处于 RUNNING 或 SHUTDOWN 状态时，调用 shutdownNow() 方法会使线程池进入到该状态；
4. **TIDYING**：如果所有的任务都已终止了，workerCount (有效线程数) 为0，线程池进入该状态后会调用 terminated() 方法进入TERMINATED 状态。
5. **TERMINATED**：在terminated() 方法执行完后进入该状态，默认terminated()方法中什么也没有做。

下图为线程池的状态转换过程：
![img](https://benjaminwhx.com/images/threadPoolExecutor2.png)

### 构造方法

```java
/**
 * @param corePoolSize 保留在线程池中的线程数，即使它们处于空闲状态，除非设置了{@code allowCoreThreadTimeOut}
 * @param maximumPoolSize 线程池中允许的最大线程数
 * @param keepAliveTime 当线程数大于corePoolSize时，这是多余空闲线程在终止之前等待新任务的最大时间。
 * @param unit {@code keepAliveTime}参数的时间单位
 * @param workQueue 在执行任务之前用于保存任务的队列。 这个队列将只保存{@code execute}方法提交的{@code Runnable}任务。
 * @param threadFactory 用来执行的时候创建线程的线程工厂
 * @param handler 在执行被阻塞时使用的处理程序，因为达到了线程边界和队列容量
 */
public ThreadPoolExecutor(int corePoolSize,
                          int maximumPoolSize,
                          long keepAliveTime,
                          TimeUnit unit,
                          BlockingQueue<Runnable> workQueue,
                          ThreadFactory threadFactory,
                          RejectedExecutionHandler handler) {
    if (corePoolSize < 0 ||
        maximumPoolSize <= 0 ||
        maximumPoolSize < corePoolSize ||
        keepAliveTime < 0)
        throw new IllegalArgumentException();
    if (workQueue == null || threadFactory == null || handler == null)
        throw new NullPointerException();
    this.corePoolSize = corePoolSize;
    this.maximumPoolSize = maximumPoolSize;
    this.workQueue = workQueue;
    this.keepAliveTime = unit.toNanos(keepAliveTime);
    this.threadFactory = threadFactory;
    this.handler = handler;
}
```

对于参数handler：线程池提供了4种策略：

1. **AbortPolicy**：直接抛出异常，这是默认策略。
2. **CallerRunsPolicy**：用调用者所在的线程来执行任务。
3. **DiscardOldestPolicy**：丢弃阻塞队列中靠最前的任务，并执行当前任务。
4. **DiscardPolicy**：直接丢弃任务。

### 核心方法

**execute方法**

线程池最核心的方法莫过于execute了，execute()方法用来提交任务，下面我们顺着这个方法看看其实现原理：

```java
/**
 * 在未来的某个时刻执行给定的任务。这个任务用一个新线程执行，或者用一个线程池中已经存在的线程执行
 * 如果任务无法被提交执行，要么是因为这个Executor已经被shutdown关闭，要么是已经达到其容量上限，任务会被当前的RejectedExecutionHandler处理
 */
public void execute(Runnable command) {
    if (command == null)
        throw new NullPointerException();
    /*
     * 执行分以下3步：
     *
     * 1. 如果运行的线程少于corePoolSize，尝试开启一个新线程去运行command，command作为这个线程的第一个任务
     *
     * 2. 如果线程入队成功，然后还是要进行double-check的，因为线程池在入队之后状态是可能会发生变化的
     *
     * 3. 如果无法将任务入队列（可能队列满了），需要新开一个线程
     * 如果失败了，说明线程池shutdown 或者 饱和了，所以我们拒绝任务。
     */
    int c = ctl.get();

    /**
     * 1、如果当前线程数少于corePoolSize，开启一个线程执行命令
     *（可能是由于addWorker()操作已经包含对线程池状态的判断，如此处没加，而入workQueue前加了）
     */
    if (workerCountOf(c) < corePoolSize) {
        if (addWorker(command, true))
            return;

        /**
         * 没有成功addWorker()，再次获取c（凡是需要再次用ctl做判断时，都会再次调用ctl.get()）
         * 失败的原因可能是：
         * 1、线程池已经shutdown，shutdown的线程池不再接收新任务
         * 2、workerCountOf(c) < corePoolSize 判断后，由于并发，别的线程先创建了worker线程，导致workerCount>=corePoolSize
         */
        c = ctl.get();
    }

    /**
     * 2、如果线程池RUNNING状态，且入队列成功
     */
    if (isRunning(c) && workQueue.offer(command)) {
        int recheck = ctl.get();//再次校验位

        //如果再次校验过程中，线程池不是RUNNING状态，并且remove(command)--workQueue.remove()成功，拒绝当前command
        if (! isRunning(recheck) && remove(command))
            reject(command);
        else if (workerCountOf(recheck) == 0)
            // 新建一个worker线程，没有指定firstTask，因为命令已经放入queue里了
            addWorker(null, false);
    }
    /**
     * 3、如果线程池不是running状态 或者 无法入队列
     *   尝试开启新线程，扩容至maxPoolSize，如果addWork(command, false)失败了，拒绝当前command
     */
    else if (!addWorker(command, false))
        reject(command);
}
```

在执行execute()方法时如果状态一直是RUNNING时，的执行过程如下：

1. 如果workerCount < corePoolSize，则创建并启动一个线程来执行新提交的任务。
2. 如果workerCount >= corePoolSize，且线程池内的阻塞队列未满，则将任务添加到该阻塞队列中。
3. 如果workerCount >= corePoolSize && workerCount < maximumPoolSize，且线程池内的阻塞队列已满，则创建并启动一个线程来执行新提交的任务。
4. 如果workerCount >= maximumPoolSize，并且线程池内的阻塞队列已满, 则根据拒绝策略来处理该任务, 默认的处理方式是直接抛异常。

**addWorker方法**

addWorker方法的主要工作是在线程池中创建一个新的线程并执行，firstTask参数 用于指定新增的线程执行的第一个任务。core为true表示在新增线程时会判断当前活动线程数是否少于corePoolSize，false表示新增线程前需要判断当前活动线程数是否少于maximumPoolSize，代码如下：

```java
/**
 * 检查是否可以针对当前的池状态和给定的界限（核心或最大值）添加新的工作者。相应地调整工人数量，并且如果可能的话，创建并开始新的工作者，运行firstTask作为其第一个任务。
 */
private boolean addWorker(Runnable firstTask, boolean core) {
    retry:
    for (;;) {
        int c = ctl.get();
        int rs = runStateOf(c);

        /**
         * 只有当下面两种情况会继续执行，其他直接返回false（添加失败）
         * 1、rs == RUNNING
         * 2、rs == SHUTDOWN && firstTask == null && !workQueue.isEmpty() （执行了shutdown方法，但是阻塞队列还有任务没有执行）
         */
        if (rs >= SHUTDOWN &&
            ! (rs == SHUTDOWN &&
               firstTask == null &&
               ! workQueue.isEmpty()))
            return false;

        for (;;) {
            int wc = workerCountOf(c);
            // 当wc超过最大限制 || 如果是核心线程，超过了核心数，否则超过了最大线程数，直接返回false
            if (wc >= CAPACITY ||
                wc >= (core ? corePoolSize : maximumPoolSize))
                return false;
            if (compareAndIncrementWorkerCount(c))
                // count累加成功，直接跳出两层for循环，执行下面的逻辑
                break retry;

            /**
             * 能执行到这里，都是因为多线程竞争，只有两种情况
             * 1、workCount发生变化，compareAndIncrementWorkerCount失败，这种情况不需要重新获取ctl，继续for循环即可。
             * 2、runState发生变化，可能执行了shutdown或者shutdownNow，这种情况重新走retry，取得最新的ctl并判断状态。
             */
            c = ctl.get();  // 重新读取ctl，可能状态发生了变化
            if (runStateOf(c) != rs)
                continue retry;
        }
    }

    boolean workerStarted = false;
    boolean workerAdded = false;
    Worker w = null;
    try {
        w = new Worker(firstTask);
        final Thread t = w.thread;
        if (t != null) {
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // 获取锁后重新检测runState，因为有可能shutdown了
                int rs = runStateOf(ctl.get());

                if (rs < SHUTDOWN ||
                    (rs == SHUTDOWN && firstTask == null)) {
                    if (t.isAlive())
                        // 线程不能是活跃状态
                        throw new IllegalThreadStateException();
                    workers.add(w);
                    int s = workers.size();
                    if (s > largestPoolSize)
                        largestPoolSize = s;    //记录最大线程数
                    workerAdded = true;
                }
            } finally {
                mainLock.unlock();
            }
            if (workerAdded) {
                t.start();
                workerStarted = true;
            }
        }
    } finally {
        if (! workerStarted)
            // 失败回退,从 wokers 移除 w, 线程数减一，尝试结束线程池(调用tryTerminate 方法)
            addWorkerFailed(w);
    }
    return workerStarted;
}
```

注意一下这里的t.start()这个语句，启动时会调用Worker类中的run方法，Worker本身实现了Runnable接口，所以一个Worker类型的对象也是一个线程。

**Worker类**

线程池中的每一个线程被封装成一个Worker对象，而Worker对象继承自AQS，自己实现了锁定的逻辑（AQS相关的内容本文不讲），ThreadPool维护的其实就是一组Worker对象。看一下Worker的定义：

```java
private final class Worker extends AbstractQueuedSynchronizer implements Runnable {
    final Thread thread;
    Runnable firstTask;
    volatile long completedTasks;

    Worker(Runnable firstTask) {
        // 还没有执行任务时，这时就不应该被中断，设置状态为-1
        setState(-1);
        this.firstTask = firstTask;
        this.thread = getThreadFactory().newThread(this);
    }

    public void run() {
        // 调用runWorker方法执行
        runWorker(this);
    }

    // Lock methods
    //
    // 0代表没有锁定状态
    // 1代表锁定状态

    protected boolean isHeldExclusively() {
        return getState() != 0;
    }

    protected boolean tryAcquire(int unused) {
        if (compareAndSetState(0, 1)) {
            setExclusiveOwnerThread(Thread.currentThread());
            return true;
        }
        return false;
    }

    protected boolean tryRelease(int unused) {
        setExclusiveOwnerThread(null);
        setState(0);
        return true;
    }

    public void lock()        { acquire(1); }
    public boolean tryLock()  { return tryAcquire(1); }
    public void unlock()      { release(1); }
    public boolean isLocked() { return isHeldExclusively(); }

    void interruptIfStarted() {
        Thread t;
        if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
            try {
                t.interrupt();
            } catch (SecurityException ignore) {
            }
        }
    }
}
```

Worker继承了AQS，使用AQS来实现独占锁的功能。为什么不使用ReentrantLock来实现呢？可以看到tryAcquire方法，它是不允许重入的，而ReentrantLock是允许重入的：

1. lock方法一旦获取了独占锁，表示当前线程正在执行任务中。
2. 如果正在执行任务，则不应该中断线程。
3. 如果该线程现在不是独占锁的状态，也就是空闲的状态，说明它没有在处理任务，这时可以对该线程进行中断。
4. 线程池在执行shutdown方法或tryTerminate方法时会调用interruptIdleWorkers方法来中断空闲的线程，interruptIdleWorkers方法会使用tryLock方法来判断线程池中的线程是否是空闲状态。
5. 之所以设置为不可重入，是因为我们不希望任务在调用像setCorePoolSize这样的线程池控制方法时重新获取锁。如果使用ReentrantLock，它是可重入的，这样如果在任务中调用了如setCorePoolSize这类线程池控制的方法，会中断正在运行的线程。

所以，Worker用于判断线程是否空闲以及是否可以被中断。

**runWorker方法**

在Worker类中的run方法调用了runWorker方法来执行任务，runWorker方法的代码如下：

```java
final void runWorker(Worker w) {
    Thread wt = Thread.currentThread();
    Runnable task = w.firstTask;
    w.firstTask = null;
    w.unlock(); // 允许中断
    boolean completedAbruptly = true;
    try {
        // 如果task为空，则通过getTask来获取任务
        while (task != null || (task = getTask()) != null) {
            w.lock();   // 开始运行，不允许中断

            /**
             * 确保只有在线程STOP时，才会被设置中断标示，否则清除中断标示
             * 1、如果线程池状态>=STOP，且当前线程没有设置中断状态，wt.interrupt()
             * 2、如果一开始判断线程池状态<STOP，但Thread.interrupted()为true，即线程已经被中断，又清除了中断标示，再次判断线程池状态是否>=STOP
             *  是，再次设置中断标示，wt.interrupt()
             *  否，不做操作，清除中断标示后进行后续步骤
             */
            if ((runStateAtLeast(ctl.get(), STOP) ||
                 (Thread.interrupted() &&
                  runStateAtLeast(ctl.get(), STOP))) &&
                !wt.isInterrupted())
                wt.interrupt();
            try {
                // 用户自己实现
                beforeExecute(wt, task);
                Throwable thrown = null;
                try {
                    // 真正执行任务
                    task.run();
                } catch (RuntimeException x) {
                    thrown = x; throw x;
                } catch (Error x) {
                    thrown = x; throw x;
                } catch (Throwable x) {
                    thrown = x; throw new Error(x);
                } finally {
                    // 用户自己实现
                    afterExecute(task, thrown);
                }
            } finally {
                task = null;
                // worker已经完成的任务数 + 1
                w.completedTasks++;
                w.unlock();
            }
        }
        completedAbruptly = false;
    } finally {
        processWorkerExit(w, completedAbruptly);
    }
}

/**
 * getTask方法用来从阻塞队列中取任务
 * 以下情况会返回null（被回收）
 * 1、超过了maximumPoolSize设置的线程数量（因为调用了setMaximumPoolSize()）
 * 2、线程池被stop
 * 3、线程池被shutdown，并且workQueue空了
 * 4、线程等待任务超时
 * 返回null表示这个worker要结束了，这种情况下workerCount-1
 */
private Runnable getTask() {
    boolean timedOut = false; // 上一次poll()是否超时

    for (;;) {
        int c = ctl.get();
        int rs = runStateOf(c);

        /**
         * 满足以下几点，wc - 1，返回null
         * 1、rs >= STOP
         * 2、rs == SHUTDOWN && workQueue.isEmpty()
         */
        if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
            decrementWorkerCount();
            return null;
        }

        int wc = workerCountOf(c);

        // 队列获取值是否要阻塞等待
        // allowCoreThreadTimeOut默认是false，也就是核心线程不允许进行超时；
        // wc > corePoolSize，表示当前线程池中的线程数量大于核心线程数量；
        // 对于超过核心线程数量的这些线程，需要进行超时控制
        boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

        /**
         * 满足以下几点，wc - 1，返回null
         * 1、wc > maximumPoolSize
         * 2、1 < wc <= maximumPoolSize && timed && timedOut
         * 3、wc <= 1 && workQueue.isEmpty() && timed && timedOut
         */
        if ((wc > maximumPoolSize || (timed && timedOut))
            && (wc > 1 || workQueue.isEmpty())) {
            if (compareAndDecrementWorkerCount(c))
                return null;
            continue;
        }

        try {
            // 获取Runnable
            Runnable r = timed ?
                // 超时会被回收
                workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                // 阻塞等待，默认设置最后最多会有corePoolSize的线程一起阻塞。
                // 如果设置allowCoreThreadTimeOut=true的话，最后所有线程都会被回收。
                workQueue.take();
            if (r != null)
                return r;
            timedOut = true;
        } catch (InterruptedException retry) {
            timedOut = false;
        }
    }
}

/**
 * @param completedAbruptly true：worker执行的时候异常了
 */
private void processWorkerExit(Worker w, boolean completedAbruptly) {
    /**
     * 1、worker数量-1
     * 如果是突然终止，说明是task执行时异常情况导致，即run()方法执行时发生了异常，那么正在工作的worker线程数量需要-1
     * 如果不是突然终止，说明是worker线程没有task可执行了，不用-1，因为已经在getTask()方法中-1了
     */
    if (completedAbruptly)
        decrementWorkerCount();

    /**
     * 2、从Workers Set中移除worker
     */
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        // 统计完成的任务数
        completedTaskCount += w.completedTasks;
        // 从workers中移除，也就表示着从线程池中移除了一个工作线程
        workers.remove(w);
    } finally {
        mainLock.unlock();
    }

    /**
     * 3、在对线程池有负效益的操作时，都需要“尝试终止”线程池
     * 主要是判断线程池是否满足终止的状态
     * 如果状态满足，但还有线程池还有线程，尝试对其发出中断响应，使其能进入退出流程
     * 没有线程了，更新状态为tidying->terminated
     */
    tryTerminate();

    /**
     * 4、是否需要增加worker线程
     * 线程池状态是running 或 shutdown
     * 如果当前线程是突然终止的，addWorker()
     * 如果当前线程不是突然终止的，但当前线程数量 < 要维护的线程数量，addWorker()
     * 故如果调用线程池shutdown()，直到workQueue为空前，线程池都会维持corePoolSize个线程，然后再逐渐销毁这corePoolSize个线程
     */
    int c = ctl.get();
    /**
     * 以下情况会增加一个worker addWorker(null, false);
     * 1、completedAbruptly == true
     * 2、completedAbruptly == false && allowCoreThreadTimeOut == true && wc < 1
     * 3、completedAbruptly == false && allowCoreThreadTimeOut == false && wc < corePoolSize
     */
    if (runStateLessThan(c, STOP)) {
        if (!completedAbruptly) {
            int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
            if (min == 0 && ! workQueue.isEmpty())
                min = 1;
            if (workerCountOf(c) >= min)
                return; // replacement not needed
        }
        addWorker(null, false);
    }
}
```

getTask重要的地方是第二个if判断，目的是控制线程池的有效线程数量。由上文中的分析可以知道，在执行execute方法时，如果当前线程池的线程数量超过了corePoolSize且小于maximumPoolSize，并且workQueue已满时，则可以增加工作线程，但这时如果超时没有获取到任务，也就是timedOut为true的情况，说明workQueue已经为空了，也就说明了当前线程池中不需要那么多线程来执行任务了，可以把多于corePoolSize数量的线程销毁掉，保持线程数量在corePoolSize即可。

processWorkerExit执行完之后，工作线程被销毁，以上就是整个工作线程的生命周期，从execute方法开始，Worker使用ThreadFactory创建新的工作线程，runWorker通过getTask获取任务，然后执行任务，如果getTask返回null，进入processWorkerExit方法，整个线程结束。

下面是从execute到线程销毁的整个流程图：
![img](https://benjaminwhx.com/images/threadPoolExecutor3.png)

### 其他外部调用方法

下面的方法都是用户可以自己进行调用的：

```java
/**
 * 状态改为SHUTDOWN
 * 启动先前提交的任务被执行的有序关闭，但不接受新的任务。 如果已经关闭，则调用没有其他影响。
 * 该方法不能等待之前提交的任务执行完，如果需要等待执行，可以使用{@link #awaitTermination awaitTermination}
 */
public void shutdown() {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        checkShutdownAccess();
        advanceRunState(SHUTDOWN);
        interruptIdleWorkers();
        onShutdown(); // hook for ScheduledThreadPoolExecutor
    } finally {
        mainLock.unlock();
    }
    tryTerminate();
}

/**
 * 状态改为SHUTDOWN
 * 尝试停止所有正在执行的任务，停止等待任务的处理，并返回正在等待执行的任务的列表。 
 * 该方法不能等待之前提交的任务执行完，如果需要等待执行，可以使用{@link #awaitTermination awaitTermination}
 * 从这个方法返回后，这些任务从任务队列中排出（移除）。 除了竭尽全力地停止处理主动执行任务之外，没有任何保证。 
 * 这个实现通过{@link Thread＃interrupt}来取消任务，所以任何不能响应中断的任务都不会终止。
 */
public List<Runnable> shutdownNow() {
    List<Runnable> tasks;
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        checkShutdownAccess();
        advanceRunState(STOP);
        interruptWorkers();
        tasks = drainQueue();
    } finally {
        mainLock.unlock();
    }
    tryTerminate();
    return tasks;
}

// 执行任务前的hook
protected void beforeExecute(Thread t, Runnable r) { }

// 执行任务后的hook
protected void afterExecute(Runnable r, Throwable t) { }

/**
 * 什么都不做，交给子类实现，注意实现的时候使用super.terminated();
 */
protected void terminated() { }

/**
 * 判断状态 >= SHUTDOWN
 */
public boolean isShutdown() {
    return ! isRunning(ctl.get());
}

/**
 * 判断 SHUTDOWN <= 状态 < TERMINATED
 */
public boolean isTerminating() {
    int c = ctl.get();
    return ! isRunning(c) && runStateLessThan(c, TERMINATED);
}

/**
 * 判断状态 == TERMINATED
 */
public boolean isTerminated() {
    return runStateAtLeast(ctl.get(), TERMINATED);
}

/**
 * 在指定的超时时间范围内等待状态变为TERMINATED
 */
public boolean awaitTermination(long timeout, TimeUnit unit)
    throws InterruptedException {
    long nanos = unit.toNanos(timeout);
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        for (;;) {
            if (runStateAtLeast(ctl.get(), TERMINATED))
                return true;
            if (nanos <= 0)
                return false;
            nanos = termination.awaitNanos(nanos);
        }
    } finally {
        mainLock.unlock();
    }
}

public void setCorePoolSize(int corePoolSize) {
    if (corePoolSize < 0)
        throw new IllegalArgumentException();
    int delta = corePoolSize - this.corePoolSize;
    this.corePoolSize = corePoolSize;
    /**
     * 1、当前workCount > 传入的corePoolSize，中断空闲worker
     * 2、传入的corePoolSize比之前的要大，选出差值和queue的大小做比较，比较小的作为要增加的线程数，调用addWorker，如果中途遇到workQueue为空，就不增加了。
     */
    if (workerCountOf(ctl.get()) > corePoolSize)
        interruptIdleWorkers();
    else if (delta > 0) {
        int k = Math.min(delta, workQueue.size());
        while (k-- > 0 && addWorker(null, true)) {
            if (workQueue.isEmpty())
                break;
        }
    }
}

/**
 * 提前准备一个core的线程
 */
public boolean prestartCoreThread() {
    return workerCountOf(ctl.get()) < corePoolSize &&
        addWorker(null, true);
}

/**
 * 提前准备所有的core线程
 */
public int prestartAllCoreThreads() {
    int n = 0;
    while (addWorker(null, true))
        ++n;
    return n;
}

// 设置coreThreadTimeOut的值
public void allowCoreThreadTimeOut(boolean value) {
    if (value && keepAliveTime <= 0)
        throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
    if (value != allowCoreThreadTimeOut) {
        allowCoreThreadTimeOut = value;
        if (value)
            interruptIdleWorkers();
    }
}

// 设置maximumPoolSize
public void setMaximumPoolSize(int maximumPoolSize) {
    if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
        throw new IllegalArgumentException();
    this.maximumPoolSize = maximumPoolSize;
    if (workerCountOf(ctl.get()) > maximumPoolSize)
        interruptIdleWorkers();
}

// 从队列里面移除任务
public boolean remove(Runnable task) {
    boolean removed = workQueue.remove(task);
    tryTerminate(); // In case SHUTDOWN and now empty
    return removed;
}

/**
 * 清除队列里所有呗cancel的Future类型的任务，此方法可用作存储回收操作
 * 该方法可能存在其他线程的干扰，导致清除失败
 */
public void purge() {
    final BlockingQueue<Runnable> q = workQueue;
    try {
        Iterator<Runnable> it = q.iterator();
        while (it.hasNext()) {
            Runnable r = it.next();
            if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                it.remove();
        }
    } catch (ConcurrentModificationException fallThrough) {
        // 如果在遍历期间遇到干扰，请采取慢速路径。进行遍历复制并调用remove取消条目。慢路径更可能是O（N * N）。
        for (Object r : q.toArray())
            if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                q.remove(r);
    }

    tryTerminate(); // In case SHUTDOWN and now empty
}

/**
 * 返回线程池大小
 */
public int getPoolSize() {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        // runState == TIDYING 或者 runState == TERMINATED 返回0
        // 否则返回workers的大小
        return runStateAtLeast(ctl.get(), TIDYING) ? 0
            : workers.size();
    } finally {
        mainLock.unlock();
    }
}

/**
 * 获取活跃线程数：根据isLocked来判断是不是在执行任务
 */
public int getActiveCount() {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        int n = 0;
        for (Worker w : workers)
            if (w.isLocked())
                ++n;
        return n;
    } finally {
        mainLock.unlock();
    }
}

/**
 * 返回最大线程池的大小
 */
public int getLargestPoolSize() {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        return largestPoolSize;
    } finally {
        mainLock.unlock();
    }
}

/**
 * 返回任务总数（包括已经完成的和未完成的）
 */
public long getTaskCount() {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        long n = completedTaskCount;
        for (Worker w : workers) {
            n += w.completedTasks;
            if (w.isLocked())
                ++n;
        }
        return n + workQueue.size();
    } finally {
        mainLock.unlock();
    }
}

/**
 * 返回已完成任务总数
 */
public long getCompletedTaskCount() {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        long n = completedTaskCount;
        for (Worker w : workers)
            n += w.completedTasks;
        return n;
    } finally {
        mainLock.unlock();
    }
}
```

### 内部方法以及空方法

下面的方法都是用户自己调用不了的方法，这里也做一下说明：

```java
/**
 * 替换状态
 * 如果现在的ctl状态 >= targetState，什么都不做
 * 如果现在的ctl状态 < targetState，尝试替换状态
 */
private void advanceRunState(int targetState) {
    for (;;) {
        int c = ctl.get();
        if (runStateAtLeast(c, targetState) ||
            // 前3位替换，后29位保持ctl原来的数目
            ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
            break;
    }
}

/**
 * 尝试终止，只有当以下几种情况才把状态改为TERMINATED
 *      1、SHUTDOWN状态 && queue是空的 && wc == 0
 *      2、STOP状态 && wc == 0
 * workCount如果不是0，这时候就中断其中一个idle的worker来传播关闭信号
 * 该方法必须在执行任何可能会终止的操作之后调用此方法 - 在关闭期间减少工作人员数量或从队列中删除任务。
 * ScheduledThreadPoolExecutor里面也用到了，所以这里修饰符没用private
 */
final void tryTerminate() {
    for (;;) {
        int c = ctl.get();
        // c是运行时的状态
        if (isRunning(c) ||
            // c的状态值 >= TIDYING
            runStateAtLeast(c, TIDYING) ||
            // c的状态是SHUTDOWN && 队列不是空
            (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))
            return;

        // worker数不是0
        if (workerCountOf(c) != 0) { // Eligible to terminate
            interruptIdleWorkers(ONLY_ONE);
            return;
        }

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 设置ctl的状态为TIDYING，为中间过渡状态
            if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                try {
                    // 终止方法，空方法什么都不做，子类去实现
                    terminated();
                } finally {
                    // 设置ctl的状态为TERMINATED
                    ctl.set(ctlOf(TERMINATED, 0));
                    termination.signalAll();
                }
                return;
            }
        } finally {
            mainLock.unlock();
        }
    }
}

/**
 * 中断worker的空闲线程
 * @param onlyOne 是否仅仅中断worker中的第一个
 */
private void interruptIdleWorkers(boolean onlyOne) {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        for (Worker w : workers) {
            Thread t = w.thread;
            // 尝试获取锁，这里只有当线程没有运行的时候才能够tryLock成功
            if (!t.isInterrupted() && w.tryLock()) {
                try {
                    // 设置worker线程的中断变量
                    t.interrupt();
                } catch (SecurityException ignore) {
                } finally {
                    w.unlock();
                }
            }
            // true，只中断队列的第一个就退出
            if (onlyOne)
                break;
        }
    } finally {
        mainLock.unlock();
    }
}

/**
 * 中断所有worker的线程
 */
private void interruptWorkers() {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        for (Worker w : workers)
            w.interruptIfStarted();
    } finally {
        mainLock.unlock();
    }
}

/**
 * 中断所有worker的空闲线程
 */
private void interruptIdleWorkers() {
    interruptIdleWorkers(false);
}

/**
 * 根据拒绝策略拒绝执行命令
 */
final void reject(Runnable command) {
    handler.rejectedExecution(command, this);
}

/**
 * 移除队列中的Runnable到一个新list中，使用的是阻塞队列的drainTo方法
 * 但是如果队列是DelayQueue或者其他能够让poll或者drainTo失败移除元素的队列的话，遍历队列并删除它
 */
private List<Runnable> drainQueue() {
    BlockingQueue<Runnable> q = workQueue;
    ArrayList<Runnable> taskList = new ArrayList<Runnable>();
    q.drainTo(taskList);
    if (!q.isEmpty()) {
        for (Runnable r : q.toArray(new Runnable[0])) {
            if (q.remove(r))
                taskList.add(r);
        }
    }
    return taskList;
}

/**
 * 预留方法，ScheduledThreadPoolExecutor重写了此方法
 */
void onShutdown() {
}

// ScheduledThreadPoolExecutor进行调用，判断是不是running或shutdown状态
final boolean isRunningOrShutdown(boolean shutdownOK) {
    int rs = runStateOf(ctl.get());
    return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
}

// ScheduledThreadPoolExecutor进行调用，确认都提前准备好了
void ensurePrestart() {
    int wc = workerCountOf(ctl.get());
    if (wc < corePoolSize)
        addWorker(null, true);
    else if (wc == 0)
        addWorker(null, false);
}
```

## 4、线程池的监控

通过线程池提供的参数进行监控。线程池里有一些属性在监控线程池的时候可以使用

1. **getTaskCount**：线程池已经执行的和未执行的任务总数。
2. **getCompletedTaskCount**：线程池已完成的任务数量，该值小于等于taskCount。
3. **getLargestPoolSize**：线程池曾经创建过的最大线程数量。通过这个数据可以知道线程池是否满过，也就是达到了maximumPoolSize。
4. **getPoolSize**：线程池当前的线程数量。
5. **getActiveCount**：当前线程池中正在执行任务的线程数量。

通过这些方法，可以对线程池进行监控，在ThreadPoolExecutor类中提供了几个空方法，如beforeExecute方法，afterExecute方法和terminated方法，可以扩展这些方法在执行前或执行后增加一些新的操作，例如统计线程池的执行任务的时间等，可以继承自ThreadPoolExecutor来进行扩展。

## 5、合理的配置线程池

要想合理的配置线程池，就必须首先分析任务特性，可以从以下几个角度来进行分析：

1. 任务的性质：CPU密集型任务，IO密集型任务和混合型任务。
2. 任务的优先级：高，中和低。
3. 任务的执行时间：长，中和短。
4. 任务的依赖性：是否依赖其他系统资源，如数据库连接。

任务性质不同的任务可以用不同规模的线程池分开处理。CPU密集型任务配置尽可能少的线程数量，如配置Ncpu+1个线程的线程池。IO密集型任务则由于需要等待IO操作，线程并不是一直在执行任务，则配置尽可能多的线程，如2*Ncpu。混合型的任务，如果可以拆分，则将其拆分成一个CPU密集型任务和一个IO密集型任务，只要这两个任务执行的时间相差不是太大，那么分解后执行的吞吐率要高于串行执行的吞吐率，如果这两个任务执行时间相差太大，则没必要进行分解。我们可以通过`Runtime.getRuntime().availableProcessors()`方法获得当前设备的CPU个数。

优先级不同的任务可以使用优先级队列PriorityBlockingQueue来处理。它可以让优先级高的任务先得到执行，需要注意的是如果一直有优先级高的任务提交到队列里，那么优先级低的任务可能永远不能执行。

执行时间不同的任务可以交给不同规模的线程池来处理，或者也可以使用优先级队列，让执行时间短的任务先执行。

依赖数据库连接池的任务，因为线程提交SQL后需要等待数据库返回结果，如果等待的时间越长CPU空闲时间就越长，那么线程数应该设置越大，这样才能更好的利用CPU。

建议使用有界队列，有界队列能增加系统的稳定性和预警能力，可以根据需要设大一点，比如几千。有一次我们组使用的后台任务线程池的队列和线程池全满了，不断的抛出抛弃任务的异常，通过排查发现是数据库出现了问题，导致执行SQL变得非常缓慢，因为后台任务线程池里的任务全是需要向数据库查询和插入数据的，所以导致线程池里的工作线程全部阻塞住，任务积压在线程池里。如果当时我们设置成无界队列，线程池的队列就会越来越多，有可能会撑满内存，导致整个系统不可用，而不只是后台任务出现问题。当然我们的系统所有的任务是用的单独的服务器部署的，而我们使用不同规模的线程池跑不同类型的任务，但是出现这样问题时也会影响到其他任务。

我参考了：[如何合理地估算线程池大小？](http://ifeve.com/how-to-calculate-threadpool-size/) 这篇文章里的使用程序评估线程池大小。

## 6、结论

本文比较详细的分析了线程池的工作流程，总体来说有如下几个内容：

1. 分析了线程的创建，任务的提交，状态的转换以及线程池的关闭。
2. 这里通过execute方法来展开线程池的工作流程，execute方法通过corePoolSize，maximumPoolSize以及阻塞队列的大小来判断决定传入的任务应该被立即执行，还是应该添加到阻塞队列中，还是应该拒绝任务。
3. 介绍了线程池关闭时的过程，也分析了shutdown方法与getTask方法存在竞态条件。
4. 在获取任务时，要通过线程池的状态来判断应该结束工作线程还是阻塞线程等待新的任务，也解释了为什么关闭线程池时要中断工作线程以及为什么每一个worker都需要lock。

在向线程池提交任务时，除了execute方法，还有一个submit方法，submit方法会返回一个Future对象用于获取返回值，有关Future和Callable请自行了解一下相关的文章，这里就不介绍了。

## 7、扩展

一般开发中core线程数量是很难确定的，可以参考上面提到的如何合理的估算线程池的大小，但是一般都是开发者自己经过压测后得到的数据，之后到真正的线程环境验证，得出一个合理的core数字。假设是5，但是为了预防某些瞬时大流量(我们也无法预知到底流量会有多大)，通常会再设置一个比core线程数要大的max线程，假设是10。那么当这种瞬时流量真的发生了，如果希望服务器能尽快的提高处理速度，当然是需要让MAX线程尽快启动起来，帮着处理任务。这时候我们就可以自己扩展线程池，可以参考Tomcat的线程池实现。

## 参考

[聊聊并发（三）Java线程池的分析和使用](http://ifeve.com/java-threadpool/)
[深入理解Java线程池：ThreadPoolExecutor](https://www.jianshu.com/p/d2729853c4da)
[Java线程池ThreadPoolExecutor使用和分析(二) - execute()原理](https://www.cnblogs.com/trust-freedom/p/6681948.html)