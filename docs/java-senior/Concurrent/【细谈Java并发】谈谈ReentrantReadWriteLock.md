### 【细谈Java并发】谈谈ReentrantReadWriteLock

转载自[【细谈Java并发】谈谈ReentrantReadWriteLock](https://benjaminwhx.com/2018/05/02/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88ReentrantReadWriteLock/)

我们今天来讨论一下ReentrantReadWriteLock，它的读锁利用了AQS中的共享锁机制以及写锁利用了AQS中的独占锁机制。读本文前建议先阅读AQS：[【细谈Java并发】谈谈AQS](./%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88AQS/) 以及ReentrantLock [【细谈Java并发】谈谈ReentrantLock](./%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88ReentrantLock/)。

# 1、简介

ReentrantReadWriteLock出现的目的就是针对ReentrantLock独占带来的性能问题，使用ReentrantLock无论是“写/写”线程、“读/读”线程、“读/写”线程之间的工作都是互斥，同时只有一个线程能进入同步区域。然而大多实际场景是“读/读”线程间并不存在互斥关系，只有”读/写”线程或”写/写”线程间的操作需要互斥的。因此引入ReentrantReadWriteLock，它的特性是：**一个资源可以被多个读操作访问，或者一个写操作访问，但两者不能同时进行。从而提高读操作的吞吐量。**

# 2、框架

ReentrantReadWriteLock实现了ReadWriteLock接口来提供读写锁服务

```java
public interface ReadWriteLock {
    /**
     * 返回读锁
     */
    Lock readLock();

    /**
     * 返回写锁
     */
    Lock writeLock();
}
```

它拥有了和ReentrantLock类似的公平锁和非公平锁（默认构造方法是非公平锁）

```java
private final ReentrantReadWriteLock.ReadLock readerLock;
private final ReentrantReadWriteLock.WriteLock writerLock;

public ReentrantReadWriteLock() {
    this(false);
}

public ReentrantReadWriteLock(boolean fair) {
    sync = fair ? new FairSync() : new NonfairSync();
    readerLock = new ReadLock(this);
    writerLock = new WriteLock(this);
}

public ReentrantReadWriteLock.WriteLock writeLock() { return writerLock; }
public ReentrantReadWriteLock.ReadLock  readLock()  { return readerLock; }
```

我们可以通过下面的方式构造一个读的非公平锁以及一个写的非公平锁

```java
ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
```

ReentrantReadWriteLock有如下特性：

1. 可重入性：同一个线程的读读、写读、写写都可以重入，但是读写不可以重入。
2. 锁降级：允许写锁降级为读锁。
3. 中断锁的获取：在读锁和写锁的获取过程中支持中断。
4. 支持Condition：写锁提供Condition实现
5. 公平锁、非公平锁模式。
6. 监控：提供确定锁是否被持有等辅助方法。

好了，我们通过内部源码的分析来揭开它的神秘面纱吧。

# 3、源码分析

## 3.1、读写锁

```java
public static class ReadLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = -5992448646407690164L;
    private final Sync sync;

    protected ReadLock(ReentrantReadWriteLock lock) { sync = lock.sync; }

    public void lock() { sync.acquireShared(1); }

    public void lockInterruptibly() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    public boolean tryLock() { return sync.tryReadLock(); }

    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    public void unlock() { sync.releaseShared(1); }

    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}

public static class WriteLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = -4992448646407690164L;
    private final Sync sync;

    protected WriteLock(ReentrantReadWriteLock lock) { sync = lock.sync; }

    public void lock() { sync.acquire(1); }

    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    public boolean tryLock( ) { return sync.tryWriteLock(); }

    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    public void unlock() { sync.release(1); }

    public Condition newCondition() { return sync.newCondition(); }

    public boolean isHeldByCurrentThread() { return sync.isHeldExclusively(); }

    public int getHoldCount() { return sync.getWriteHoldCount(); }
}
```

可以看到，读锁用到的是AQS里的共享锁的相关方法，而写锁用到的是AQS里的独占锁的相关方法。

## 3.2、Sync

因为ReentrantReadWriteLock中的读写操作都间接的使用Sync来实现的，而Sync是AQS的一个实现类，我们来看看看它的重要属性和方法。

### 属性

```java
// 表示读锁占用的位数
static final int SHARED_SHIFT   = 16;
// 增加一个读锁，按照上述设计，就相当于增加 SHARED_UNIT
static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
// 表示申请读锁最大的线程数量，为65535
static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
// 低16位的MASK，用来计算写锁的数值
static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

/** 返回共享锁数  */
static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }
/** 返回独占锁数  */
static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }
```

ReentrantReadWriterLock使用一个32位的int类型来表示锁被占用的线程数（ReentrantLock中的state），它用高16位用来表示读锁占有的线程数量，用低16位表示写锁被同一个线程申请的次数。

![img](https://benjaminwhx.com/images/readwritelock1.png)

上面的sharedCount方法原理是让32位的数右移16位，看起来好像把高16位放在了低16位上，然后清空高16位。

而exclusiveCount相当于用c和EXCLUSIVE_MASK进行与操作得到低16位的值。

举个例子：比如当前申请读锁的线程数为13个，写锁一个，那state怎么表示？

上文说过，用一个32位的int类型的高16位表示读锁线程数，13的二进制为 1101,那state的二进制表示为

`00000000 00001101 00000000 00000001`，十进制数为851969， 接下在具体获取锁时，需要根据这个851968这个值得出上文中的 13 与 1。要算成13，只需要将state 无符号向左移位16位置，得出`00000000 00001101`，就得出13，根据851969要算成低16位置，只需要用该`00000000 00001101 00000000 00000001 & 111111111111111`（15位），就可以得出`00000001`,就是利用了 `1&1` 得1, `1&0` 得0这个技巧。

我们再关注几个与线程本地变量相关的属性：

```java
// 本地变量里读锁的线程
private transient ThreadLocalHoldCounter readHolds;
// 缓存的是最后一个获取线程的HolderCount信息，主要减少从readHolds中获取HoldCounter的次数
private transient HoldCounter cachedHoldCounter;
// 保存第一个获取读锁的线程
private transient Thread firstReader = null;
// 保存第一个获取读锁的线程总共获取读锁的数量
private transient int firstReaderHoldCount;
```

说完属性后，我们再来说说Sync的两个实现类：FairSync和NonfairSync，如果不懂公平锁和非公平锁的区别的话，先去了解一下相关概念。其实可以拿排队来类比，公平锁就是一个个排队等待，而非公平锁就是来一个人先去窗口问轮到自己没，如果这个时候办理的人刚好结束，这个人就可以插队进行办理（不道德的行为）。他们都可以重入，也就是如果你和办理业务的是一家人，可以一起办理业务。

### FairSync

公平锁里只有两个方法writerShouldBlock和readerShouldBlock。

```java
static final class FairSync extends Sync {
    private static final long serialVersionUID = -2274990926593161451L;
    final boolean writerShouldBlock() {
        return hasQueuedPredecessors();
    }
    final boolean readerShouldBlock() {
        return hasQueuedPredecessors();
    }
}
```

writerShouldBlock和readerShouldBlock方法都表示当有别的线程也在尝试获取锁时，是否应该阻塞。
对于公平模式，hasQueuedPredecessors()方法表示前面是否有等待线程。**一旦前面有等待线程，那么为了遵循公平，当前线程也就应该被挂起。**

### NonfairSync

非公平锁是ReentrantReadWriteLock里的默认锁。

```java
static final class NonfairSync extends Sync {
    private static final long serialVersionUID = -8159625535654395037L;
    final boolean writerShouldBlock() {
        return false; // writers can always barge
    }

    // 读锁是否应该阻塞(通过队列前面是否有写锁来判断)
    final boolean readerShouldBlock() {
        return apparentlyFirstQueuedIsExclusive();
    }
}

/**
 * 当head节点不为null且head节点的下一个节点s不为null
 * 且s是独占模式（写线程）且s的线程不为null时，返回true。
 */
final boolean apparentlyFirstQueuedIsExclusive() {
    Node h, s;
    return (h = head) != null &&
            (s = h.next)  != null &&
            !s.isShared()         &&
            s.thread != null;
}
```

从上面可以看到，非公平模式下，writerShouldBlock直接返回false，说明不需要阻塞；而readShouldBlock调用了apparentFirstQueuedIsExcluisve()方法。该方法的**目的是为了防止写线程饥饿等待，如果同步队列中的第一个线程是以独占模式获取锁（写锁），那么当前获取读锁的线程需要阻塞，让队列中的第一个线程先执行。**（这就好比排队的时候你插队，但是你得看看有没有人不爽你的，有的话还是老老实实排队去吧~）

### 主要方法

我们来说说Sync里的主要方法：

1. tryAcquire(int)：尝试获取写锁。成功返回true，反之返回false。
2. tryRelease(int)：尝试释放写锁。成功返回true，反之返回false。
3. tryAcquireShared(int)
4. tryReleaseShared(int)

#### tryAcquire(int)

```java
protected final boolean tryAcquire(int acquires) {
    Thread current = Thread.currentThread();
    int c = getState();
    int w = exclusiveCount(c);    //获取独占锁的重入数
    if (c != 0) {
        /**
         * 有两种情况直接返回false
         * 1.c != 0 and w == 0 表示分配了读锁
         * 2.w != 0 && current != getExclusiveOwnerThread() 表示其他线程获取了写锁。
         */
        if (w == 0 || current != getExclusiveOwnerThread())
            return false;
        // 写锁重入，检测是否超过最大重入次数。
        if (w + exclusiveCount(acquires) > MAX_COUNT)
            throw new Error("Maximum lock count exceeded");
        // 更新写锁重入次数，写锁在低位，直接加上 acquire 即可。
        setState(c + acquires);
        return true;
    }
    // writerShouldBlock 留给子类实现，用于实现公平性策略。
    // 如果允许获取写锁，则用 CAS 更新状态。
    if (writerShouldBlock() ||
            !compareAndSetState(c, c + acquires))
        return false;
    // 没有任何线程持有锁，设置当前线程为owner thread
    setExclusiveOwnerThread(current);
    return true;
}
```

这个方法很简单，总结一下只有下面几种情况能够立刻获得写锁：

1. 写锁重入。
2. 当前没有任何线程持有锁。

#### tryRelease(int)

```java
protected final boolean tryRelease(int releases) {
    if (!isHeldExclusively())    // 是否是当前线程持有写锁
        throw new IllegalMonitorStateException();
    // 这里不考虑高16位是因为高16位肯定是 0。
    int nextc = getState() - releases;
    boolean free = exclusiveCount(nextc) == 0;
    if (free)
        setExclusiveOwnerThread(null);    // 写锁完全释放，设置独占线程为null。
    setState(nextc);
    return free;
}

/**
 * 当前线程是否占有写锁。
 */
protected final boolean isHeldExclusively() {
    return getExclusiveOwnerThread() == Thread.currentThread();
}
```

释放锁的逻辑还是比较简单的，具体分为以下几步：

1. 判断当前线程是否持有写锁。
2. 设置更改后的state值。如果state变为0，设置独占线程为null。

#### tryAcquireShared(int)

```java
// 参数变为 unused 是因为读锁的重入计数是内部维护的
protected final int tryAcquireShared(int unused) {
    Thread current = Thread.currentThread();
    int c = getState();
    // 这个if语句是说：持有写锁的线程可以获取读锁。(锁降级)
    if (exclusiveCount(c) != 0 &&    // 已分配了写锁
            getExclusiveOwnerThread() != current)    // 且当前线程不是持有写锁的线程
        return -1;
    int r = sharedCount(c); // 取读锁计数

    if (!readerShouldBlock() &&    // 由子类根据其公平策略决定是否允许获取读锁
            r < MAX_COUNT &&    // 读锁数量还没达到最大值
            compareAndSetState(c, c + SHARED_UNIT)) {
        // 成功获取读锁
        // 注意下面对firstReader的处理：firstReader是不会放到readHolds里的
        // 这样，在读锁只有一个的情况下，就避免了查找readHolds。
        if (r == 0) {    // 是 firstReader，计数不会放入  readHolds。
            firstReader = current;
            firstReaderHoldCount = 1;
        } else if (firstReader == current) {    // firstReader 重入
            // 如果第一个获取读锁的对象为当前对象，将firstReaderHoldCount 加一
            firstReaderHoldCount++;
        } else {
            // 非 firstReader 读锁重入计数更新
            HoldCounter rh = cachedHoldCounter;    // 首先访问缓存
            if (rh == null || rh.tid != getThreadId(current))
                cachedHoldCounter = rh = readHolds.get();
            else if (rh.count == 0)
                readHolds.set(rh);
            rh.count++;
        }
        return 1;
    }
    // 获取读锁失败，放到循环里重试。
    return fullTryAcquireShared(current);
}

final int fullTryAcquireShared(Thread current) {
    HoldCounter rh = null;
    for (;;) {
        int c = getState();
        if (exclusiveCount(c) != 0) {
            if (getExclusiveOwnerThread() != current)
                // 写锁被分配，非写锁线程获取读锁，失败
                return -1;
                // 否则，当前线程持有写锁，在这里阻塞将导致死锁。
        }
        //如果读线程需要阻塞
        else if (readerShouldBlock()) {
            // Make sure we're not acquiring read lock reentrantly
            if (firstReader == current) {
                // assert firstReaderHoldCount > 0;
            }
            //说明有别的读线程占有了锁
            else {
                if (rh == null) {
                    rh = cachedHoldCounter;
                    if (rh == null || rh.tid != getThreadId(current)) {
                        rh = readHolds.get();
                        if (rh.count == 0)
                            readHolds.remove();
                    }
                }
                // 需要阻塞且是非重入(还未获取读锁的)，获取失败。
                if (rh.count == 0)
                    return -1;
            }
        }
        //如果读锁达到了最大值，抛出异常
        if (sharedCount(c) == MAX_COUNT)
            throw new Error("Maximum lock count exceeded");
        //如果成功更改状态，成功返回
        if (compareAndSetState(c, c + SHARED_UNIT)) {
            // 申请读锁成功，下面的处理跟tryAcquireShared是类似的。
            if (sharedCount(c) == 0) {
                firstReader = current;
                firstReaderHoldCount = 1;
            } else if (firstReader == current) {
                firstReaderHoldCount++;
            } else {
                if (rh == null)
                    rh = cachedHoldCounter;
                if (rh == null || rh.tid != getThreadId(current))
                    rh = readHolds.get();
                else if (rh.count == 0)
                    readHolds.set(rh);
                rh.count++;
                cachedHoldCounter = rh; // cache for release
            }
            return 1;
        }
    }
}
```

下面通过一张图来说明这两个方法（读锁-读锁的情况这里没有，因为不会加入到队列）：

![img](https://benjaminwhx.com/images/readwritelock2.jpg)

上图左边代表序号，head指向的是正在运行的线程锁类型。可以发现3和5能够正常获取到锁。

3的情况是A线程持有写锁，这时候A线程要获得读锁，造成了锁降级。

5的情况是A线程持有读锁，这时候A线程要获得读锁，因为可重入锁的缘故，所以能够正常获取。

#### tryReleaseShared(int)

```java
// 参数变为 unused 是因为读锁的重入计数是内部维护的
protected final boolean tryReleaseShared(int unused) {
    Thread current = Thread.currentThread();
    // 清理firstReader缓存 或 readHolds里的重入计数
    if (firstReader == current) {
        // assert firstReaderHoldCount > 0;
        if (firstReaderHoldCount == 1)
            firstReader = null;
        else
            firstReaderHoldCount--;
    } else {
        HoldCounter rh = cachedHoldCounter;
        if (rh == null || rh.tid != getThreadId(current))
            rh = readHolds.get();
        int count = rh.count;
        if (count <= 1) {
            // 完全释放读锁
            readHolds.remove();
            if (count <= 0)
                throw unmatchedUnlockException();
        }
        --rh.count;    // 主要用于重入退出
    }
    // 循环在CAS更新状态值，主要是把读锁数量减 1
    for (;;) {
        int c = getState();
        int nextc = c - SHARED_UNIT;
        if (compareAndSetState(c, nextc))
            // 释放读锁对其他读线程没有任何影响，
            // 但可以允许等待的写线程继续，如果读锁、写锁都空闲。
            return nextc == 0;
    }
}
```

# 4、作用

## 缓存

我们通过官方的一个例子，来说明如何使用读写锁来进行缓存管理。

```java
class CachedData {
    Object data;
    volatile boolean cacheValid;
    ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

    void processCachedData() {
        rwl.readLock().lock();
        if (!cacheValid) {
            // Must release read lock before acquiring write lock  
            rwl.readLock().unlock();
            rwl.writeLock().lock();
            // Recheck state because another thread might have acquired  
            //   write lock and changed state before we did.  
            if (!cacheValid) {
                data = ...
                cacheValid = true;
            }
            // Downgrade by acquiring read lock before releasing write lock  
            rwl.readLock().lock();
            rwl.writeLock().unlock(); // Unlock write, still hold read  
        }
        use(data);
        rwl.readLock().unlock();
    }
}
```

cacheValid变量使用volatile修饰保证了多线程变量值修改的一致性，代码很简单，就不详说了。

## treeMap

当数据操作较大或者read操作明显多于write操作时，由于readLock的不阻塞性质使得ReentrantReadWriteLock效率明显高于synchronized

```java
class RWDictionary {
    private final Map m = new TreeMap();
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();

    public Data get(String key) {
        r.lock();
        try { return m.get(key); }
        finally { r.unlock(); }
    }
    public String[] allKeys() {
        r.lock();
        try { return m.keySet().toArray(); }
        finally { r.unlock(); }
    }
    public Data put(String key, Data value) {
        w.lock();
        try { return m.put(key, value); }
        finally { w.unlock(); }
    }
    public void clear() {
        w.lock();
        try { m.clear(); }
        finally { w.unlock(); }
    }
}
```