### 【细谈Java并发】谈谈锁

转载自[【细谈Java并发】谈谈锁](https://benjaminwhx.com/2018/05/03/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88%E9%94%81/)

我们知道在并发环境下为了保证共享变量的线程安全，除了可以使用某些原子类的操作，还可以通过为被保护的变量加锁的方式实现该变量的线程安全。

而在java中我们有两种方式来使用一个锁，请注意，这里所说的锁都是对象锁。一种是JVM帮我们实现的，通过synchronized关键字来进行加锁，另外一种是J.U.C包中的Lock接口，该接口中的两个常用的用来加锁和解锁的方法为：lock()、unlock()，除此以外还有其他的用来处理中断的锁等等。那么，对于我们使用者来说，该怎么选择使用锁就是一个需要解决的问题，而要解决这个问题，首先我们需要对这两种锁的实现原理进行深入的了解。本文笔者将会深入了解java中的这两种锁的实现原理，以期望得出一种正确使用锁的方法。

# 1、synchronized

synchronized是java中的一个关键字，通过该关键字，我们就可以很方便的对类方法、实例方法、代码块进行加锁，并且锁的释放由JVM来保证，不需要使用者执行额外的操作。

Java中的每个对象都可以作为锁。

1. 普通同步方法，锁是当前实例对象。
2. 静态同步方法，锁是当前类的class对象。
3. 同步代码块，锁是括号中的对象。

我们来看一段代码：

```java
public class SynchronizedTest {

    private static Object object = new Object();

    public static void main(String[] args) {
        synchronized (object) {
        }
    }

    public synchronized static void m() {}
}
```

上述代码中，使用了同步代码块和同步方法，我们通过`javap -verbose com/github/lock/SynchronizedTest`来查看class文件的信息，以此来分析synchronized关键字的实现细节。

```java
  public static void main(java.lang.String[]);
    descriptor: ([Ljava/lang/String;)V
    flags: ACC_PUBLIC, ACC_STATIC
    Code:
      stack=2, locals=3, args_size=1
         0: getstatic     #2                  // Field object:Ljava/lang/Object;
         3: dup
         4: astore_1
         5: monitorenter
         6: aload_1
         7: monitorexit
         8: goto          16
        11: astore_2
        12: aload_1
        13: monitorexit
        14: aload_2
        15: athrow
        16: return

  public static synchronized void m();
    descriptor: ()V
    flags: ACC_PUBLIC, ACC_STATIC, ACC_SYNCHRONIZED
    Code:
      stack=0, locals=0, args_size=0
         0: return
      LineNumberTable:
        line 17: 0
```

从生成的class信息中，可以清楚的看到

1. 同步代码块使用了 **monitorenter** 和 **monitorexit** 指令实现。
2. 同步方法中依靠方法修饰符上的 **ACC_SYNCHRONIZED** 实现。

synchronized加锁解锁的过程可以描述为下面几个步骤：

1. 线程执行monitorenter指令时尝试获取monitor的所有权。
2. 如果monitor的进入数为0，则该线程进入monitor，然后将进入数设置为1，该线程即为monitor的所有者。
3. 如果线程已经占有该monitor，只是重新进入，则进入monitor的进入数加1。
4. 如果其他线程已经占用了monitor，则该线程进入阻塞状态，直到monitor的进入数为0，再重新尝试获取monitor的所有权。

但是synchronized并不是对所有的请求都直接通过使用monitor来进行锁的分配，因为monitor是比较重的锁，获取不到锁的线程将进入阻塞状态，这就涉及到线程状态的切换，比较耗资源。**synchronized除了实现了重量级锁之外，还实现了偏向锁，轻量级锁，其中偏向锁在1.6是默认开启的。**

在进一步深入之前，我们先认识下两个概念：对象头和monitor。

## 1.1、对象头

对象头包括两部分：Mark Word （存储对象的hashCode或锁信息等）和 类型指针（存储到对象类型数据的指针）。如果对象时数组类型，对象头还包括数组的长度，这三个部分每个都占32/64bit的长度。

这里我们主要说的是Mark Word。

Mark Word用于存储对象自身的运行时数据，如哈希码（HashCode）、GC分代年龄、锁状态标志、线程持有的锁、偏向线程ID、偏向时间戳等等，占用内存大小与虚拟机位长一致。

HotSpot通过markOop类型实现Mark Word，32位虚拟机的markOop实现如下：

- hash：保存对象的哈希码
- age：保存对象的分代年龄
- biased_lock：偏向锁标识位
- lock：锁状态标识位
- JavaThread：保存持有偏向锁的线程ID
- epoch：保存偏向时间戳

其中锁状态对应的是biased_lock和lock，不同的锁状态，存储着不同的数据：

![img](https://benjaminwhx.com/images/lock1.png)

## 1.2、monitor

monitor是线程私有的数据结构，每一个线程都有一个可用monitor列表，同时还有一个全局的可用列表。monitor内部组成如下：

- Owner：初始时为NULL表示当前没有任何线程拥有该monitor，当线程成功拥有该锁后保存线程唯一标识，当锁被释放时又设置为NULL。
- EntryQ：关联一个系统互斥锁（semaphore），阻塞所有试图锁住monitor失败的线程。
- RcThis：表示blocked或waiting在该monitor上的所有线程的个数。
- Nest：用来实现重入锁的计数。
- HashCode：保存从对象头拷贝过来的HashCode值（可能还包含GC age）。
- Candidate：用来避免不必要的阻塞或等待线程唤醒，因为每一次只有一个线程能够成功拥有锁，如果每次前一个释放锁的线程唤醒所有正在阻塞或等待的线程，会引起不必要的上下文切换（从阻塞到就绪然后因为竞争锁失败又被阻塞）从而导致性能严重下降。Candidate只有两种可能的值：0表示没有需要唤醒的线程，1表示要唤醒一个继任线程来竞争锁。

那么monitor的作用是什么呢？在 java 虚拟机中，线程一旦进入到被synchronized修饰的方法或代码块时，指定的锁对象通过某些操作将对象头中的Mark Word指向monitor 的起始地址与之关联，同时monitor 中的Owner存放拥有该锁的线程的唯一标识，确保一次只能有一个线程执行该部分的代码，线程在获取锁之前不允许执行该部分的代码。

接下去，我们可以深入了解下在锁各个状态下，底层是如何处理多线程之间对锁的竞争。

## 1.3、锁的升级与对比

Java SE1.6为了减少获得锁和释放锁带来的性能消耗，引入了“偏向锁”和“轻量级锁”，在Java SE1.6中，锁一共有4种状态，**级别从低到高依次是：无锁状态、偏向锁状态、轻量级锁状态和重量级锁状态**，这几个状态会随着竞争情况逐渐升级。锁可以升级但不能降级，意味着偏向锁升级成轻量级锁后不能降级成偏向锁。这种锁升级不能降级的策略，目的是为了提高获得锁和释放锁的效率。

下面引用一张网上的图说明白了synchronized的原理。

![img](https://benjaminwhx.com/images/synchronized.jpeg)

### 偏向锁

HotSpot的作者经过研究发现，大多数情况下，锁不仅不存在多线程竞争，而且总是由同一线程多次获得，为了让线程获得锁的代价更低而引入了偏向锁。当一个线程访问同步块并获取锁时，会在对象头和栈帧中的锁记录里存储锁偏向的线程ID，以后该线程在进入和退出同步块时不需要进行CAS操作来加锁和解锁，只需简单地测试一下对象头的Mark Word里是否存储着指向当前线程的偏向锁。如果测试成功，表示线程已经获得了锁。如果测试失败，则需要再测试一下Mark Word中偏向锁的标识是否设置成1（表示当前是偏向锁）：如果没有设置，则使用CAS竞争锁，如果设置了，则尝试使用CAS将对象头的偏向锁指向当前线程。

> 偏向锁在Java6和Java7里是默认启用的，但是它在应用程序启动几秒钟之后才激活，如果有必要可以使用JVM参数来关闭延迟：-XX:BiasedLockingStartupDelay=0。如果你确定应用程序里所有的锁通常情况下处于竞争状态，可以通过JVM参数关闭偏向锁：-XX:-UseBiasedLocking=false，那么程序默认会进入轻量级锁状态。

我们前面说到了synchronized首先会生成monitorenter指令，我们来看看Hotspot的入口：

![img](https://benjaminwhx.com/images/lock2.png)

**UseBiasedLocking**标识虚拟机是否开启偏向锁功能，如果开启则执行fast_enter逻辑，否则执行slow_enter；

可以看到偏向锁的处理器逻辑在`ObjectSynchronizer::fast_enter`函数里。

![img](https://benjaminwhx.com/images/lock3.png)

#### 偏向锁的获取

我们看看revoke_and_rebias方法（因为太长，只放出部分重要代码）：

```java
// 获取对象的markOop数据mark，即对象头的Mark Word
markOop mark = obj->mark();
if (mark->has_bias_pattern()) {
    Klass* k = obj->klass();
    // obj类的对象头
    markOop prototype_header = k->prototype_header();

    if (!prototype_header->has_bias_pattern()) { // 对象头里没有偏向锁
        markOop biased_value       = mark;
        // CAS替换失败，说明有多线程竞争，直接返回BIAS_REVOKED
        markOop res_mark = (markOop) Atomic::cmpxchg_ptr(prototype_header, obj->mark_addr(), mark);
        assert(!(*(obj->mark_addr()))->has_bias_pattern(), "even if we raced, should still be revoked");
        return BIAS_REVOKED;
    } else if (prototype_header->bias_epoch() != mark->bias_epoch()) { // 偏向时间戳改变
        // 再偏向
        if (attempt_rebias) {
            assert(THREAD->is_Java_thread(), "");
            markOop biased_value       = mark;
            markOop rebiased_prototype = markOopDesc::encode((JavaThread*) THREAD, mark->age(), prototype_header->bias_epoch());
            // CAS替换成功，返回BIAS_REVOKED_AND_REBIASED，继续偏向
            markOop res_mark = (markOop) Atomic::cmpxchg_ptr(rebiased_prototype, obj->mark_addr(), mark);
            if (res_mark == biased_value) {
                return BIAS_REVOKED_AND_REBIASED;
            }
        }
    }
}
```

实现逻辑如下：

1. 通过`markOop mark = obj->mark()`获取对象的markOop数据mark，即对象头的Mark Word。
2. 判断mark是否为可偏向状态，即mark的偏向锁标志位为 **1**，锁标志位为 **01**。
3. 判断mark中JavaThread的状态：如果为空，则进入步骤（4）；如果指向当前线程，则执行同步代码块；如果指向其它线程，进入步骤（5）。
4. 通过CAS原子指令设置mark中JavaThread为当前线程ID，如果执行CAS成功，则执行同步代码块，否则进入步骤（5）。
5. 如果执行CAS失败，表示当前存在多个线程竞争锁，当达到全局安全点（safepoint），获得偏向锁的线程被挂起，撤销偏向锁，并升级为轻量级，升级完成后被阻塞在安全点的线程继续执行同步代码块。

#### 偏向锁的撤销

只有当其它线程尝试竞争偏向锁时，持有偏向锁的线程才会释放锁，撤销的逻辑如下：

1. 偏向锁的撤销动作必须等待全局安全点。
2. 暂停拥有偏向锁的线程，判断锁对象是否处于被锁定状态。
3. 撤销偏向锁，恢复到无锁（标志位为 **01**）或轻量级锁（标志位为 **00**）的状态。

### 轻量级锁

JVM通过CAS修改对象头来获取锁，如果CAS修改成功则获取锁，如果获取失败，则说明有竞争，则通过CAS自旋一段时间来修改对象头，如果还是获取失败，则升级为重量级锁。如果没有竞争，轻量级锁使用CAS操作避免了使用互斥量的开销，但如果存在锁竞争，除了互斥量的开销外，还额外发生了CAS操作，因此在有竞争的情况下，轻量级锁会比传统的重量级锁更慢。

### 重量级锁

重量级锁通过对象内部的监视器（monitor）实现，其中monitor的本质是依赖于底层操作系统的Mutex Lock实现，操作系统实现线程之间的切换需要从用户态到内核态的切换，切换成本非常高。

### 优缺点对比

| 锁       | 优点                                                         | 缺点                                           | 适用场景                           |
| :------- | :----------------------------------------------------------- | :--------------------------------------------- | :--------------------------------- |
| 偏向锁   | 加锁和解锁不需要额外的消耗，和执行非同步方法相比仅存在纳秒级的差距 | 如果线程间存在锁竞争，会带来额外的锁撤销的消耗 | 适用于只有一个线程访问同步块的场景 |
| 轻量级锁 | 竞争的线程不会阻塞，提高了程序的响应速度                     | 如果始终得不到锁竞争的线程，使用自旋会消耗CPU  | 追求响应时间同步块执行速度非常快   |
| 重量级锁 | 线程竞争不使用自旋，不会消耗CPU                              | 线程阻塞，响应时间缓慢                         | 追求吞吐量，同步块执行速度较长     |

## 1.4、其他概念

#### 锁消除

锁消除是指虚拟机在即时编译器在运行时，对于一些在代码上要求同步，但是被检测到不可能存在数据竞争的锁进行消除。比如，一个锁不可能被多个线程访问到，那么在这个锁上的同步块JVM将把锁消除掉。

#### 锁粗化

程序中一系列的连续操作都对同一个对象反复加锁和解锁，甚至加锁操作是出现在循环体中的，那即使没有线程竞争，频繁地进行互斥同步操作也会导致不必要的性能损耗。

```java
for(int i=0; i<1000; i++){
    synchronized(this){
        ...
    }
}
```

上面代码JVM将会优化成如下：

```java
synchronized(this){
    for(int i=0; i<1000; i++){
        ...
    }
}
```

# 2、Lock

Lock是java并发包J.U.C中的一个用来实现锁的接口，该接口的主要实现类有ReadLock，WriteLock，ReentrantLock，实际使用过程中比较常用的就是ReentrantLock。而ReentrantLock主要是基于AQS来完成同步操作的。

Lock有以下几个特点：

1. 可重入：同一个线程可以多次获取锁，不会阻塞。
2. 可中断：可以使用lock.lockInterruptibly()来响应中断，当有中断出现时，即放弃锁的请求。
3. 公平、非公平：公平锁讲究先来先到，线程在获取锁时，如果这个锁的等待队列中已经有线程在等待，那么当前线程就会进入等待队列中 。非公平锁不管是否有等待队列，如果可以获取锁，则立刻占有锁对象。

其他的详细原理的分析请看我的几篇文章：[【细谈Java并发】谈谈AQS](http://benjaminwhx.com/2018/04/30/【细谈Java并发】谈谈AQS/)、[【细谈Java并发】谈谈ReentrantLock](http://benjaminwhx.com/2018/05/02/【细谈Java并发】谈谈ReentrantLock/)

# 3、锁的内存语义

## 内存可见性

synchronized关键字强制实施一个互斥锁，使得被保护的代码块在同一时间只能有一个线程进入并执行。当然synchronized还有另外一个 方面的作用：在线程进入synchronized块之前，会把工作存内存中的所有内容映射到主内存上，然后把工作内存清空再从主存储器上拷贝最新的值。而 在线程退出synchronized块时，同样会把工作内存中的值映射到主内存，但此时并不会清空工作内存。这样一来就可以强制其按照上面的顺序运行，以 保证线程在执行完代码块后，工作内存中的值和主内存中的值是一致的，保证了数据的一致性！
所以由synchronized修饰的set与get方法都是相当于直接对主内存进行操作，不会出现数据一致性方面的问题。

## 指令重排序

指令重排序是JVM为了优化指令，提高程序运行效率，在不影响单线程程序执行结果的前提下，尽可能地提高并行度。
synchronized块对应java程序来说是原子操作，所以说内部不管怎么重排序都不会影响其它线程执行导致数据错误，执行的指令也不会溢出方法。

这里放出一张之前分析volatile的图：

![img](http://benjaminwhx.com/images/volatile4.jpg)

可以看出MonitorEnter（锁获取）和volatile读的语义一致，而MonitorExit（锁释放）和volatile写的语义一致。

下面对锁释放和锁获取的内存语义做个总结：

- 线程A释放一个锁，实质上是线程A向接下来将要获取这个锁的某个线程发出了（线程A对共享变量所做修改的）消息。
- 线程B获取一个锁，实质上是线程B接收了之前某个线程发出的（在释放这个锁之前对共享变量所做修改的）消息。
- 线程A释放锁，随后线程B获取这个锁，这个过程实质上是线程A通过主内存向线程B发送消息。

# 4、synchronized和lock的区别

synchronized和lock的区别如下：

| compare | synchronized             | lock                                      |
| :------ | :----------------------- | :---------------------------------------- |
| 哪层面  | 虚拟机层面               | 代码层面                                  |
| 锁类型  | 可重入、不可中断、非公平 | 可重入、可中断、可公平                    |
| 锁获取  | A线程获取锁，B线程等待   | 可以尝试获取锁，不需要一直等待            |
| 锁释放  | 由JVM 释放锁             | 在finally中手动释放。如不释放，会造成死锁 |
| 锁状态  | 无法判断                 | 可以判断                                  |



lock比synchronized有如下优点：

1. 支持公平锁，某些场景下需要获得锁的时间与申请锁的时间相一致，但是synchronized做不到 。
2. 支持中断处理，就是说那些持有锁的线程一直不释放，正在等待的线程可以放弃等待。如果不支持中断处理，那么线程可能一直无限制的等待下去，就算那些正在占用资源的线程死锁了，正在等待的那些资源还是会继续等待，但是ReentrantLock可以选择放弃等待 。
3. condition和lock配合使用，以获得最大的性能。

# 5、建议

1. 如果没有特殊的需求，建议使用synchronized，因为操作简单，便捷，不需要额外进行锁的释放。鉴于JDK1.8中的ConcurrentHashMap也使用了CAS+synchronized的方式替换了老版本中使用分段锁（ReentrantLock）的方式，可以得知，JVM中对synchronized的性能做了比较好的优化。
2. 如果代码中有特殊的需求，建议使用Lock。例如并发量比较高，且有些操作比较耗时，则可以使用支持中断的所获取方式；如果对于锁的获取，讲究先来后到的顺序则可以使用公平锁；另外对于多个变量的锁保护可以通过lock中提供的condition对象来和lock配合使用，获取最大的性能。