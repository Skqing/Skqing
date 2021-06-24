### 【细谈Java并发】谈谈AQS

转载自[【细谈Java并发】谈谈AQS](https://benjaminwhx.com/2018/04/30/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88AQS/)

# 1、概述

谈到并发，不得不谈ReentrantLock；而谈到ReentrantLock，不得不谈AbstractQueuedSynchronizer（AQS）！

AQS定义了一个抽象的队列来进行同步操作，很多同步类都依赖于它，例如常用的ReentrantLock/Semaphore/CountDownLatch等。下面我们来通过源码分析解开AQS的神秘面纱。

# 2、框架

![img](https://benjaminwhx.com/images/AQS1.png)

它维护了一个volatile int state（代表共享资源）和一个FIFO线程等待队列（多线程争用资源被阻塞时会进入此队列）。这里volatile是核心关键词，具体volatile的语义，在此不述。state的访问方式有三种:

- getState()
- setState()
- compareAndSetState()

AQS定义两种资源共享方式：**Exclusive**（独占，只有一个线程能执行，如ReentrantLock）和**Share**（共享，多个线程可同时执行，如Semaphore/CountDownLatch）。

不同的自定义同步器争用共享资源的方式也不同。**自定义同步器在实现时只需要实现共享资源state的获取与释放方式即可**，至于具体线程等待队列的维护（如获取资源失败入队/唤醒出队等），AQS已经在顶层实现好了。自定义同步器实现时主要实现以下几种方法：

```java
/**
 * 独占式获取同步状态，实现该方法需要查询当前状态并判断同步状态是否符合预期，然后再进行CAS设置同步状态。
 * 成功返回true，失败返回false。
 */
protected boolean tryAcquire(int arg) {
    throw new UnsupportedOperationException();
}

/**
 * 独占式释放同步状态，等待获取同步状态的线程将有机会获取同步状态。
 * 成功返回true，失败返回false。
 */
protected boolean tryRelease(int arg) {
    throw new UnsupportedOperationException();
}

/**
 * 共享式获取同步状态。
 * 1. 返回负数表示失败。
 * 2. 0表示成功，但没有剩余可用资源。
 * 3. 正数表示成功，且有剩余资源。
 */
protected int tryAcquireShared(int arg) {
    throw new UnsupportedOperationException();
}

/**
 * 共享式释放同步状态。
 * 如果释放后允许唤醒后续等待结点返回true，否则返回false。
 */
protected boolean tryReleaseShared(int arg) {
    throw new UnsupportedOperationException();
}

/**
 * 当前同步器是否在独占模式下被线程占用，一般该方法表示是否被当前线程所独占。
 * 只有用到condition才需要去实现它。
 */
protected boolean isHeldExclusively() {
    throw new UnsupportedOperationException();
}
```

以ReentrantLock为例，state初始化为0，表示未锁定状态。A线程lock()时，会调用tryAcquire()独占该锁并将state+1。此后，其他线程再tryAcquire()时就会失败，直到A线程unlock()到state=0（即释放锁）为止，其它线程才有机会获取该锁。当然，释放锁之前，A线程自己是可以重复获取此锁的（state会累加），这就是可重入的概念。但要注意，获取多少次就要释放多么次，这样才能保证state是能回到零态的。

再以CountDownLatch以例，任务分为N个子线程去执行，state也初始化为N（注意N要与线程个数一致）。这N个子线程是并行执行的，每个子线程执行完后countDown()一次，state会CAS减1。等到所有子线程都执行完后(即state=0)，会unpark()主调用线程，然后主调用线程就会从await()函数返回，继续后余动作。

一般来说，自定义同步器要么是独占方法，要么是共享方式，但AQS也支持自定义同步器同时实现独占和共享两种方式，如ReentrantReadWriteLock。

# 3、源码分析

我们先来看看Node元素的类结构图：

```java
static final class Node {
    static final Node SHARED = new Node();
    static final Node EXCLUSIVE = null;
    //表示当前的线程被取消；
    static final int CANCELLED =  1;
    //表示当前节点的后继节点包含的线程需要运行，也就是unpark；
    static final int SIGNAL    = -1;
    //表示当前节点在等待condition，也就是在condition队列中；
    static final int CONDITION = -2;
    //表示当前场景下后续的acquireShared能够得以执行；
    static final int PROPAGATE = -3;
    //表示节点的状态。默认为0，表示当前节点在sync队列中，等待着获取锁。
    //其它几个状态为：CANCELLED、SIGNAL、CONDITION、PROPAGATE
    volatile int waitStatus;
    //前驱节点
    volatile Node prev;
    //后继节点
    volatile Node next;
    //获取锁的线程
    volatile Thread thread;
    //存储condition队列中的后继节点。
    Node nextWaiter;
    ......
}
```

## 3.1、acquire(int)

```java
/**
 * 独占获取同步状态，如果当前线程获取同步状态成功，则由该方法返回，否则，
 * 将会进入同步队列等待，该方法会调用重写的tryAcquire(int arg)方法
 */
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}
```

该方法是独占模式下线程获取共享资源的顶层入口。如果获取到资源，线程直接返回，否则进入等待队列，直到获取到资源为止，且整个过程忽略中断的影响。ReentrantLock的lock方法就是调用的该方法来获取锁。

方法的执行流程如下：

1. 调用自定义同步器的tryAcquire()尝试直接去获取资源，如果成功则直接返回。
2. 没成功，则addWaiter()将该线程加入等待队列的尾部，并标记为独占模式。
3. acquireQueued()使线程在等待队列中休息，有机会时（轮到自己，会被unpark()）会去尝试获取资源。获取到资源后才返回。如果在整个等待过程中被中断过，则返回true，否则返回false。
4. 如果线程在等待过程中被中断过，它是不响应的。只是获取资源后才再进行自我中断selfInterrupt()，将中断补上。

可能单看这个流程还是看不太明白，没关系，接下来我们会一一击破，等会回过头来再看这个流程就会非常清晰了。

### tryAcquire(int)

上面我们也说过这个方法（具体的资源的获取/释放）是需要实现类进行重写的。至于能不能重入，能不能加锁，那就看具体的自定义同步器怎么去设计了。当然，自定义同步器在进行资源访问时要考虑线程安全的影响。

下面有的方法比较简单，直接看注释吧。

### addWaiter(Node)

```java
/**
 * 将当前线程加入到等待队列的队尾，并返回当前线程所在的结点
 */
private Node addWaiter(Node mode) {
    Node node = new Node(Thread.currentThread(), mode);
    // 首先尝试在链表的后面快速添加节点
    Node pred = tail;
    if (pred != null) {
        node.prev = pred;
        // 将该节点添加到队列尾部
        if (compareAndSetTail(pred, node)) {
            pred.next = node;
            return node;
        }
    }
    // 如果首节点为空或者cas添加失败，则进入enq方法通过自旋方式入队列(保证成功)
    enq(node);
    return node;
}
```

### enq(Node)

```java
/**
 * 将node加入队尾
 */
private Node enq(final Node node) {
    // 自旋重试
    for (;;) {
        Node t = tail;
        // 当前没有节点，构造一个new Node()，将head和tail指向它
        if (t == null) { // Must initialize
            if (compareAndSetHead(new Node()))
                tail = head;
        } else {
            // 当前有节点，将传入的Node放在链表的最后
            node.prev = t;
            if (compareAndSetTail(t, node)) {
                t.next = node;
                return t;
            }
        }
    }
}
```

这里用到了CAS自旋来把Node放到队尾。

### acquireQueued(Node, int)

OK，通过tryAcquire()和addWaiter()，该线程获取资源失败，已经被放入等待队列尾部了。聪明的你立刻应该能想到该线程下一部该干什么了吧：**进入等待状态休息，直到其他线程彻底释放资源后唤醒自己，自己再拿到资源，然后就可以去干自己想干的事了**。没错，就是这样！是不是跟医院排队拿号有点相似~~acquireQueued()就是干这件事：**在等待队列中排队拿号（中间没其它事干可以休息），直到拿到号后再返回**。这个函数非常关键，还是上源码吧

```java
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true; // 标记是否成功拿到资源
    try {
        boolean interrupted = false; // 标记等待过程中是否被中断过
        for (;;) {
            final Node p = node.predecessor();    // node的前一个节点
            // 如果前一个节点是head，说明当前node节点是第二个节点，接着尝试去获取资源
            // Note：可能是head释放完资源唤醒自己的，当然也可能被interrupt了
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return interrupted;    // 返回等待过程中是否被中断过
            }

            // 如果自己可以休息了，就进入waiting状态，直到被unpark()
            if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                interrupted = true;    // 如果等待过程中被中断过，哪怕只有那么一次，就将interrupted标记为true
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

/**
 * 此方法主要用于检查状态，看看自己是否真的可以去休息了
 * 1.如果pred的waitStatus是SIGNAL，直接返回true
 * 2.如果pred的waitStatus>0，也就是CANCELLED，向前一直找到<=0的节点，让节点的next指向node
 * 3.如果pred的waitStatus<=0，改成SIGNAL
 */
private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
    int ws = pred.waitStatus;
    if (ws == Node.SIGNAL)
        // 如果已经告诉前驱拿完号后通知自己一下，那就可以安心休息了
        return true;
    if (ws > 0) {
        /*
         * 如果前节点放弃了，那就一直往前找，直到找到最近一个正常等待的状态，并排在它的后边。
         * 注意：那些放弃的结点，由于被自己“加塞”到它们前边，它们相当于形成一个无引用链，
         * 稍后就会被保安大叔赶走了(GC回收)！
         */
        do {
            node.prev = pred = pred.prev;
        } while (pred.waitStatus > 0);
        pred.next = node;
    } else {
        // 如果前节点正常，那就把前节点的状态设置成SIGNAL，告诉它拿完号后通知自己一下。有可能失败，人家说不定刚刚释放完呢！
        compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
    }
    return false;
}

/**
 * 让线程去休息，真正进入等待状态
 */
private final boolean parkAndCheckInterrupt() {
    LockSupport.park(this);    // 调用park()使线程进入waiting状态
    return Thread.interrupted(); // 如果被唤醒，查看是否被中断(该方法会重置标识位)
}
```

上面的parkAndCheckInterrupt方法才是真正让线程“等待”的方法，其中用到了LockSupport的park方法，关于LockSupport可以参考我之前的博文：[Java并发之LockSupport](http://benjaminwhx.com/2018/03/11/【细谈Java并发】谈谈LockSupport/)

总结一下，acquireQueued总共做了3件事：

1. 结点进入队尾后，检查状态，找到安全休息点。
2. 调用park()进入waiting状态，等待unpark()或interrupt()唤醒自己。
3. 被唤醒后，看自己是不是有资格能拿到号。如果拿到，head指向当前结点，并返回从入队到拿到号的整个过程中是否被中断过；如果没拿到，继续流程1。

### acquire总结

我们回过头来再看acquire方法，发现还有一个方法没有说到，那就是selfInterrupt方法，在

```java
// 重新设置中断标识位
static void selfInterrupt() {
    Thread.currentThread().interrupt();
}
```

由于此函数是重中之重，最后再用流程图总结一下：

![img](https://benjaminwhx.com/images/AQS2.png)

## 3.2、release(int)

上一小节已经把acquire()说完了，这一小节就来讲讲它的反操作release()吧。此方法是独占模式下线程释放资源的顶层入口。它会释放指定量的资源，如果彻底释放了（即state=0）,它会唤醒等待队列里的其他线程来获取资源。这也正是unlock()的语义，当然不仅仅只限于unlock()。下面是release()的源码：

```java
/**
 * 释放资源
 */
public final boolean release(int arg) {
    if (tryRelease(arg)) {
        Node h = head;
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h); // 唤醒等待队列里的下一个线程
        return true;
    }
    return false;
}
```

逻辑并不复杂。它调用tryRelease()来释放资源。有一点需要注意的是，**它是根据tryRelease()的返回值来判断该线程是否已经完成释放掉资源了！所以自定义同步器在设计tryRelease()的时候要明确这一点！！**

### tryRelease(int)

跟tryAcquire()一样，这个方法是需要独占模式的自定义同步器去实现的。正常来说，tryRelease()都会成功的，因为这是独占模式，该线程来释放资源，那么它肯定已经拿到独占资源了，直接减掉相应量的资源即可(state-=arg)，也不需要考虑线程安全的问题。但要注意它的返回值，上面已经提到了，**release()是根据tryRelease()的返回值来判断该线程是否已经完成释放掉资源了！**所以自义定同步器在实现时，如果已经彻底释放资源(state=0)，要返回true，否则返回false。

### unparkSuccessor(Node)

此方法用于唤醒等待队列中下一个线程。下面是源码：

```java
private void unparkSuccessor(Node node) {
    // 这里，node一般为当前线程所在的结点。
    int ws = node.waitStatus;
    if (ws < 0)    // 置零当前线程所在的结点状态，允许失败。
        compareAndSetWaitStatus(node, ws, 0);

    // 找到下一个需要唤醒的结点s
    Node s = node.next;
    if (s == null || s.waitStatus > 0) {
        s = null;
        for (Node t = tail; t != null && t != node; t = t.prev)
            if (t.waitStatus <= 0)
                s = t;
    }
    if (s != null)
        LockSupport.unpark(s.thread);    // 唤醒
}
```

### release总结

release()是独占模式下线程释放共享资源的顶层入口。它会释放指定量的资源，如果彻底释放了（即state=0）,它会唤醒等待队列里的其他线程来获取资源。

## 3.3、acquireShared(int)

此方法是共享模式下线程获取共享资源的顶层入口。它会获取指定量的资源，获取成功则直接返回，获取失败则进入等待队列，直到获取到资源为止，整个过程忽略中断。下面是acquireShared()的源码：

```java
public final void acquireShared(int arg) {
    if (tryAcquireShared(arg) < 0)
        doAcquireShared(arg);
}
```

这里tryAcquireShared()依然需要自定义同步器去实现。但是AQS已经把其返回值的语义定义好了：负值代表获取失败；0代表获取成功，但没有剩余资源；正数表示获取成功，还有剩余资源，其他线程还可以去获取。

### doAcquireShared(int)

此方法用于将当前线程加入等待队列尾部休息，直到其他线程释放资源唤醒自己，自己成功拿到相应量的资源后才返回。

```java
private void doAcquireShared(int arg) {
    final Node node = addWaiter(Node.SHARED);    //加入队列尾部
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);    // 尝试获取资源
                if (r >= 0) {
                    setHeadAndPropagate(node, r);    // 将head指向自己，还有剩余资源可以再唤醒之后的线程
                    p.next = null; // help GC
                    if (interrupted)    // 如果等待过程中被打断过，此时将中断补上
                        selfInterrupt();
                    failed = false;
                    return;
                }
            }
            // 判断状态，寻找安全点，进入waiting状态，等着被unpark()或interrupt()
            if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}
```

有木有觉得跟acquireQueued()很相似？对，其实流程并没有太大区别。只不过这里将补中断的selfInterrupt()放到doAcquireShared()里了，而独占模式是放到acquireQueued()之外，其实都一样，不知道Doug Lea是怎么想的。

除此之外，有一个方法很重要：setHeadAndPropagate。它除了重新标记head指向的节点外，还有一个重要的作用，那就是propagate（传递），也就是共享的意思。

用图举个例子：

![img](https://benjaminwhx.com/images/AQS4.jpg)

因为线程B的读锁无法直接获得锁，所以需要在Sync队列中等待，导致后面其他线程的读锁都得等待。

当线程A的读锁释放后，线程B的写锁获得锁，当它释放后，线程B的读锁会获取到锁，并传递给后面的节点，传递的事情就是在setHeadAndPropagate里做的，我们来看看它是如何做的。

### setHeadAndPropagate(Node, int)

```java
private void setHeadAndPropagate(Node node, int propagate) {
    Node h = head;//记录当前头节点
    //设置新的头节点，即把当前获取到锁的节点设置为头节点
    //注：这里是获取到锁之后的操作，不需要并发控制
    setHead(node);
    //这里意思有两种情况是需要执行唤醒操作
    //1.propagate > 0 表示调用方指明了后继节点需要被唤醒
    //2.头节点后面的节点需要被唤醒（waitStatus<0），不论是老的头结点还是新的头结点
    if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0) {
        Node s = node.next;
        //如果当前节点的后继节点是共享类型或者没有后继节点，则进行唤醒
        //这里可以理解为除非明确指明不需要唤醒（后继等待节点是独占类型），否则都要唤醒
        if (s == null || s.isShared())
            doReleaseShared();
    }
}
```

此方法在setHead()的基础上多了一步，就是自己苏醒的同时，如果条件符合（比如还有剩余资源），还会去唤醒后继结点，毕竟是共享模式！这样，形成了一个唤醒链，直到写锁的节点就停止。

doReleaseShared()我们留着下一小节的releaseShared()里来讲。

### acquireShared总结

OK，至此，acquireShared()也要告一段落了。让我们再梳理一下它的流程：

1. tryAcquireShared()尝试获取资源，成功则直接返回，否则进入流程2。
2. doAcquireShared()会将当前线程加入等待队列尾部休息，直到其他线程释放资源唤醒自己。它还会尝试着让唤醒传递到后面的节点。

其实跟acquire()的流程大同小异，只不过多了个**自己拿到资源后，还会去唤醒后继队友的操作（这才是共享嘛）**。

## 3.4、releaseShared()

上一小节已经把acquireShared()说完了，这一小节就来讲讲它的反操作releaseShared()吧。此方法是共享模式下线程释放共享资源的顶层入口。它会释放指定量的资源，如果成功释放且允许唤醒等待线程，它会唤醒等待队列里的其他线程来获取资源。下面是releaseShared()的源码：

```java
public final boolean releaseShared(int arg) {
    if (tryReleaseShared(arg)) {// 尝试释放资源
        doReleaseShared();// 唤醒后继结点
        return true;
    }
    return false;
}
```

此方法的流程也比较简单，一句话：**释放掉资源后，唤醒后继**。跟独占模式下的release()相似，但有一点稍微需要注意：独占模式下的tryRelease()在完全释放掉资源（state=0）后，才会返回true去唤醒其他线程，这主要是基于独占下可重入的考量；而共享模式下的releaseShared()则没有这种要求，共享模式实质就是控制一定量的线程并发执行，那么拥有资源的线程在释放掉部分资源时就可以唤醒后继等待结点。例如，资源总量是13，A（5）和B（7）分别获取到资源并发运行，C（4）来时只剩1个资源就需要等待。A在运行过程中释放掉2个资源量，然后tryReleaseShared(2)返回true唤醒C，C一看只有3个仍不够继续等待；随后B又释放2个，tryReleaseShared(2)返回true唤醒C，C一看有5个够自己用了，然后C就可以跟A和B一起运行。而ReentrantReadWriteLock读锁的tryReleaseShared()只有在完全释放掉资源（state=0）才返回true，所以自定义同步器可以根据需要决定tryReleaseShared()的返回值。

### doReleaseShared()

此方法主要用于唤醒后继。下面是它的源码：

```java
private void doReleaseShared() {
    /*
     * 如果head需要通知下一个节点，调用unparkSuccessor
     * 如果不需要通知，需要在释放后把waitStatus改为PROPAGATE来继续传播
     * 此外，我们必须通过自旋来CAS以防止操作时有新节点加入
     * 另外，不同于其他unparkSuccessor的用途，我们需要知道CAS设置状态失败的情况，
     * 以便进行重新检查。
     */
    for (;;) {
        //唤醒操作由头结点开始，注意这里的头节点已经是上面新设置的头结点了
        //其实就是唤醒上面新获取到共享锁的节点的后继节点
        Node h = head;
        if (h != null && h != tail) {
            int ws = h.waitStatus;
            //表示后继节点需要被唤醒
            if (ws == Node.SIGNAL) {
                //这里需要控制并发，因为入口有setHeadAndPropagate跟release两个，避免两次unpark
                if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                    continue;           
                //执行唤醒操作
                unparkSuccessor(h);
            }
            //如果后继节点暂时不需要唤醒，则把当前节点状态设置为PROPAGATE确保以后可以传递下去
            else if (ws == 0 &&
                    !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                continue;               
        }
        //如果头结点没有发生变化，表示设置完成，退出循环
        //如果头结点发生变化，比如说其他线程获取到了锁，为了使自己的唤醒动作可以传递，必须进行重试
        if (h == head)                   
            break;
    }
}
```

## 3.5、Condition

在没有Lock之前，我们使用synchronized来控制同步，配合Object的wait()、notify()系列方法可以实现等待/通知模式。在Java SE5后，Java提供了Lock接口，相对于Synchronized而言，Lock提供了条件Condition，对线程的等待、唤醒操作更加详细和灵活。

Condition提供了一系列的方法来对阻塞和唤醒线程：

```java
public interface Condition {

    /**
     * 造成当前线程在接到信号或被中断之前一直处于等待状态。
     */
    void await() throws InterruptedException;

    /**
     * 造成当前线程在接到信号之前一直处于等待状态。【注意：该方法对中断不敏感】。
     */
    void awaitUninterruptibly();

    /**
     * 造成当前线程在接到信号、被中断或到达指定等待时间之前一直处于等待状态。
     * 返回值表示剩余时间，如果在nanosTimesout之前唤醒，那么返回值 = nanosTimeout - 消耗时间，
     * 如果返回值 <= 0 ,则可以认定它已经超时了。
     */
    long awaitNanos(long nanosTimeout) throws InterruptedException;

    /**
     * 造成当前线程在接到信号、被中断或到达指定等待时间之前一直处于等待状态。
     */
    boolean await(long time, TimeUnit unit) throws InterruptedException;

    /**
     * 造成当前线程在接到信号、被中断或到达指定最后期限之前一直处于等待状态。
     * 如果没有到指定时间就被通知，则返回true，否则表示到了指定时间，返回返回false。
     */
    boolean awaitUntil(Date deadline) throws InterruptedException;

    /**
     * 唤醒一个等待线程。该线程从等待方法返回前必须获得与Condition相关的锁。
     */
    void signal();

    /**
     * 唤醒所有等待线程。能够从等待方法返回的线程必须获得与Condition相关的锁。
     */
    void signalAll();
}
```

Condition是一种广义上的条件队列。他为线程提供了一种更为灵活的等待/通知模式，线程在调用await方法后执行挂起操作，直到线程等待的某个条件为真时才会被唤醒。Condition必须要配合锁一起使用，因为对共享状态变量的访问发生在多线程环境下。一个Condition的实例必须与一个Lock绑定，因此Condition一般都是作为Lock的内部实现。

我们这里要说的Condition其实就是AQS的一个内部类：ConditionObject

```java
public class ConditionObject implements Condition, java.io.Serializable {
    //头节点
    private transient Node firstWaiter;
    //尾节点
    private transient Node lastWaiter;
}
```

每个Condition对象都包含着一个FIFO队列，该队列是Condition对象通知/等待功能的关键。在队列中每一个节点都包含着一个线程引用，该线程就是在该Condition对象上等待的线程。Condition拥有首节点（firstWaiter），尾节点（lastWaiter）。当前线程调用await()方法，将会以当前线程构造成一个节点（Node），并将节点加入到该队列的尾部。

![img](https://benjaminwhx.com/images/AQS3.jpg)

Condition的队列结构比CLH同步队列的结构简单些，新增过程较为简单只需要将原尾节点的nextWaiter指向新增节点，然后更新lastWaiter即可。

因为大多数方法都差不多，我们这里只重点讲解await和signal方法。我们来看看源码是如何实现的。

### await()

调用Condition的await()方法会使当前线程进入等待状态，同时会加入到Condition等待队列同时释放锁。当从await()方法返回时，当前线程一定是获取了Condition相关连的锁。

```java
public final void await() throws InterruptedException {
    // 当前线程中断
    if (Thread.interrupted())
        throw new InterruptedException();
    //当前线程加入等待队列
    Node node = addConditionWaiter();
    //释放锁，返回释放之前的状态
    int savedState = fullyRelease(node);
    int interruptMode = 0;
    /**
     * 检测此节点的线程是否在Sync队列里，如果不在，则说明该线程还不具备竞争锁的资格，则继续等待
     * 直到检测到此节点在Sync队列里
     */
    while (!isOnSyncQueue(node)) {
        //线程挂起
        LockSupport.park(this);
        //如果已经中断了，则退出循环
        if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
            break;
    }
    //竞争同步状态
    if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
        interruptMode = REINTERRUPT;
    //清理下条件队列中的不是在等待条件的节点
    if (node.nextWaiter != null) // clean up if cancelled
        unlinkCancelledWaiters();
    if (interruptMode != 0)    // 对中断状态进行判断，是需要抛异常还是重置中断位
        reportInterruptAfterWait(interruptMode);
}

private Node addConditionWaiter() {
    Node t = lastWaiter;
    // 检查队尾的节点的状态，清理掉CANCELLED的节点。
    if (t != null && t.waitStatus != Node.CONDITION) {
        unlinkCancelledWaiters();
        t = lastWaiter;    // 重新获取lastWaiter
    }
    // 新建一个CONDITION节点放到队尾
    Node node = new Node(Thread.currentThread(), Node.CONDITION);
    if (t == null)
        firstWaiter = node;
    else
        t.nextWaiter = node;
    lastWaiter = node;
    return node;
}

/**
 * 释放当前状态值，返回已保存的状态
 */
final int fullyRelease(Node node) {
    boolean failed = true;
    try {
        int savedState = getState();
        if (release(savedState)) {
            failed = false;
            return savedState;
        } else {
            throw new IllegalMonitorStateException();
        }
    } finally {
        if (failed)
            node.waitStatus = Node.CANCELLED;
    }
}

/**
 * 是不是在Sync队列里
 */
final boolean isOnSyncQueue(Node node) {
    if (node.waitStatus == Node.CONDITION || node.prev == null)
        return false;
    if (node.next != null) // 这种情况node一定在Sync队列中
        return true;
    // 从队尾往前找node，找到返回true，否则返回false(这种情况很少发生)
    return findNodeFromTail(node);
}
```

总结一下，await其实就做了几件事：

1. 将当前线程的Condition节点放入等待队列中。
2. 释放Sync中的锁。
3. 确认节点不在Sync中了，调用park挂起线程。等待signal唤醒。
4. 唤醒后竞争锁。

> 注：await不会消化中断异常，如果在await前就interrupt了，在await第一行就会抛出异常。而在await中进行interrupt的话，也会抛出异常。

### signal()

调用Condition的signal()方法，将会唤醒在等待队列中等待最长时间的节点（条件队列里的首节点），在唤醒节点前，会将节点移到CLH同步队列中。

```java
public final void signal() {
    //检测当前线程是否为拥有独占锁
    if (!isHeldExclusively())
        throw new IllegalMonitorStateException();
    //头节点，唤醒条件队列中的第一个节点
    Node first = firstWaiter;
    if (first != null)
        doSignal(first);
}

private void doSignal(Node first) {
    do {
        //修改头结点，完成旧头结点的移出工作
        if ( (firstWaiter = first.nextWaiter) == null)
            lastWaiter = null;
        first.nextWaiter = null;
    } while (!transferForSignal(first) &&    //将节点移动到CLH同步队列中
            (first = firstWaiter) != null);
}

final boolean transferForSignal(Node node) {
    if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
        return false;    // CAS失败，说明node已经被CANCELLED了

    //将节点加入到syn队列中去，返回的是syn队列中node节点前面的一个节点
    Node p = enq(node);
    int ws = p.waitStatus;
    //如果结点p的状态为cancel 或者修改waitStatus失败，则直接唤醒
    if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
        LockSupport.unpark(node.thread);
    return true;
}
```

可以发现，signal主要是拿到Condition队列里的第一个节点并调用doSignal方法，主要做了以下几件事：

1. 修改节点waitStatus（把要唤醒的节点waitStatus从CONDITION改为0）。
2. 把节点放入syn队列中（这样该节点就可以竞争锁了）。
3. 如果节点在Sync队列的前一个节点状态是CANCELLED，或者把状态改为SIGNAL时失败了，立即唤醒当前节点竞争锁（如果不这么做，这么节点将永远不会被唤醒去竞争锁，导致一直等待）。

signalAll其实就是多次signal。它从Condition队列的首节点一直遍历到最后去调用transferForSignal，这里就不讲了。

### 使用Condition

我们可以使用Condition实现顺序执行

```java
public class SyncConditionTest {

    private static Lock lock = new ReentrantLock();
    private static final Condition CONDITION_A = lock.newCondition();
    private static final Condition CONDITION_B = lock.newCondition();
    private static final Condition CONDITION_C = lock.newCondition();
    private static volatile int nextPrintWho = 1;

    public static void main(String[] args) {
        Thread threadA = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.lock();
                    while (nextPrintWho != 1) {
                        CONDITION_A.await();
                    }
                    System.out.println("ThreadA");
                    nextPrintWho = 2;
                    CONDITION_B.signalAll();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        });
        Thread threadB = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.lock();
                    while (nextPrintWho != 2) {
                        CONDITION_B.await();
                    }
                    System.out.println("ThreadB");
                    nextPrintWho = 3;
                    CONDITION_C.signalAll();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        });
        Thread threadC = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.lock();
                    while (nextPrintWho != 3) {
                        CONDITION_C.await();
                    }
                    System.out.println("ThreadC");
                    nextPrintWho = 1;
                    CONDITION_C.signalAll();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        });
        Thread[] aArray = new Thread[2];
        Thread[] bArray = new Thread[2];
        Thread[] cArray = new Thread[2];
        for (int i = 0; i < 2; i++) {
            aArray[i] = new Thread(threadA);
            bArray[i] = new Thread(threadB);
            cArray[i] = new Thread(threadC);
            aArray[i].start();
            bArray[i].start();
            cArray[i].start();
        }
    }
}
```

输出结果：

```sql
ThreadA
ThreadB
ThreadC
ThreadA
ThreadB
ThreadC
```

## 3.6、小结

本节我们详解了独占和共享两种模式下获取-释放资源(acquire-release、acquireShared-releaseShared)以及Condition的源码，相信大家都有一定认识了。

值得注意的是，acquire()和acquireSahred()两种方法下，线程在等待队列中都是忽略中断的。AQS也支持响应中断的，acquireInterruptibly()/acquireSharedInterruptibly()即是，这里相应的源码跟acquire()和acquireSahred()差不多，这里就不再详解了。

# 4、Mutex（互斥锁）

Mutex是一个不可重入的互斥锁实现。锁资源（AQS里的state）只有两种状态：0表示未锁定，1表示锁定。下边是Mutex的核心源码：

```java
class Mutex implements Lock, java.io.Serializable {
    // 自定义同步器
    private static class Sync extends AbstractQueuedSynchronizer {
        // 判断是否锁定状态
        protected boolean isHeldExclusively() {
            return getState() == 1;
        }

        // 尝试获取资源，立即返回。成功则返回true，否则false。
        public boolean tryAcquire(int acquires) {
            assert acquires == 1; // 这里限定只能为1个量
            if (compareAndSetState(0, 1)) {//state为0才设置为1，不可重入！
                setExclusiveOwnerThread(Thread.currentThread());//设置为当前线程独占资源
                return true;
            }
            return false;
        }

        // 尝试释放资源，立即返回。成功则为true，否则false。
        protected boolean tryRelease(int releases) {
            assert releases == 1; // 限定为1个量
            if (getState() == 0)//既然来释放，那肯定就是已占有状态了。只是为了保险，多层判断！
                throw new IllegalMonitorStateException();
            setExclusiveOwnerThread(null);
            setState(0);//释放资源，放弃占有状态
            return true;
        }
    }

    // 真正同步类的实现都依赖继承于AQS的自定义同步器！
    private final Sync sync = new Sync();

    //lock<-->acquire。两者语义一样：获取资源，即便等待，直到成功才返回。
    public void lock() {
        sync.acquire(1);
    }

    //tryLock<-->tryAcquire。两者语义一样：尝试获取资源，要求立即返回。成功则为true，失败则为false。
    public boolean tryLock() {
        return sync.tryAcquire(1);
    }

    //unlock<-->release。两者语文一样：释放资源。
    public void unlock() {
        sync.release(1);
    }

    //锁是否占有状态
    public boolean isLocked() {
        return sync.isHeldExclusively();
    }
}
```

同步类在实现时一般都将自定义同步器（sync）定义为内部类，供自己使用；而同步类自己（Mutex）则实现某个接口，对外服务。当然，接口的实现要直接依赖sync，它们在语义上也存在某种对应关系！！而sync只用实现资源state的获取-释放方式tryAcquire-tryRelelase，至于线程的排队、等待、唤醒等，上层的AQS都已经实现好了，我们不用关心。

除了Mutex，ReentrantLock/CountDownLatch/Semphore这些同步类的实现方式都差不多，不同的地方就在获取-释放资源的方式tryAcquire-tryRelelase。掌握了这点，AQS的核心便被攻破了！

OK，至此，整个AQS的讲解也要落下帷幕了。希望本文能够对学习Java并发编程的同学有所借鉴，中间写的有不对的地方，也欢迎讨论和指正~