### 【细谈Java并发】谈谈FutureTask

转载自[【细谈Java并发】谈谈FutureTask](https://benjaminwhx.com/2018/05/03/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88FutureTask%2F)

# 1、简介

FutureTask是一种异步任务(或异步计算)，举个栗子，主线程的逻辑中需要使用某个值，但这个值需要负责的运算得来，那么主线程可以提前建立一个异步任务来计算这个值(在其他的线程中计算)，然后去做其他事情，当需要这个值的时候再通过刚才建立的异步任务来获取这个值，有点并行的意思，这样可以缩短整个主线程逻辑的执行时间。

与1.6版本不同，1.7的FutureTask不再基于AQS来构建，而是在内部采用简单的Treiber Stack来保存等待线程。

# 2、框架

我们先来看看类图：

![img](https://benjaminwhx.com/images/futuretask1.jpg)

可以看到FutureTask实现了Runnable接口和Future接口，因此FutureTask可以传递到线程对象Thread或Excutor(线程池)来执行。

如果在当前线程中需要执行比较耗时的操作，但又不想阻塞当前线程时，可以把这些作业交给FutureTask，另开一个线程在后台完成，当当前线程将来需要时，就可以通过FutureTask对象获得后台作业的计算结果或者执行状态。

我们来看看它的构造方法

```java
public FutureTask(Callable<V> callable) {
    if (callable == null)
        throw new NullPointerException();
    this.callable = callable;
    this.state = NEW;       // ensure visibility of callable
}

public FutureTask(Runnable runnable, V result) {
    this.callable = Executors.callable(runnable, result);
    this.state = NEW;       // ensure visibility of callable
}
```

可见，构造一个FutureTask很简单，可以通过一个Callable来构建，也可以通过一个Runnable和一个result来构建。

> 这里要注意的是必须把state的写放到最后，因为state本身由volatile修饰，所以可以保证callable的可见性。(因为后续读callable之前会先读state，还记得这个volatile写读的HB规则吧)。

接下来我们看一下它的内部结构：

```java
public class FutureTask<V> implements RunnableFuture<V> {  

    /** 
     * 内部状态可能得迁转过程: 
     * NEW -> COMPLETING -> NORMAL //正常完成 
     * NEW -> COMPLETING -> EXCEPTIONAL //发生异常 
     * NEW -> CANCELLED //取消 
     * NEW -> INTERRUPTING -> INTERRUPTED //中断 
     */  
    private volatile int state;  
    private static final int NEW          = 0;  
    private static final int COMPLETING   = 1;  
    private static final int NORMAL       = 2;  
    private static final int EXCEPTIONAL  = 3;  
    private static final int CANCELLED    = 4;  
    private static final int INTERRUPTING = 5;  
    private static final int INTERRUPTED  = 6;  
    /** 内部的callable，运行完成后设置为null */  
    private Callable<V> callable;  
    /** 如果正常完成，就是执行结果，通过get方法获取；如果发生异常，就是具体的异常对象，通过get方法抛出。 */  
    private Object outcome; // 本身没有volatile修饰, 依赖state的读写来保证可见性。  
    /** 执行内部callable的线程。 */  
    private volatile Thread runner;  
    /** 存放等待线程的Treiber Stack*/  
    private volatile WaitNode waiters;  
}
```

内部结构很明确，重点看下WaitNode的结构吧：

```java
static final class WaitNode {  
    volatile Thread thread;  
    volatile WaitNode next;  
    WaitNode() { thread = Thread.currentThread(); }  
}
```

这个也很简单，就是包含了当前线程对象，并有指向下一个WaitNode的指针，所谓的Treiber Stack就是由WaitNode组成的(一个单向链表)。

经常使用FutureTask的话一定会非常熟悉它的运行过程：

1. 创建任务，实际使用时，一般会结合线程池(ThreadPoolExecutor)使用，所以是在线程池内部创建FutureTask。
2. 执行任务，一般会有由工作线程(对于我们当前线程来说的其他线程)调用FutureTask的run方法，完成执行。
3. 获取结果，一般会有我们的当前线程去调用get方法来获取执行结果，如果获取时，任务并没有被执行完毕，当前线程就会被阻塞，直到任务被执行完毕，然后获取结果。
4. 取消任务，某些情况下会放弃任务的执行，进行任务取消。

**接下来我们从源码的角度看下执行任务过程，也就是运行相关方法吧 。**

# 3、源码分析

## run()

```java
public void run() {
    // 如果state是NEW，设置线程为当前线程
    if (state != NEW || 
        !UNSAFE.compareAndSwapObject(this, runnerOffset, null, Thread.currentThread()))
        return;
    try {
        Callable<V> c = callable;
        if (c != null && state == NEW) {
            V result;
            boolean ran;
            try {
                // 调用Callable的call方法，得到结果
                result = c.call();
                ran = true;
            } catch (Throwable ex) {
                // 处理异常状态和结果
                result = null;
                ran = false;
                setException(ex);
            }
            if (ran)
                // 正常处理设置状态和结果
                set(result);
        }
    } finally {
        // runner必须在设置了state之后再置空，避免run方法出现并发问题。
        runner = null;
        // 这里还必须再读一次state，避免丢失中断。
        int s = state;
        if (s >= INTERRUPTING)
            // 处理可能发生的取消中断(cancel(true))。  
            handlePossibleCancellationInterrupt(s);
    }
}
```

看下run过程中，正常完成后调用的set方法：

```java
/**
 * 设置结果，状态从 NEW 变为 COMPLETING
 * 设置返回结果为t(正常结果)
 * 改变状态从 COMPLETING 到 NORMAL
 * 调用finishCompletion完成收尾工作
 */
protected void set(V v) {
    if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
        outcome = v;
        UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
        finishCompletion();
    }
}
```

set过程中，首先尝试将当前任务状态state从NEW改为COMPLETING。如果成功的话，再设置执行结果到outcome。然后将state再次设置为NORMAL，注意这次使用的是putOrderedInt，其实就是原子量的LazySet内部使用的方法。为什么使用这个方法？首先LazySet相对于Volatile-Write来说更廉价，因为它没有昂贵的Store/Load屏障，只有Store/Store屏障(x86下Store/Store屏障是一个空操作)，其次，后续线程不会及时的看到state从COMPLETING变为NORMAL，但这没什么关系，而且NORMAL是state的最终状态之一，以后不会在变化了。

上述过程最后还调用了一个finishCompletion方法：

```java
/**
 * 遍历waiters的next节点，唤醒节点的线程并把引用变为null，等待GC
 */
private void finishCompletion() {
    for (WaitNode q; (q = waiters) != null;) {
        // 尝试将waiters设置为null。  
        if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
            // 然后将waiters中的等待线程全部唤醒。  
            for (;;) {
                Thread t = q.thread;
                if (t != null) {
                    q.thread = null;
                    LockSupport.unpark(t);    // 唤醒线程
                }
                WaitNode next = q.next;
                if (next == null)
                    break;
                q.next = null; // unlink to help gc
                q = next;
            }
            break;
        }
    }
    // 回调下钩子方法。  
    done();
    // 置空callable，减少内存占用  
    callable = null; 
}
```

可见，finishCompletion主要就是在任务执行完毕后，移除Treiber Stack，并将Treiber Stack中所有等待获取任务结果的线程唤醒，然后回调下done钩子方法。

看完了set，再看下run过程中如果发生异常，调用的setException方法：

```java
/**
 * 发生异常，状态从 NEW 变为 COMPLETING
 * 设置返回结果为t(异常结果)
 * 改变状态从 COMPLETING 到 EXCEPTIONAL
 * 调用finishCompletion完成收尾工作
 */
protected void setException(Throwable t) {
    if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
        outcome = t;
        UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
        finishCompletion();
    }
}
```

和set方法一个套路。

最后看下run过程中最后调用的handlePossibleCancellationInterrupt方法：

```java
/**
 * 确保cancel(true)产生的中断发生在run或runAndReset方法过程中。 
 */
private void handlePossibleCancellationInterrupt(int s) {
    // 如果当前正在中断过程中，自旋等待一下，等中断完成。 
    if (s == INTERRUPTING)
        while (state == INTERRUPTING)
            Thread.yield(); // wait out pending interrupt
    // 这里的state状态一定是INTERRUPTED;  
    // 这里不能清除中断标记，因为没办法区分来自cancel(true)的中断。  
    // Thread.interrupted();  
}
```

这里总结一下run方法：

1. 只有state为NEW的时候才执行任务(调用内部callable的run方法)。执行前会原子的设置执行线程(runner)，防止竞争。
2. 如果任务执行成功，设置执行结果，状态变更：NEW -> COMPLETING -> NORMAL。
3. 如果任务执行发生异常，设置异常结果，状态变更：NEW -> COMPLETING -> EXCEPTIONAL。
4. 将Treiber Stack中等待当前任务执行结果的等待节点中的线程全部唤醒，同时删除这些等待节点，将整个Treiber Stack置空。
5. 最后别忘了等一下可能发生的cancel(true)中引起的中断，让这些中断发生在执行任务过程中(别泄露出去)。

## runAndReset()

```java
protected boolean runAndReset() {
    if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                    null, Thread.currentThread()))
        return false;
    boolean ran = false;
    int s = state;
    try {
        Callable<V> c = callable;
        if (c != null && s == NEW) {
            try {
                c.call(); // don't set result
                ran = true;
            } catch (Throwable ex) {
                setException(ex);
            }
        }
    } finally {
        // runner must be non-null until state is settled to
        // prevent concurrent calls to run()
        runner = null;
        // state must be re-read after nulling runner to prevent
        // leaked interrupts
        s = state;
        if (s >= INTERRUPTING)
            handlePossibleCancellationInterrupt(s);
    }
    return ran && s == NEW;
}
```

该方法和run方法的区别是，run方法只能被运行一次任务，而该方法可以多次运行任务。而runAndReset这个方法不会设置任务的执行结果值,如果该任务成功执行完成后，不修改state的状态，还是可运行（NEW）状态，如果取消任务或出现异常，则不会再次执行。

## get()

```java
public V get() throws InterruptedException, ExecutionException {
    int s = state;
    if (s <= COMPLETING)
        s = awaitDone(false, 0L);    // 如果任务还没执行完毕，等待任务执行完毕。  
    return report(s);    // 如果任务执行完毕，获取执行结果。  
}
```

看下awaitDone方法

```java
private int awaitDone(boolean timed, long nanos)
        throws InterruptedException {
    // 先算出到期时间。  
    final long deadline = timed ? System.nanoTime() + nanos : 0L;
    WaitNode q = null;
    boolean queued = false;
    for (;;) {
        if (Thread.interrupted()) {
            // 如果当前线程被中断，移除等待节点q，然后抛出中断异常。
            removeWaiter(q);
            throw new InterruptedException();
        }

        int s = state;
        if (s > COMPLETING) {
            // 如果任务已经执行完毕  
            if (q != null)
                q.thread = null;    // 如果q不为null，将q中的thread置空。
            return s;    // 返回任务状态。 
        }
        else if (s == COMPLETING) // cannot time out yet
            Thread.yield();    // 如果当前正在完成过程中，出让CPU。
        else if (q == null)
            q = new WaitNode();    // 创建一个等待节点。
        else if (!queued)
            // 将q(包含当前线程的等待节点)入队。  
            queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                    q.next = waiters, q);
        else if (timed) {
            nanos = deadline - System.nanoTime();
            if (nanos <= 0L) {
                //如果超时，移除等待节点q
                removeWaiter(q);
                //返回任务状态。  
                return state;
            }
            //超时的话，就阻塞给定时间。  
            LockSupport.parkNanos(this, nanos);
        }
        else
            //没设置超时的话，就阻塞当前线程。
            LockSupport.park(this);
    }
}
```

再看下awaitDone方法中调用的removeWaiter：

```java
private void removeWaiter(WaitNode node) {
    if (node != null) {
        //将node的thread域置空。 
        node.thread = null;
        //下面过程中会将node从等待队列中移除，以thread域为null为依据，  
        //如果过程中发生了竞争，重试。  
        retry:
        for (;;) {          // restart on removeWaiter race
            for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                s = q.next;
                if (q.thread != null)
                    pred = q;
                else if (pred != null) {
                    pred.next = s;
                    if (pred.thread == null) // check for race
                        continue retry;
                }
                else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                        q, s))
                    continue retry;
            }
            break;
        }
    }
}
```

再看下get方法中获取结果时调用的report：

```java
private V report(int s) throws ExecutionException {
    Object x = outcome;
    if (s == NORMAL)
        return (V)x;
    if (s >= CANCELLED)
        throw new CancellationException();
    throw new ExecutionException((Throwable)x);
}
```

report如果是正常状态，就返回结果。否则抛出异常。

看完了get方法，再看下get(long timeout, TimeUnit unit)方法：

```java
public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
    if (unit == null)
        throw new NullPointerException();
    int s = state;
    if (s <= COMPLETING &&
            (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
        throw new TimeoutException();
    return report(s);
}
```

小结一下get方法：

1. 首先检查当前任务的状态，如果状态表示执行完成，进入第2步。
2. 获取执行结果，也可能得到取消或者执行异常，get过程结束。
3. 如果当前任务状态表示未执行或者正在执行，那么当前线程放入一个新建的等待节点，然后进入Treiber Stack进行阻塞等待。
4. 如果任务被工作线程(对当前线程来说是其他线程)执行完毕，执行完毕时工作线程会唤醒Treiber Stack上等待的所有线程，所以当前线程被唤醒，清空当前等待节点上的线程域，然后进入第2步。
5. 当前线程在阻塞等待结果过程中可能被中断，如果被中断，那么会移除当前线程在Treiber Stack上对应的等待节点，然后抛出中断异常，get过程结束。
6. 当前线程也可能执行带有超时时间的阻塞等待，如果超时时间过了，还没得到执行结果，那么会除当前线程在Treiber Stack上对应的等待节点，然后抛出超时异常，get过程结束。

## cancel(boolean)

```java
public boolean cancel(boolean mayInterruptIfRunning) {
    if (!(state == NEW &&
            UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                    mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
        return false;
    try {   
        // mayInterruptIfRunning并且有正在运行的线程，调用interrupt中断，最后设置状态为INTERRUPTED
        if (mayInterruptIfRunning) {
            try {
                Thread t = runner;
                if (t != null)
                    t.interrupt();
            } finally { // final state
                UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
            }
        }
    } finally {
        finishCompletion();
    }
    return true;
}
```

cancel分为两种情况：

1. mayInterruptIfRunning == true。这个时候状态从 NEW 变为 INTERRUPTING ，如果有正在运行的线程，调用interrupt中断，最后把状态从 INTERRUPTING 变为 INTERRUPTED。
2. mayInterruptIfRunning == false。这个时候状态从 NEW 变为 CANCELLED。
3. 最后都会执行finishCompletion方法，完成结束的收尾工作。唤醒所有在get()方法等待的线程。

# 4、jdk1.6不同的地方

为什么jdk 1.6以后的FutureTask不像1.6那样基于AQS构建了？

首先，前面贴代码了时候故意去掉了一些注释，避免读代码的时候受影响，现在我们来看一下关键的一段：

```java
/* 
 * Revision notes: This differs from previous versions of this 
 * class that relied on AbstractQueuedSynchronizer, mainly to 
 * avoid surprising users about retaining interrupt status during 
 * cancellation races.  
 */
```

主要是这句：mainly to avoid surprising users about retaining interrupt status during cancellation races。

大概意思是：使用AQS的方式，可能会在取消发生竞争过程中诡异的保留了中断状态。这里之所以没有采用这种方式，是为了避免这种情况的发生。

具体什么情况下会发生呢？

```java
ThreadPoolExecutor executor = ...;  
executor.submit(task1).cancel(true);  
executor.submit(task2);
```

看上面的代码，虽然中断的是task1，但可能task2得到中断信号。

原因是什么呢？看下JDK1.6的FutureTask的中断代码：

```java
boolean innerCancel(boolean mayInterruptIfRunning) {  
 for (;;) {  
  int s = getState();  
  if (ranOrCancelled(s))  
      return false;  
  if (compareAndSetState(s, CANCELLED))  
      break;  
 }  
    if (mayInterruptIfRunning) {  
        Thread r = runner;  
        if (r != null)  //第1行  
            r.interrupt(); //第2行  
    }  
    releaseShared(0);  
    done();  
    return true;  
}
```

结合上面代码例子看一下，如果主线程执行到第1行的时候，线程池可能会认为task1已经执行结束(被取消)，然后让之前执行task1工作线程去执行task2，工作线程开始执行task2之后，然后主线程执行第2行(我们会发现并没有任何同步机制来阻止这种情况的发生)，这样就会导致task2被中断了。更多的相关信息参考[这个Bug说明](http://bugs.java.com/bugdatabase/view_bug.do?bug_id=8016247)。

所以现在就能更好的理解JDK1.7 FutureTask的handlePossibleCancellationInterrupt中为什么要将cancel(true)中的中断保留在当前run方法运行范围内了吧!

JDK1.7的FutureTask的代码解析完毕！