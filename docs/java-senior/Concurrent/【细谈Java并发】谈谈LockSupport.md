### 【细谈Java并发】谈谈LockSupport

转载自[【细谈Java并发】谈谈LockSupport](https://benjaminwhx.com/2018/05/01/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88LockSupport/)

# 1、简介

LockSupport 和 CAS 是Java并发包中很多并发工具控制机制的基础，它们底层其实都是依赖Unsafe实现。

LockSupport是用来创建锁和其他同步类的基本**线程阻塞**原语。LockSupport 提供park()和unpark()方法实现阻塞线程和解除线程阻塞，LockSupport和每个使用它的线程都与一个许可(permit)关联。permit相当于1，0的开关，默认是0，调用一次unpark就加1变成1，调用一次park会消费permit, 也就是将1变成0，同时park立即返回。再次调用park会变成block（因为permit为0了，会阻塞在这里，直到permit变为1）, 这时调用unpark会把permit置为1。每个线程都有一个相关的permit, permit最多只有一个，重复调用unpark也不会积累。

park()和unpark()不会有 `Thread.suspend` 和 `Thread.resume` 所可能引发的死锁问题，由于许可的存在，调用 park 的线程和另一个试图将其 unpark 的线程之间的竞争将保持活性。

如果调用线程被中断，则park方法会返回。同时park也拥有可以设置超时时间的版本。

三种形式的 park 还各自支持一个 blocker 对象参数。此对象在线程受阻塞时被记录，以允许监视工具和诊断工具确定线程受阻塞的原因。（这样的工具可以使用方法 getBlocker(java.lang.Thread) 访问 blocker。）建议最好使用这些形式，而不是不带此参数的原始形式。在锁实现中提供的作为 blocker 的普通参数是 this。
看下线程dump的结果来理解blocker的作用。

![img](https://benjaminwhx.com/images/locksupport1.png)

从线程dump结果可以看出：
有blocker的可以传递给开发人员更多的现场信息，通过jstack命令可以非常方便的监控具体的阻塞对象，方便定位问题。所以java6新增加带blocker入参的系列park方法，替代原有的park方法。

看一个Java docs中的示例用法：一个先进先出非重入锁类的框架

```java
class FIFOMutex {
    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final Queue<Thread> waiters
      = new ConcurrentLinkedQueue<Thread>();

    public void lock() {
      boolean wasInterrupted = false;
      Thread current = Thread.currentThread();
      waiters.add(current);

      // Block while not first in queue or cannot acquire lock
      while (waiters.peek() != current ||
             !locked.compareAndSet(false, true)) {
        LockSupport.park(this);
        if (Thread.interrupted()) // ignore interrupts while waiting
          wasInterrupted = true;
      }

      waiters.remove();
      if (wasInterrupted)          // reassert interrupt status on exit
        current.interrupt();
    }

    public void unlock() {
      locked.set(false);
      LockSupport.unpark(waiters.peek());
    }
  }
```

# 2、Unsafe的park和unpark

LockSupport类是Java6(JSR166-JUC)引入的一个类，提供了基本的线程同步原语。LockSupport实际上是调用了Unsafe类里的函数，归结到Unsafe里，只有两个函数：

```java
/**
 * 为指定线程提供“许可(permit)”
 */
public native void unpark(Thread jthread);

/**
 * 阻塞指定时间等待“许可”。
 * @param isAbsolute: 时间是绝对的，还是相对的
 * @param time：等待许可的时间
 */
public native void park(boolean isAbsolute, long time);
```

上面的这个“许可”是不能叠加的，“许可”是一次性的。

比如线程B连续调用了三次unpark函数，当线程A调用park函数就使用掉这个“许可”，如果线程A再次调用park，则进入等待状态。

**注意，unpark函数可以先于park调用。比如线程B调用unpark函数，给线程A发了一个“许可”，那么当线程A调用park时，它发现已经有“许可”了，那么它会马上再继续运行。**

可能有些朋友还是不理解“许可”这个概念，我们深入HotSpot的源码来看看。

每个java线程都有一个Parker实例，Parker类是这样定义的：

```java
class Parker : public os::PlatformParker {  
private:  
  volatile int _counter ;  
  ...  
public:  
  void park(bool isAbsolute, jlong time);  
  void unpark();  
  ...  
}  
class PlatformParker : public CHeapObj<mtInternal> {  
  protected:  
    pthread_mutex_t _mutex [1] ;  
    pthread_cond_t  _cond  [1] ;  
    ...  
}
```

可以看到Parker类实际上用Posix的mutex，condition来实现的。在Parker类里的_counter字段，就是用来记录所谓的“许可”的。

当调用park时，先尝试直接能否直接拿到“许可”，即`_counter>0`时，如果成功，则把`_counter`设置为0,并返回：

```java
void Parker::park(bool isAbsolute, jlong time) {  
  // Ideally we'd do something useful while spinning, such  
  // as calling unpackTime().  


  // Optional fast-path check:  
  // Return immediately if a permit is available.  
  // We depend on Atomic::xchg() having full barrier semantics  
  // since we are doing a lock-free update to _counter.  
  if (Atomic::xchg(0, &_counter) > 0) return;
```

如果不成功，则构造一个ThreadBlockInVM，然后检查`_counter`是不是>0，如果是，则把`_counter`设置为0，unlock mutex并返回：

```java
ThreadBlockInVM tbivm(jt);  
if (_counter > 0)  { // no wait needed  
  _counter = 0;  
  status = pthread_mutex_unlock(_mutex);
```

否则，再判断等待的时间，然后再调用pthread_cond_wait函数等待，如果等待返回，则把_counter设置为0，unlock mutex并返回：

```java
if (time == 0) {  
  status = pthread_cond_wait (_cond, _mutex) ;  
}  
_counter = 0 ;  
status = pthread_mutex_unlock(_mutex) ;  
assert_status(status == 0, status, "invariant") ;  
OrderAccess::fence();
```

当unpark时，则简单多了，直接设置`_counter`为1，再unlock mutext返回。如果`_counter`之前的值是0，则还要调用pthread_cond_signal唤醒在park中等待的线程：

```java
void Parker::unpark() {  
  int s, status ;  
  status = pthread_mutex_lock(_mutex);  
  assert (status == 0, "invariant") ;  
  s = _counter;  
  _counter = 1;  
  if (s < 1) {  
     if (WorkAroundNPTLTimedWaitHang) {  
        status = pthread_cond_signal (_cond) ;  
        assert (status == 0, "invariant") ;  
        status = pthread_mutex_unlock(_mutex);  
        assert (status == 0, "invariant") ;  
     } else {  
        status = pthread_mutex_unlock(_mutex);  
        assert (status == 0, "invariant") ;  
        status = pthread_cond_signal (_cond) ;  
        assert (status == 0, "invariant") ;  
     }  
  } else {  
    pthread_mutex_unlock(_mutex);  
    assert (status == 0, "invariant") ;  
  }  
}
```

简而言之，是用mutex和condition保护了一个_counter的变量，当park时，这个变量置为了0，当unpark时，这个变量置为1。

**值得注意的是在park函数里，调用pthread_cond_wait时，并没有用while来判断，所以posix condition里的”Spurious wakeup”一样会传递到上层Java的代码里。**关于”Spurious wakeup”，可以参考：[并行编程之条件变量（posix condition variables）](http://blog.csdn.net/hengyunabc/article/details/27969613)

# 3、LockSupport源码分析

解释完Unsafe的park和unpark的实现原理，我们再来看LockSupport的源码时就会异常清晰，因为不复杂，所以直接看注释吧。

```java
public class LockSupport {
    private LockSupport() {} // Cannot be instantiated.

    private static void setBlocker(Thread t, Object arg) {
        UNSAFE.putObject(t, parkBlockerOffset, arg);
    }

    /**
     * 返回提供给最近一次尚未解除阻塞的 park 方法调用的 blocker 对象。
     * 如果该调用不受阻塞，则返回 null。
     * 返回的值只是一个瞬间快照，即由于未解除阻塞或者在不同的 blocker 对象上受阻而具有的线程。
     */
    public static Object getBlocker(Thread t) {
        if (t == null)
            throw new NullPointerException();
        return UNSAFE.getObjectVolatile(t, parkBlockerOffset);
    }

    /**
     * 如果给定线程的许可尚不可用，则使其可用。
     * 如果线程在 park 上受阻塞，则它将解除其阻塞状态。
     * 否则，保证下一次调用 park 不会受阻塞。
     * 如果给定线程尚未启动，则无法保证此操作有任何效果。 
     * @param thread: 要执行 unpark 操作的线程；该参数为 null 表示此操作没有任何效果。
     */
    public static void unpark(Thread thread) {
        if (thread != null)
            UNSAFE.unpark(thread);
    }

    /**
     * 为了线程调度，在许可可用之前阻塞当前线程。 
     * 如果许可可用，则使用该许可，并且该调用立即返回；
     * 否则，为线程调度禁用当前线程，并在发生以下三种情况之一以前，使其处于休眠状态：
     *     1. 其他某个线程将当前线程作为目标调用 unpark
     *  2. 其他某个线程中断当前线程
     *  3. 该调用不合逻辑地（即毫无理由地）返回
     */
    public static void park() {
        UNSAFE.park(false, 0L);
    }

    /**
     * 和park()方法类似，不过增加了等待的相对时间
     */
    public static void parkNanos(long nanos) {
        if (nanos > 0)
            UNSAFE.park(false, nanos);
    }

    /**
     * 和park()方法类似，不过增加了等待的绝对时间
     */
    public static void parkUntil(long deadline) {
        UNSAFE.park(true, deadline);
    }

    /**
     * 和park()方法类似，只不过增加了暂停的同步对象
     * @param blocker 导致此线程暂停的同步对象
     * @since 1.6
     */
    public static void park(Object blocker) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        UNSAFE.park(false, 0L);
        setBlocker(t, null);
    }

    /**
     * parkNanos(long nanos)方法类似，只不过增加了暂停的同步对象
     * @param blocker 导致此线程暂停的同步对象
     * @since 1.6
     */
    public static void parkNanos(Object blocker, long nanos) {
        if (nanos > 0) {
            Thread t = Thread.currentThread();
            setBlocker(t, blocker);
            UNSAFE.park(false, nanos);
            setBlocker(t, null);
        }
    }

    /**
     * parkUntil(long deadline)方法类似，只不过增加了暂停的同步对象
     * @param blocker 导致此线程暂停的同步对象
     * @since 1.6
     */
    public static void parkUntil(Object blocker, long deadline) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        UNSAFE.park(true, deadline);
        setBlocker(t, null);
    }

    static final int nextSecondarySeed() {
        int r;
        Thread t = Thread.currentThread();
        if ((r = UNSAFE.getInt(t, SECONDARY)) != 0) {
            r ^= r << 13;   // xorshift
            r ^= r >>> 17;
            r ^= r << 5;
        }
        else if ((r = java.util.concurrent.ThreadLocalRandom.current().nextInt()) == 0)
            r = 1; // avoid zero
        UNSAFE.putInt(t, SECONDARY, r);
        return r;
    }

    // Hotspot implementation via intrinsics API
    private static final sun.misc.Unsafe UNSAFE;
    private static final long parkBlockerOffset;
    private static final long SEED;
    private static final long PROBE;
    private static final long SECONDARY;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            parkBlockerOffset = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("parkBlocker"));
            SEED = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("threadLocalRandomSeed"));
            PROBE = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("threadLocalRandomProbe"));
            SECONDARY = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("threadLocalRandomSecondarySeed"));
        } catch (Exception ex) { throw new Error(ex); }
    }
}
```

# 4、实践

看完LockSupport的源码，我们来动手写几个例子来验证一下猜想是否正确。

## 先park再unpark

```java
public class LockSupportTest {

    public static void main(String[] args) throws InterruptedException {
        String a = new String("A");
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("睡觉");
                LockSupport.park(a);
                System.out.println("起床");
            }
        });
        t.setName("A-Name");
        t.start();
        Thread.sleep(300000);
        System.out.println("妈妈喊我起床");
        LockSupport.unpark(t);
    }
}
```

输出结果：

```java
睡觉
妈妈喊我起床
起床
```

不过在等待的过程中，我们可以用jstack查看是否能够打印出检测的对象A，找到`A-Name`这个线程确实看到了等待一个String对象

```sql
~ jps
5589 LockSupportTest

~ jstack 5589
"A-Name" #11 prio=5 os_prio=31 tid=0x00007fc143009800 nid=0xa803 waiting on condition [0x000070000c233000]
   java.lang.Thread.State: WAITING (parking)
        at sun.misc.Unsafe.park(Native Method)
        - parking to wait for  <0x000000076adf4d30> (a java.lang.String)
        at java.util.concurrent.locks.LockSupport.park(LockSupport.java:175)
        at com.github.locksupport.LockSupportTest$1.run(LockSupportTest.java:18)
        at java.lang.Thread.run(Thread.java:745)
```

验证完unpark，接着我们来验证一下interrupt。

## 先interrupt再park

```java
public class LockSupportTest {

    public static void main(String[] args) throws InterruptedException {
        String a = new String("A");
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("睡觉");
                LockSupport.park(a);
                System.out.println("起床");
                System.out.println("是否中断：" + Thread.currentThread().isInterrupted());
            }
        });
        t.setName("A-Name");
        t.start();
        t.interrupt();
        System.out.println("突然肚子很疼");
    }
}
```

可以看到中断后执行park会直接执行下面的方法，并不会抛出`InterruptedException`，输出结果如下：

```sql
突然肚子很疼
睡觉
起床
是否中断：true
```

## 先unpark再park

```java
public class LockSupportTest {

    public static void main(String[] args) throws InterruptedException {
        String a = new String("A");
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("睡觉");
                LockSupport.park(a);
                System.out.println("7点到，起床");
            }
        });
        t.setName("A-Name");
        t.start();
        LockSupport.unpark(t);
        System.out.println("提前上好闹钟7点起床");
    }
}
```

按照上面说过的，先设置好许可（unpark）再获取许可的时候不会进行等待，正如我们说的那样输出如下：

```sql
提前上好闹钟7点起床
睡觉
7点到，起床
```

# 4、思考一个问题

看完源码后，是不是觉得LockSupport.park()和unpark()和object.wait()和notify()很相似，那么它们有什么区别呢？

1. 面向的主体不一样。LockSuport主要是针对Thread进进行阻塞处理，可以指定阻塞队列的目标对象，每次可以指定具体的线程唤醒。Object.wait()是以对象为纬度，阻塞当前的线程和唤醒单个(随机)或者所有线程。
2. 实现机制不同。虽然LockSuport可以指定monitor的object对象，但和object.wait()，两者的阻塞队列并不交叉。可以看下测试例子。object.notifyAll()不能唤醒LockSupport的阻塞Thread.