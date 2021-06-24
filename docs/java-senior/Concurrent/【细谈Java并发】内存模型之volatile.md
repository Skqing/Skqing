### 【细谈Java并发】内存模型之volatile

转载自[【细谈Java并发】内存模型之volatile](https://benjaminwhx.com/2018/05/13/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E5%86%85%E5%AD%98%E6%A8%A1%E5%9E%8B%E4%B9%8Bvolatile/)

在实际的工作中，可能我们比较少去使用的一个关键字就是volatile，但是观察源码的时候却是经常遇到。如果不搞懂何时用它以及为什么用它，会给我们带来很多困惑。

本文将从volatile关键字的原理、特性以及CPU层面的实现来分析它。

> Java语言规范第3版中对volatile的定义如下：Java编程语言允许线程访问共享变量，为了确保共享变量能够准确和一致地更新，线程应该确保通过排他锁单独获得这个变量。如果一个字段被声明成volatile，Java线程内存模型确保所有线程看到这个变量的值是一致的。

# volatile的特性

volatile变量具有下列特性：

1. 可见性。对一个volatile变量的读，总是能看到（任意线程）对这个volatile变量最后的写入。
2. 阻止编译时和运行时的指令重排。
3. 原子性。这里所说的原子性是对任意单个volatile变量的读/写，但是类似于volatile++这种复合操作不具有原子性。

## 可见性

什么是可见性？

可见性的意思是当一个线程修改一个共享变量时，另外一个线程能读到这个修改的值。

volatile是轻量级的synchronized，它在多处理器开发中保证了共享变量的“可见性”，我们可以简单的理解为把对volatile变量的单个读/写，看成是使用同一个锁对这些单个读/写操作做了同步。如果volatile变量修饰符使用恰当的话，它比synchronized的使用和执行成本更低，因为它不会引起线程上下文的切换和调度。下面的例子中两个类的执行效果是相同的。

```java
public class VolatileFeatureExample {
    volatile long v1 = 0L;

    public void set(long l) {
        v1 = l;
    }

    public void getAndIncrement() {
        v1++;
    }

    public long get() {
        return v1;
    }
}

public class VolatileFeatureExample {
    long v1 = 0L;

    public synchronized void set(long l) {
        v1 = l;
    }

    public void getAndIncrement() {
        long temp = get();
        temp += 1L;
        set(temp);
    }

    public synchronized long get() {
        return v1;
    }
}
```

那么volatile是如何实现可见性的呢？

我们在X86处理器下通过工具获取JIT编译器生成的汇编指令来查看对volatile进行写操作时，CPU会做什么事情。

Java代码如下：

```java
instance = new Singleton();    // instance是volatile变量
```

转变成汇编代码，如下：

```java
0x01a3de1d: movb $0x0,0x1104800(%esi);0x01a3de24: lock addl $0x0,(%esp);
```

通过查询架构软件开发手册可知，Lock前缀的指令在多核处理器下会引发两件事情。

1. Lock前缀指令会引起处理器缓存回写到内存（相当于volatile写的内存语义）。在多处理器环境中，对于Intel486和Pentium处理器，LOCK#信号确保在声言该信号期间，处理器可以独占任何共享内存（锁住总线，让其他CPU不能访问总线，也就是不能访问系统内存）。但是对于Pentium 4, Intel Xeon, P6系列处理器，如果加锁的内存区域已经缓存在处理器中，处理器可能并不对总线发出LOCK#信号，而是仅仅修改缓存中的数据，然后依赖缓存一致性机制来保证加锁操作的自动执行。这个操作称为“缓存加锁”（MESI就是这样的一个缓存一致性协议）。
2. 一个处理器的缓存回写到内存会导致其他处理器的缓存无效，线程接下来将从主内存中读取共享变量，也就是MESI协议所做的事（相当于volatile读的内存语义）。

> IA-32处理器和Intel 64处理器使用MESI（修改、独占、共享、无效）控制协议去维护内存缓存和其他处理器缓存的一致性。处理器能够使用嗅探技术保证它的内部缓存、系统内存和其他处理器的缓存的数据在总线上保持一致。
>
> 在MESI协议中，每个Cache line有4种状态，分别是：
>
> - M（Modified）：这行数据有效，但是被修改了，和内存中的数据不一致，数据只存在本Cache中。
> - E（Exclusive）：这行数据有效，和内存中的数据一致，数据只存在于本Cache中。
> - S（Shared）：这行数据有效，和内存中的数据一致，数据分布在很多Cache中。
> - I（Invalid）：这行数据无效。

CPU在cache line状态的转化期间是阻塞的，经过长时间的优化，在寄存器和L1缓存之间添加了LoadBuffer、StoreBuffer来降低阻塞时间，LoadBuffer、StoreBuffer，合称排序缓冲(Memoryordering Buffers (MOB))，Load缓冲64长度，store缓冲36长度，Buffer与L1进行数据传输时，CPU无须等待。

1. CPU执行load读数据时，把读请求放到LoadBuffer，这样就不用等待其它CPU响应，先进行下面操作，稍后再处理这个读请求的结果。
2. CPU执行store写数据时，把数据写到StoreBuffer中，待到某个适合的时间点，把StoreBuffer的数据刷到主存中。

因为StoreBuffer的存在，CPU在写数据时，真实数据并不会立即表现到内存中，所以对于其它CPU是不可见的；同样的道理，LoadBuffer中的请求也无法拿到其它CPU设置的最新数据；

由于StoreBuffer和LoadBuffer是异步执行的，所以在外面看来，先写后读，还是先读后写，没有严格的固定顺序。

可是这样不能保证CPU在load的时候可以拿到最新数据，我们来结合一个例子来看看volatile是如何保证可见性的。

```java
public class VolatileExample {
    int a = 0;
    volatile boolean flag = false;

    public void writer() {
        a = 1;          // 1
        flag = true;    // 2
    }

    public void reader() {
        if (flag) {     // 3
            int i = a;  // 4
            // ...
        }
    }
}
```

![img](https://benjaminwhx.com/images/volatile1.png)
当线程A执行writer()方法后，线程B执行reader()方法。线程A写的这个volatile变量flag将会立即对线程B可见，看起来好像线程A向线程B发送了消息一样。

那么它是怎么做到的呢？

原来线程A在2的后面增加了一个StoreLoad的内存屏障，那么什么是内存屏障呢？下面我们来重点讲讲。

## 指令重排和内存屏障

之前我提到过，在执行程序时，为了提高性能，编译器和处理器常常会对指令做重排序。而重排序会带来可见性的问题。

为了实现volatile的内存语义，JMM会分别限制这两种类型的重排序。

### 阻止编译器重排序

JSR-133之前的旧Java内存模型中，虽然不允许volatile变量之间重排序，但是允许volatile变量与普通变量重排序。

拿上面VolatileExample的例子来说，旧的Java内存模型中有可能执行的顺序为2341。导致读线程B执行4时，不一定能看到写线程A在执行1时对共享变量的修改。

因此，volatile的写-读没有锁的释放-获取所具有的内存语义。为了提供一种比锁更轻量级的线程之间的通信机制，JSR-133专家组决定增加volatile的语义：**只要volatile变量与普通变量之间的重排序可能会破坏volatile的内存语义，这种重排序就会被编译器排序规则和处理器内存屏障插入策略禁止**。

下面是JSR-133中volatile对于编译器重排序的规则表。

![img](https://benjaminwhx.com/images/volatile3.png)

举例来说，第三行最后一个单元格的意思是，当第一个操作是普通变量的读/写时，如果第二个操作为volatile写，则编译器不能重排序这两个操作。

### 阻止处理器重排序

JMM的处理器重排序规则会要求Java编译器在生成指令序列时，插入特定类型的内存屏障（Memory Barriers，Intel称之为Memory Fence）指令，通过内存屏障指令来禁止特定类型的处理器重排序。

那么什么是内存屏障呢？

内存屏障（[memory barrier](http://en.wikipedia.org/wiki/Memory_barrier)）是组个CPU指令。编译器和CPU可以在保证输出结果一样的情况下对指令重排序，使性能得到优化。插入一个内存屏障，相当于告诉CPU和编译器先于这个命令的必须先执行，后于这个命令的必须后执行。

内存屏障另一个作用是强制更新一次不同CPU的缓存。例如，一个写屏障会把这个屏障前写入的数据刷新到主存，这样任何试图读取该数据的线程将得到最新值，而不用考虑到底是被哪个cpu核心或者哪个CPU执行的。

几乎所有的处理器至少支持一种粗粒度的屏障指令，通常被称为“栅栏（Fence）”，它保证在栅栏前初始化的load和store指令，能够严格有序的在栅栏后的load和store指令之前执行。无论在何种处理器上，这几乎都是最耗时的操作之一（与原子指令差不多，甚至更消耗资源），所以大部分处理器支持更细粒度的屏障指令。

内存屏障的一个特性是将它们运用于内存之间的访问。尽管在一些处理器上有一些名为屏障的指令，但是正确的/最好的屏障使用取决于内存访问的类型。下面是一些屏障指令的通常分类，正好它们可以对应上常用处理器上的特定指令（有时这些指令不会导致操作）。

内存屏障分为以下4个：

1. LoadLoad屏障（Load1，LoadLoad， Load2）：确保Load1所要读入的数据能够在被Load2和后续的load指令访问前读入。通常能执行预加载指令或/和支持乱序处理的处理器中需要显式声明Loadload屏障，因为在这些处理器中正在等待的加载指令能够绕过正在等待存储的指令。 而对于总是能保证处理顺序的处理器上，设置该屏障相当于无操作。
2. LoadStore屏障（Load1，LoadStore， Store2）：确保Load1的数据在Store2和后续Store指令被刷新之前读取。在等待Store指令可以越过loads指令的乱序处理器上需要使用LoadStore屏障。
3. StoreStore屏障（Store1，StoreStore，Store2）：确保Store1的数据在Store2以及后续Store指令操作相关数据之前对其它处理器可见（例如向主存刷新数据）。通常情况下，如果处理器不能保证从写缓冲或/和缓存向其它处理器和主存中按顺序刷新数据，那么它需要使用StoreStore屏障。
4. StoreLoad屏障（Store1，StoreLoad，Load2）：确保Store1的数据在被Load2和后续的Load指令读取之前对其他处理器可见。StoreLoad屏障可以防止一个后续的load指令 不正确的使用了Store1的数据，而不是另一个处理器在相同内存位置写入一个新数据。

StoreLoad屏障是一个“全能型”的屏障，它同时具有其他3个屏障的效果。现代的多处理器大多支持该屏障。执行该屏障的开销会有昂贵，因为当前处理器通常要把写缓冲区中的数据全部刷新到内存中。

可能光从概念上来看我们还是不懂什么是内存屏障以及在处理器层面是如何调用内存屏障的。它是一个指令还是一组指令呢？接下来我们从jvm源码以及linux内核的层面说说它。

下面是jvm1.7中调用屏障的各个方法，可以看出StoreLoad调用了fence方法，正是上面所说的”栅栏”。

```java
// Implementation of class OrderAccess.
inline void OrderAccess:loadload() { acquire(); }

inline void OrderAccess:storestore() { release(); }

inline void OrderAccess:loadstore() { acquire(); }

inline void OrderAccess:storeload() { fence(); }

inline void OrderAccess::acquire() {
    volatile intptr_t local_dummy;
    #ifdef AMD64
        __asm__ volatile ("movq 0(%%rsp), %0" : "=r" (local_dummy) : : "memory");
    #else
        __asm__ volatile ("movq 0(%%esp), %0" : "=r" (local_dummy) : : "memory");
    #endif //AMD64
}

inline void OrderAccess::release() {
    // 避免多线程触及同一高速缓存行
    volatile jint local_dummy = 0;
}

inline void OrderAccess::fence() {
    // 判断是否是多核，单核不进来
    if (os::is_MP()) {
        // always use locked addl since mfence is sometimes expensive
        #ifdef AMD64
            __asm__ volatile ("lock; addl $0,0(%%rsp)" : : : "cc", "memory");
        #else
            __asm__ volatile ("lock; addl $0,0(%%esp)" : : : "cc", "memory");
        #endif
    }
}
```

> 关键词解惑：
>
> `intptr_t`：intptr_t在不同的平台是不一样的，始终与地址位数相同，因此用来存放地址，即地址。
>
> `AMD64`：AMD公司开发的一个64位元的电脑处理器架构。
>
> `movq`：完成8个字节的复制操作。
>
> `__asm__`：用于指示编译器在此插入汇编语句。
>
> `volatile`：用于告诉编译器，严禁将此处的汇编语句与其它的语句重组合优化。即：原原本本按原来的样子处理这这里的汇编。
>
> `addl`：汇编指令的加操作。
>
> `lock`：设置LOCK#信号，上面有解释。
>
> `memory`：提示编译器该指令对内存修改，防止使用某个寄存器中已经load的内存的值,应该是告诉CPU内存已经被修改过，让CPU invalidate所有的cache。
>
> `rsp、esp`：寄存器。

我们直接看最重要的StoreLoad屏障，也就是对应的fence()方法。

```java
__asm__ volatile ("lock; addl $0,0(%%esp)" : : : "cc", "memory")
```

这行代码的意思是使用lock前缀表示将后面这句汇编语句：`addl $0,0(%%esp)`作为cpu的一个内存屏障。`addl $0,0(%%esp)`表示将数值0加到esp寄存器中，而该寄存器指向栈顶的内存单元。加上一个0，esp寄存器的数值依然不变。即这是一条无用的汇编指令（采用这个空操作而不是空操作指令nop是因为IA32手册规定lock前缀不允许配合nop指令使用）。在此**利用这条无价值的汇编指令来配合lock指令，用作cpu的内存屏障**。

有心的朋友可能会发现这样一局注释：`always use locked addl since mfence is sometimes expensive`，这句话的意思是永远使用locked addl因为mfence有时太昂贵了，这里的mfence是什么鬼？

mfence：串行化发生在MFENCE指令之前的读写操作。它是X86cpu家族中的新指令，它能够保证系统在后面的memory访问之前，先前的memory访问都已经结束。下面是新的代码

```java
alternative("lock; addl $0,0(%%esp)", "mfence", X86_FEATURE_XMM2)
```

比起以前的源代码来少了`__asm__`和`__volatile__`。增加了`alternative()`宏和`mfence`指令。我的理解就是用一个新指令代替了原来的老指令串。

但是具体为什么注释写它开销比locked addl昂贵，这个暂时还不清楚。

内存屏障就说到这，下面是为了实现volatile的内存语义，JMM内存屏障的插入策略。

1. 在每个volatile写操作的前面插入一个StoreStore屏障。
2. 在每个volatile写操作的后面插入一个StoreLoad屏障。
3. 在每个volatile读操作的后面插入一个LoadLoad屏障。
4. 在每个volatile读操作的后面插入一个LoadStore屏障。

这里说的内存屏障插入策略非常保守，但它可以保证在任意处理器平台，任意的程序中都能得到正确的volatile内存语义。编译器可以根据具体情况省略不必要的屏障。

屏障插入的规则表为（包括了锁的MonitorEnter和MonitorExit）：

![img](https://benjaminwhx.com/images/volatile4.jpg)

下面我们通过具体的代码进行说明

```java
public class VolatileBarrierExample {
    int a;
    volatile int v1 = 1;
    volatile int v2 = 2;

    void readAndWrite() {
        int i = v1; // 第一个volatile读
        int j = v2; // 第二个volatile读
        a = i + j;  // 普通写
        v1 = i + 1; // 第一个volatile写
        v2 = j * 2; // 第二个volatile写
    }
}
```

编译器在生成字节码时可以做如下的优化：

![img](https://benjaminwhx.com/images/volatile2.png)

由于不同的处理器有不同“松紧度”的处理器内存模型，内存屏障的插入还可以根据具体的处理器内存模型继续优化。以X86处理器为例，除了上图最后的StoreLoad屏障外，其他的屏障都会被省略。这意味着在X86处理器中，volatile写的开销比volatile读的开销会大很多（因为执行StoreLoad屏障开销会比较大）。

## 原子性

注意：这里说的原子性仅仅是对任意单个volatile变量的读/写，i++这种的用volatile修饰是不保证其原子性的。

这里引发了两个问题：

1. 为什么单个变量不用volatile修饰就会有问题？
2. 为什么i++这种的用volatile修饰不能保证其原子性呢？

我们一一来讨论。

**问题一：为什么单个变量不用volatile修饰就会有问题？**

这里说的单个变量不用volatile修饰的有问题的特指long和double类型修饰的变量（64bit）。在一些32位的处理器上，如果要求对64位数据的写操作具有原子性，会有比较大的开销，为了照顾这种处理器，Java语言规范鼓励但不强求JVM对64位的long型变量和double型变量的写操作具有原子性。当JVM在这种处理器上运行时，可能会把一个64位long/double型变量的写操作拆分为两个32位的写操作来执行。这两个32位的写操作可能会被划分到不同的总线事务中执行，此时对这个64位变量的写操作将不具有原子性。

> 总线事务：每次处理器和内存之间的数据传递都是通过一系列步骤来完成的，这一系列步骤称之为总线事务（Bus Transaction）。总线事务包括读事务和写事务。每个事务会读/写内存中一个或多个物理上连续的字。在一个处理器执行总线事务期间，总线会禁止其他的处理器和I/O设备执行内存的读/写。

注意：在JSR-133之前的旧内存模型中，一个64位long/double型变量的读/写操作可以被拆分为两个32的读/写操作来执行。而在JSR-133内存模型开始，仅仅允许把一个64位long/double型变量的写操作拆分为两个32位的写操作来执行，任意的读操作都必须具有原子性（即任意读操作必须要在单个读事务中执行）

**问题二：为什么i++这种的用volatile修饰不能保证其原子性呢？**

其实i++这种操作主要可以分为3步：

1. 读取volatile变量值到local
2. 增加变量的值
3. 把local的值写回，让其它的线程可见

这三步对应的JVM指令为：

```java
mov    0xc(%r10),%r8d ; Load
inc    %r8d           ; Increment
mov    %r8d,0xc(%r10) ; Store
lock addl $0x0,(%rsp) ; StoreLoad Barrier
```

从Load到store到内存屏障，一共4步，其中最后一步jvm让这个最新的变量的值在所有线程可见，也就是最后一步让所有的CPU内核都获得了最新的值，但**中间的几步（从Load到Store）**是不安全的，中间如果其他的CPU修改了值将会丢失。

# 参考

《Java并发编程的艺术》

[Java内存模型Cookbook（二）内存屏障](http://ifeve.com/jmm-cookbook-mb/)

[为什么volatile不能保证原子性而Atomic可以？](http://www.cnblogs.com/Mainz/p/3556430.html)