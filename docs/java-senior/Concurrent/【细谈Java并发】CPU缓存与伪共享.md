### 【细谈Java并发】CPU缓存与伪共享

转载自[【细谈Java并发】CPU缓存与伪共享](https://benjaminwhx.com/2018/05/18/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88CPU%E7%BC%93%E5%AD%98%E4%B8%8E%E4%BC%AA%E5%85%B1%E4%BA%AB/)

众所周知，CPU是计算机的大脑，它负责执行程序的指令； 内存负责存数据，包括程序自身数据。同样大家都知道，内存比CPU慢很多。 其实在30年前，CPU的频率和内存总线的频率在同一个级别，访问内存只比访问CPU寄存器慢一点儿。由于内存的发展都到技术及成本的限制，现在获取内存中的一条数据大概需要200多个CPU周期(CPU cycles)，而CPU寄存器一般情况下1个CPU周期就够了。如果你对这些概念还不是很了解，我将会在下面带你一一了解。

# 1、CPU缓存

网页浏览器为了加快速度，会在本机存缓存以前浏览过的数据；传统数据库或NoSQL数据库为了加速查询，常在内存设置一个缓存，减少对磁盘(慢)的IO。Ï同样内存与CPU的速度相差太远，于是CPU设计者们就给CPU加上了缓存(CPU Cache)。如果你需要对同一批数据操作很多次，那么把数据放至离CPU更近的缓存，会给程序带来很大的速度提升。例如，做一个循环计数，把计数变量放到缓存里，就不用每次循环都往内存存取数据了。下面是CPU Cache的简单示意图。

![img](https://benjaminwhx.com/images/cpu-cache.png)

随着多核的发展，CPU Cache分成了三个级别：L1、L2、L3。级别越小越接近CPU，所以速度也更快，同时也代表着容量越小。L1是最接近CPU的，它容量最小，例如32K，速度最快，每个核上都有一个L1 Cache(准确地说每个核上有两个L1 Cache，一个存数据 L1d Cache，一个存指令 L1i Cache)。L2 Cache 更大一些，例如256K，速度要慢一些，一般情况下每个核上都有一个独立的L2 Cache; L3 Cache是三级缓存中最大的一级，例如12MB，同时也是最慢的一级，在同一个CPU插槽之间的核共享一个L3 Cache。

| 从CPU到                                   | 大约需要的CPU周期 | 大约需要的时间 |
| :---------------------------------------- | :---------------- | :------------- |
| 主存                                      |                   | 约60-80ns      |
| QPI总线传输（between sockets，not drawn） |                   | 约20ns         |
| L3 cache                                  | 约40-45 cycles    | 约15ns         |
| L2 cache                                  | 约10 cycles       | 约3ns          |
| L1 cache                                  | 约3-4 cycles      | 约1ns          |
| 寄存器                                    | 1 cycle           | 1/4ns          |



如果你的目标是让端到端的延迟只有 10毫秒，而其中花80纳秒去主存拿一些未命中数据的过程将占很重的一块。

在CPU中数据是由一个个的缓存行组成的，下面来说说缓存行。

# 2、缓存行

缓存是由缓存行组成的，通常是64字节（比较旧的处理器缓存行是32字节），并且它有效地引用主内存中的一块地址。

对于HotSpot JVM，所有对象都有两个字长的对象头。第一个字是由24位哈希码和8位标志位（如锁的状态或作为锁对象）组成的Mark Word。第二个字是对象所属类的引用。如果是数组对象还需要一个额外的字来存储数组的长度。每个对象的起始地址都对齐于8字节以提高性能。因此当封装对象的时候为了高效率，对象字段声明的顺序会被重排序成下列基于字节大小的顺序：

1. doubles (8) 和 longs (8)
2. ints (4) 和 floats (4)
3. shorts (2) 和 chars (2)
4. booleans (1) 和 bytes (1)
5. references (4/8)
6. <子类字段重复上述顺序>

由此可知一个Java的long类型是8字节，因此在一个缓存行中可以存8个long类型的变量。

非常奇妙的是如果你访问一个long数组，当数组中的一个值被加载到缓存中，它会额外加载另外7个。因此你能非常快地遍历这个数组。事实上，你可以非常快速的遍历在连续的内存块中分配的任意数据结构。

下面我们通过一个例子来说明一下利用好缓存行提升的效率有多大。

```java
public class L1CacheMiss {
    private static final int RUNS = 10;
    private static final int DIMENSION_1 = 1024 * 1024;
    private static final int DIMENSION_2 = 62;

    private static long[][] longs;

    public static void main(String[] args) throws Exception {
        Thread.sleep(10000);
        longs = new long[DIMENSION_1][];
        for (int i = 0; i < DIMENSION_1; i++) {
            longs[i] = new long[DIMENSION_2];
            for (int j = 0; j < DIMENSION_2; j++) {
                longs[i][j] = 0L;
            }
        }
        System.out.println("starting....");

        final long start = System.currentTimeMillis();
        long sum = 0L;
        for (int r = 0; r < RUNS; r++) {

            // 1----------------------------
//            for (int j = 0; j < DIMENSION_2; j++) {
//                for (int i = 0; i < DIMENSION_1; i++) {
//                    sum += longs[i][j];
//                }
//            }
            // 2----------------------------

            // 3----------------------------
            for (int i = 0; i < DIMENSION_1; i++) {
                for (int j = 0; j < DIMENSION_2; j++) {
                    sum += longs[i][j];
                }
            }
            // 4----------------------------
        }
        System.out.println("duration = " + (System.currentTimeMillis() - start));
    }
}
```

编译后运行,结果如下（运行在Mac 4核上）：

```sql
starting....
duration = 907
```

把代码里1-2的注释打开，关闭3-4的代码，运行后结果如下：

```sql
starting....
duration = 10969
```

可以发现，两次相差10几倍的效率。32位机器中的java的数组对象头共占16字节(详情见 [From Java code to Java heap](http://www.ibm.com/developerworks/java/library/j-codetoheap/index.html)), 加上62个long型一行long数据一共占512字节。所以这个二维数据是顺序排列的。

从上面我们可以知道在加载`longs[i][j]`时，`longs[i][j+1]`很可能也会被加载至cache中, 所以立即访问`longs[i][j+1]`将会命中L1 Cache，而如果你访问`longs[i+1][j]`情况就不一样了，这时候很可能会产生 cache miss导致效率低下。

以上我只是示例了在L1 Cache满了之后才会发生的cache miss。其实cache冲突还可以通过补齐来解决，下面我们来说说伪共享问题。

# 3、伪共享

并发时，当多线程修改互相独立的变量时，如果这些变量共享同一个缓存行，就会无意中影响彼此的性能，这就是伪共享。

缓存行上的写竞争是运行在SMP系统中并行线程实现可伸缩性最重要的限制因素。有人将伪共享描述成无声的性能杀手，因为从代码中很难看清楚是否会出现伪共享。

为了让可伸缩性与线程数呈线性关系，就必须确保不会有两个线程往同一个变量或缓存行中写。两个线程写同一个变量可以在代码中发现。为了确定互相独立的变量是否共享了同一个缓存行，就需要了解内存布局，或找个工具告诉我们。Intel VTune就是这样一个分析工具。本文中我将解释Java对象的内存布局以及我们该如何填充缓存行以避免伪共享。

![img](https://benjaminwhx.com/images/cache-line.png)

上图说明了伪共享的问题。在核心1上运行的线程想更新变量X，同时核心2上的线程想要更新变量Y。不幸的是，这两个变量在同一个缓存行中。每个线程都要去竞争缓存行的所有权来更新变量。如果核心1获得了所有权，缓存子系统将会使核心2中对应的缓存行失效。当核心2获得了所有权然后执行更新操作，核心1就要使自己对应的缓存行失效。这会来来回回的经过L3缓存，大大影响了性能。如果互相竞争的核心位于不同的插槽，就要额外横跨插槽连接，问题可能更加严重。

## 3.1、JDK6的解决方案

解决伪共享的办法是使用缓存行填充，使一个对象占用的内存大小刚好为64bytes或它的整数倍，这样就保证了一个缓存行里不会有多个对象。

```java
public final class FalseSharing 
    implements Runnable 
{ 
    public final static int NUM_THREADS = 4; // change 
    public final static long ITERATIONS = 500L * 1000L * 1000L; 
    private final int arrayIndex; 

    private static VolatileLong[] longs = new VolatileLong[NUM_THREADS]; 
    static 
    { 
        for (int i = 0; i < longs.length; i++) 
        { 
            longs[i] = new VolatileLong(); 
        } 
    } 

    public FalseSharing(final int arrayIndex) 
    { 
        this.arrayIndex = arrayIndex; 
    } 

    public static void main(final String[] args) throws Exception 
    { 
        final long start = System.nanoTime(); 
        runTest(); 
        System.out.println("duration = " + (System.nanoTime() - start)); 
    } 

    private static void runTest() throws InterruptedException 
    { 
        Thread[] threads = new Thread[NUM_THREADS]; 

        for (int i = 0; i < threads.length; i++) 
        { 
            threads[i] = new Thread(new FalseSharing(i)); 
        } 

        for (Thread t : threads) 
        { 
            t.start(); 
        } 

        for (Thread t : threads) 
        { 
            t.join(); 
        } 
    } 

    public void run() 
    { 
        long i = ITERATIONS + 1; 
        while (0 != --i) 
        { 
            longs[arrayIndex].value = i; 
        } 
    } 

    public final static class VolatileLong 
    { 
        public volatile long value = 0L; 
        public long p1, p2, p3, p4, p5, p6; // comment out 
    } 
}
```

VolatileLong通过填充一些无用的字段p1,p2,p3,p4,p5,p6，再考虑到对象头也占用8byte, 刚好把对象占用的内存扩展到刚好占64bytes（或者64bytes的整数倍）。这样就避免了一个缓存行中加载多个对象。但这个方法现在只能适应JAVA6 及以前的版本了。

## 3.2、JDK7的解决方案

Java 7变得更加智慧了，它淘汰或者是重新排列了无用的字段，这样我们之前的办法在Java 7下就不奏效了，但是伪共享依然会发生。我在不同的平台上实验了一些列不同的方案，并且最终发现下面的代码是最可靠的。

```java
public final class FalseSharing implements Runnable {
    public final static int NUM_THREADS = 4; // change
    public final static long ITERATIONS = 500L * 1000L * 1000L;
    private final int arrayIndex;

    private static PaddedAtomicLong[] longs = new PaddedAtomicLong[NUM_THREADS];
    static {
        for (int i = 0; i < longs.length; i++) {
            longs[i] = new PaddedAtomicLong();
        }
    }

    public FalseSharing(final int arrayIndex) {
        this.arrayIndex = arrayIndex;
    }

    public static void main(final String[] args) throws Exception {
        final long start = System.nanoTime();
        runTest();
        System.out.println("duration = " + (System.nanoTime() - start));
    }

    private static void runTest() throws InterruptedException {
        Thread[] threads = new Thread[NUM_THREADS];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new FalseSharing(i));
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }
    }

    public void run() {
        long i = ITERATIONS + 1;
        while (0 != --i) {
            longs[arrayIndex].set(i);
        }
    }

    public static long sumPaddingToPreventOptimisation(final int index) {
        PaddedAtomicLong v = longs[index];
        return v.p1 + v.p2 + v.p3 + v.p4 + v.p5 + v.p6;
    }

    public static class PaddedAtomicLong extends AtomicLong {
        public volatile long p1, p2, p3, p4, p5, p6 = 7L;
    }
}
```

可以发现我们代码里添加了一个sumPaddingToPreventOptimisation方法，使那些个用于填充的字段很难被JVM优化掉。

如果注掉sumPaddingToPreventOptimisation方法，耍小聪明的JVM还是会把你用于填充的P1-P7的字段优化掉，原因是PaddedAtomicLong类如果只对final的FalseSharing类可见（就是说PaddedAtomicLong不能再被继承了）。这样一来编译器就会“知道”它正在审视的是所有可以看到这个填充字段的代码，这样就可以证明没有行为依赖于p1到p7这些字段。那么“聪明”的JVM会把上面这些丝毫不占地方的字段统统优化掉。那么针对这样的情况，可以为方法加上sumPaddingToPreventOptimisation方法，巧妙的让PaddedAtomicLong类在FalseSharing类之外可见。

## 3.3、JDK8的解决方案

在JAVA 8中，缓存行填充终于被JAVA原生支持了。JAVA 8中添加了一个@Contended的注解，添加这个的注解，将会在自动进行缓存行填充。以上的例子可以改为：

```java
public final class FalseSharing implements Runnable {  
    public static int NUM_THREADS = 4; // change  
    public final static long ITERATIONS = 500L * 1000L * 1000L;  
    private final int arrayIndex;  
    private static VolatileLong[] longs;  

    public FalseSharing(final int arrayIndex) {  
        this.arrayIndex = arrayIndex;  
    }  

    public static void main(final String[] args) throws Exception {  
        Thread.sleep(10000);  
        System.out.println("starting....");  
        if (args.length == 1) {  
            NUM_THREADS = Integer.parseInt(args[0]);  
        }  

        longs = new VolatileLong[NUM_THREADS];  
        for (int i = 0; i < longs.length; i++) {  
            longs[i] = new VolatileLong();  
        }  
        final long start = System.nanoTime();  
        runTest();  
        System.out.println("duration = " + (System.nanoTime() - start));  
    }  

    private static void runTest() throws InterruptedException {  
        Thread[] threads = new Thread[NUM_THREADS];  
        for (int i = 0; i < threads.length; i++) {  
            threads[i] = new Thread(new FalseSharing(i));  
        }  
        for (Thread t : threads) {  
            t.start();  
        }  
        for (Thread t : threads) {  
            t.join();  
        }  
    }  

    public void run() {  
        long i = ITERATIONS + 1;  
        while (0 != --i) {  
            longs[arrayIndex].value = i;  
        }  
    }  
}

@Contended
public class VolatileLong {
    public volatile long value = 0L;  
}
```

执行时，必须加上虚拟机参数-XX:-RestrictContended，@Contended注释才会生效。很多文章把这个漏掉了，那样的话实际上就没有起作用。

@Contended注释还可以添加在字段上，今后再写文章详细介绍它的用法。

（后记：以上代码基于32位JDK测试，64位JDK下，对象头大小不同，有空再测试一下）

## 3.4、执行结果

运行上面的代码，增加线程数以及添加/移除缓存行的填充，下面的图2描述了我得到的结果。这是在我4核Nehalem上测得的运行时间。

![img](https://benjaminwhx.com/images/duration.png)

从不断上升的测试所需时间中能够明显看出伪共享的影响。没有缓存行竞争时，我们几近达到了随着线程数的线性扩展。

这并不是个完美的测试，因为我们不能确定这些VolatileLong会布局在内存的什么位置。它们是独立的对象。但是经验告诉我们同一时间分配的对象趋向集中于一块。

所以你也看到了，伪共享可能是无声的性能杀手。

# 4、参考

[从Java视角理解系统结构（二）CPU缓存](http://ifeve.com/from-javaeye-cpu-cache/)

[剖析Disruptor:为什么会这么快？（二）神奇的缓存行填充](http://ifeve.com/disruptor-cacheline-padding/)

[伪共享(False Sharing)](http://ifeve.com/falsesharing/)

[Java 7与伪共享的新仇旧恨](http://ifeve.com/false-shareing-java-7-cn/)

[伪共享和缓存行填充，从Java 6, Java 7 到Java 8](https://www.cnblogs.com/Binhua-Liu/p/5620339.html)