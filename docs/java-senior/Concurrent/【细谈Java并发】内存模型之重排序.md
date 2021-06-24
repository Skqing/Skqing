### 【细谈Java并发】内存模型之重排序

转载自[【细谈Java并发】内存模型之重排序](https://benjaminwhx.com/2018/05/14/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E5%86%85%E5%AD%98%E6%A8%A1%E5%9E%8B%E4%B9%8B%E9%87%8D%E6%8E%92%E5%BA%8F/)

# 1、什么是重排序

重排序是指编译器和处理器为了优化程序性能而对指令序列进行重新排序的一种手段。

请先看一段代码：

```java
public static void main(String[] args) throws InterruptedException {
    Thread one = new Thread(new Runnable() {
        public void run() {
            a = 1;    // 1
            x = b;    // 2
        }
    });

    Thread other = new Thread(new Runnable() {
        public void run() {
            b = 1;    // 3
            y = a;    // 4
        }
    });
    one.start();other.start();
    one.join();other.join();
    System.out.println(“(” + x + “,” + y + “)”);
}
```

很容易想到这段代码的运行结果可能为(1,0)、(0,1)或(1,1)，因为线程one可以在线程two开始之前就执行完了，也有可能反之，甚至有可能二者的指令是同时或交替执行的。

然而，这段代码的执行结果也可能是(0,0)。因为可能执行的顺序为 2341，程序执行时发生了重排序。

我们让上面的代码在循环里反复的执行来复现这种情况，不出意外很快就能得出结果。

```java
public class PossibleReordering {
    private static int x = 0, y = 0;
    private static int a = 0, b =0;

    public static void main(String[] args) throws InterruptedException {
        int i = 0;
        for(;;) {
            i++;
            x = 0; y = 0;
            a = 0; b = 0;
            Thread one = new Thread(new Runnable() {
                public void run() {
                    //由于线程one先启动，下面这句话让它等一等线程two. 读着可根据自己电脑的实际性能适当调整等待时间.
                    shortWait(100000);
                    a = 1;
                    x = b;
                }
            });

            Thread other = new Thread(new Runnable() {
                public void run() {
                    b = 1;
                    y = a;
                }
            });
            one.start();other.start();
            one.join();other.join();
            String result = "第" + i + "次 (" + x + "," + y + "）";
            if(x == 0 && y == 0) {
                System.err.println(result);
                break;
            } else {
                System.out.println(result);
            }
        }
    }


    public static void shortWait(long interval){
        long start = System.nanoTime();
        long end;
        do{
            end = System.nanoTime();
        }while(start + interval >= end);
    }
}
```

# 2、重排序的类型

在执行程序时，为了提高性能，编译器和处理器常常会对指令做重排序。重排序分3种类型。

1. 编译器优化的重排序，编译器在不改变单线程程序语义的前提下，可以重新安排语义。
2. 指令级并行的重排序。现代处理器采用了指令级并行技术（Instruction-Level Parallelism，ILP）来将多条指令重叠执行。如果不存在数据依赖性，处理器可以改变语句对应机器指令的执行顺序。
3. 内存系统的重排序。由于处理器使用缓存和读/写缓冲区，这使得加载和存储操作看上去可能是在乱序执行。

从Java源代码到最终实际执行的指令序列，会分别经历下面3种重排序。

![img](https://benjaminwhx.com/images/reorder1.jpg)

对于编译器的重排序，JMM的编译器重排序规则会禁止特定类型的编译器重排序（不是所有的编译器重排序都要禁止）。

对于处理器的重排序，JMM的处理器重排序规则会要求Java编译器在生成指令序列时，插入特定类型的内存屏障来禁止特定类型的处理器重排序。（关于内存屏障，可以参考我之前写的volatile的文章，里面有详细的讲解：[【细谈Java并发】内存模型之volatile](http://benjaminwhx.com/2018/05/13/【细谈Java并发】内存模型之volatile/)）

# 3、as-if-serial语义

as-if-serial语义的意思是：不管怎么重排序，（单线程）程序的执行结果不能被改变。编译器、runtime和处理器都必须遵守as-if-serial语义。

为了遵守as-if-serial语义，编译器和处理器不会对存在数据依赖关系的操作做重排序，因为这种重排序会改变执行结果。但是，如果操作之间不存在数据依赖关系，这些操作就可以被编译器和处理器重排序。

为了具体说明，请看下面计算圆面积的代码示例。

```java
double pi = 3.14;            // A
double r = 1.0;                // B
double area = pi * r * r;    // C
```

因为上面A和C以及B和C都存在数据依赖关系，但是A和B不存在数据以来关系。所以A和B的执行实际上可以发生重排序。

这其实给大家都创建了一个幻觉一样：**单线程程序时按程序的顺序来执行的**。as-if-serial语义使**单线程**程序员无需担心重排序会干扰他们，也无需担心内存可见性问题。(这其实就是上面说的编译器重排序)

为保证as-if-serial语义，Java异常处理机制也会为重排序做一些特殊处理。例如在下面的代码中，y = 0 / 0可能会被重排序在x = 2之前执行，为了保证最终不致于输出x = 1的错误结果，JIT在重排序时会在catch语句中插入错误代偿代码，将x赋值为2，将程序恢复到发生异常时应有的状态。这种做法的确将异常捕捉的逻辑变得复杂了，但是JIT的优化的原则是，尽力优化正常运行下的代码逻辑，哪怕以catch块逻辑变得复杂为代价，毕竟，进入catch块内是一种“异常”情况的表现。

```java
public class Reordering {
    public static void main(String[] args) {
        int x, y;
        x = 1;
        try {
            x = 2;
            y = 0 / 0;    
        } catch (Exception e) {
        } finally {
            System.out.println("x = " + x);
        }
    }
}
```

# 4、happens-before

happens-before是JMM最核心的概念。对应Java程序员来说，理解happens-before是理解JMM的关键。

> JSR-133使用happens-before的概念来阐述操作之间的内存可见性。在JMM中，如果一个操作执行的结果需要对另一个操作可见，那么这2个操作之间必须要存在happens-before关系。这里提到的2个操作既可以是一个线程之内，也可以是不同线程之间。

下图是JMM的设计示意图。

![img](https://benjaminwhx.com/images/reorder2.jpg)

## 4.1、定义

JMM通过happens-before关系向程序员提供跨线程的内存可见性保证。

《JSR-133：Java Memory Model and Thread Specification》对happens-before关系的定义如下：

1. 如果一个操作happens-before另一个操作，那么第一个操作的执行结果将对第二个操作可见，而且第一个操作的执行顺序排在第二个操作之前。
2. 两个操作之间存在happens-before关系，并不意味着Java平台的具体实现必须要按照happens-before关系指定的顺序来执行。如果重排序之后的执行结果，与按happens-before关系来执行的记过一致，那么这种重排序不非法（也就是说，JMM允许这种重排序）。

**上面的1）是JMM对程序员的承诺。**从程序员的角度来看是这样的，意思就是就算JMM把它重排序了程序员也看不到。

**上面的2）是JMM对编译器和处理器重排序的约束原则。**JMM这么做的原因是：程序员对这两个操作是否真的被重排序并不关心，程序员关心的是程序执行的语义不能被改变（即结果不能被改变）。

> 因此，happens-before关系本质上和as-if-serial语义是一回事。
>
> 1. as-if-serial语义保证单线程内程序的执行结果不被改变，happens-before关系保证正确同步的多线程程序的执行结果不被改变。
> 2. as-if-serial语义给编写单线程程序的程序员创造了一个幻境：单线程程序员是按程序的顺序来执行的。happens-before关系给编写正确同步的多线程程序的程序员创造了一个幻境：正确同步的多线程程序时按happens-before指定的顺序来执行的。

**as-if-serial语义和happens-before这么做的目的，都是为了在不改变程序执行结果的前提下，尽可能地提高程序执行的并行度。**

## 4.2、规则

《JSR-133：Java Memory Model and Thread Specification》定义了如下happens-before规则。

1. 程序顺序规则：一个线程中的每个操作，happens-before于该线程中的任意后续操作。
2. 监视器锁规则：对一个锁的解锁，happens-before于随后对这个锁的加锁。
3. volatile变量规则：对一个volatile域的写，happens-before于任意后续对这个volatile域的读。
4. 传递性：如果A happens-before B，且B happens-before C，那么A happens-before C。
5. start()规则：如果线程A执行操作ThreadB.start()，那么A线程的ThreadB.start()操作happens-before于线程B中的任意操作。
6. join()规则：如果线程A执行操作ThreadB.join()并成功返回，那么线程B中的任意操作happens-before于线程A从ThreadB.join()操作成功返回。

# 5、禁止重排序

上面说了，重排序分为编译器的重排序和处理器的重排序，我们来看看volatile、synchronized以及final在JMM中是如何做到防止重排序的。

## 5.1、volatile

volatile的重排序在我之前的文章：[【细谈Java并发】内存模型之volatile](./%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E5%86%85%E5%AD%98%E6%A8%A1%E5%9E%8B%E4%B9%8Bvolatile/) **1.2 指令重排和内存屏障** 里已经详细描述过了，这里就不再赘述。

## 5.2、synchronized

因为锁的语义和volatile的语义类似，也是使用了内存屏障，这里也不再赘述了。

## 5.3、final

> 在JSR-133中，增强了final的语义：通过为final域增加写和读重排序规则，可以为Java程序员提供初始化安全保证，只要对象时正确构造的（被构造对象的引用在构造函数中没有“逸出”），那么不需要使用同步（指lock和volatile的使用）就可以保证任意线程都能看到这个final域在构造函数中被初始化之后的值。

对于final域，编译器和处理器要遵守两个重排序规则。

1. 在构造函数内对一个final域的写入，与随后把这个被构造对象的引用赋值给一个引用变量，这两个操作之间不能重排序。（防止拿到对象时，final域还未赋值）
2. 初次读一个包含final域的对象的引用，与随后初次读这个final域，这两个操作之间不能重排序。

下面通过一些示例性的代码来分别说明这两个规则。

```java
public class FinalExample {
    int i;                
    final int j;         
    static FinalExample obj;

    public FinalExample() {        
        i = 1;                      // 写普通域 1
        j = 2;                      // 写final域 2
    }

    public static void writer() {
        obj = new FinalExample();   // 3
    }

    public static void reader() {
        FinalExample object = obj;  // 读对象引用 4
        int a = object.i;           // 读普通域 5
        int b = object.j;           // 读final域 6
    }
}
```

这里假设一个线程A执行writer()方法，随后领一个线程B执行reader()方法、下面我们通过这两个线程的交互来说明这两个规则。

### 5.3.1、写final域的重排序规则

写final域的重排序规则禁止把final域的写重排序到构造函数之外。这个规则的实现包含下面2个方面。

1. JMM禁止编译器把final域的写重排序到构造函数之外。
2. 编译器会在final域的写之后，构造函数return之前，插入一个StoreStore屏障。这个屏障禁止处理器把final域的写重排序到构造函数之外。

上面的例子在这个规则下可以确保：线程B执行到4的时候，2已经完成初始化值了。

### 5.3.2、读final域的重排序规则

读final域的重排序规则是，在一个线程中，初次读对象引用与初次读该对象包含的final域，JMM禁止处理器重排序这两个操作（注意，这个规则仅仅针对处理器）。编译器会在读final域操作的前面插入一个LoadLoad屏障。

上面的例子在这个规则下可以确保：线程B执行4和6不能重排序，在4和6之间加入LoadLoad屏障。

### 5.3.3、final域为引用类型

```java
public class FinalReferenceExample {
    final int[] intArray;   // final是引用类型
    static FinalReferenceExample obj;

    public FinalReferenceExample() {
        intArray = new int[1];          // 1
        intArray[0] = 1;                // 2
    }

    public static void writerOne() {
        obj = new FinalReferenceExample();  // 3
    }

    public static void writerTwo() {
        obj.intArray[0] = 2;            // 4
    }

    public static void reader() {
        if (obj != null) {              // 5
            int temp1 = obj.intArray[0];// 6
        }
    }
}
```

本例final域为一个引用类型，它引用一个int型的数组对象。对于引用类型，写final域的重排序规则对编译器和处理器增加了如下约束：在构造函数内对一个final引用的对象的成员域的写入，与随后在构造函数外把这个被构造对象的引用赋值给一个引用变量，这两个操作之间不能重排序。

上面的例子在这个规则下可以确保：1不能喝3重排序外，2和3也不能重排序。但是4和6不能确保，如果想要6看到4的写入，需要使用同步原语（lock或volatile）来确保内存可见性。

### 5.3.4、避免final从构造函数内“逸出”

前面的写final域的重排序规则，其实还需要得到一个保证：在构造函数内部，不能让这个被构造对象的引用为其他线程所见，也就是对象引用不能在构造函数中“逸出”。为了说明这个问题，看下面的代码。

```java
public class FinalReferenceEscapeExample {
    final int i;
    static FinalReferenceEscapeExample obj;

    public FinalReferenceEscapeExample() {
        i = 1;                  // 1 写final域
        obj = this;             // 2 this引用在此"逸出"
    }

    public static void writer() {
        new FinalReferenceEscapeExample();
    }

    public static void reader() {
        if (obj != null) {      // 3
            int temp = obj.i;   // 4
        }
    }
}
```

假设一个线程A执行writer方法，线程B执行reader方法。这里的1和2可能会产生重排序，导致1没有初始化好对象就逸出了。

所以在构造函数返回前，被构造对象的引用不能为其他线程所见，因为此时的final域可能还没有被初始化。在构造函数返回后，任意线程都将保证能看到final域正确初始化后的值。

> 注：上面我们提到的写和读final域会插入一个屏障。由于X86处理器不会对写-写操作做重排序，所以在X86处理器中，写final域需要的StoreStore屏障会被省略掉。同样，由于X86处理器不会对存在间接依赖关系的操作做重排序，所以在X86处理器中，读final域需要的LoadLoad屏障也会被省略掉。也就是说，在X86处理器中，final域的读/写不会插入任何内存屏障！