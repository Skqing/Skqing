### 【细谈Java并发】谈谈ThreadLocal

转载自[【细谈Java并发】谈谈ThreadLocal](https://benjaminwhx.com/2018/04/28/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88ThreadLocal/)

# 1、前言

ThreadLocal 的作用是提供线程内的局部变量，这种变量在线程的生命周期内起作用，减少同一个线程内多个函数或者组件之间一些公共变量的传递的复杂度。但是如果滥用 ThreadLocal，就可能会导致内存泄漏。们先看看它的实现原理，只有知道了实现原理，才好判断它是否符合自己的业务场景。

# 2、ThreadLocal是什么？

首先，它是一个数据结构，有点像HashMap，可以保存”key : value”键值对，但是一个ThreadLocal只能保存一个，并且各个线程的数据互不干扰。我们也可以在同一个线程里面使用多个ThreadLocal来保存值，它们会放在同一个map里的不同entry上。

```java
ThreadLocal<String> threadLocalA = new ThreadLocal<>();
threadLocalA.set("张三");
// 张三
String name = threadLocalA.get();

ThreadLocal<String> threadLocalB = new ThreadLocal<>();
threadLocalB.set("李四");
// 李四
String name2 = threadLocalB.get();
```

上面的threadLocalA和threadLocalB都是同一个线程中操作，如果在另外一个线程中是拿不到这两个值的。

# 3、ThreadLocal实现原理

![img](https://benjaminwhx.com/images/threadlocal1.png)

每个Thread 维护一个 ThreadLocalMap 映射表，在ThreadLoalMap中，也是初始化一个大小16的Entry数组，Entry对象用来保存每一个key-value键值对，只不过这里的key永远都是ThreadLocal对象，是不是很神奇，通过ThreadLocal对象的set方法，结果把ThreadLocal对象自己当做key，放进了ThreadLocalMap中。这里需要注意的是，ThreadLocalMap的Entry是继承WeakReference，和HashMap很大的区别是，Entry中没有next字段，所以就不存在链表的情况了。

# 4、源码分析

set和get方法是threadLocal中的重要方法，它们的原理很简单，直接看源码。

```java
/**
 * 把threadLocal对象和对应的值设置到当前线程的threadLocalMap中。
 * 可以通过{@link #initialValue}方法设置当前thread-locals的初始值
 */
public void set(T value) {
    Thread t = Thread.currentThread();
    // 获取当前线程对应的threadLocalMap
    ThreadLocalMap map = getMap(t);
    if (map != null)
        map.set(this, value);
    else
        createMap(t, value);
}

/**
 * 返回当前线程中thread-local对应的值。
 * 如果没有对应的值，调用{@link #initialValue}方法来设置进行初始化操作。
 */
public T get() {
    Thread t = Thread.currentThread();
    // 获取当前线程对应的threadLocalMap
    ThreadLocalMap map = getMap(t);
    if (map != null) {
        // 根据当前threadLocal定位到map的Entry
        ThreadLocalMap.Entry e = map.getEntry(this);
        if (e != null) {
            T result = (T)e.value;
            return result;
        }
    }
    // 初始化值
    return setInitialValue();
}
```

set方法很简单，首先获取当前线程对应的threadLocalMap，如果存在map直接调用map的set方法，不存在则新建map。

当执行get方法时，也是一样的道理，拿到当前线程对应的threadLocalMap，如果存在map则直接返回值，不存在则进行初始化操作。

接下来我们看看里面相关的一些方法：

```java
/**
 * 返回传入线程的threadLocals变量，指向ThreadLocalMap
 */
ThreadLocalMap getMap(Thread t) {
    return t.threadLocals;
}

/**
 * 传入的线程t指向创建出来的ThreadLocalMap
 */
void createMap(Thread t, T firstValue) {
    t.threadLocals = new ThreadLocalMap(this, firstValue);
}
```

可以发现，创建map的时候它把线程t的内部threadLocals变量指向了新建的ThreadLocalMap。

## ThreadLocalMap

那么这个ThreadLocalMap究竟是长什么样子呢？我们来揭开它的神秘面纱。

```java
static class ThreadLocalMap {

    static class Entry extends WeakReference<ThreadLocal<?>> {
        Object value;
        Entry(ThreadLocal<?> k, Object v) {
            super(k);
            value = v;
        }
    }

    // 初始化空间，必须为2的n次方
    private static final int INITIAL_CAPACITY = 16;
    private Entry[] table;
    private int size = 0;
    // 阈值，默认0
    private int threshold;
}
```

内部结构很简单，Map里面维护着一个Entry数组，而这个Entry的key是弱引用的，这样GC的时候就会把key变为null，看核心方法之前我们先来了解一下ThreadLocalMap的hash算法，看看它和hashMap的有什么区别：

```java
private final int threadLocalHashCode = nextHashCode();
private static AtomicInteger nextHashCode = new AtomicInteger();
private static final int HASH_INCREMENT = 0x61c88647;
private static int nextHashCode() {
    return nextHashCode.getAndAdd(HASH_INCREMENT);
}
```

可以看到每一个ThreadLocal都维护着一个threadLocalHashCode变量，而它取的是共享的static变量nextHashCode。所以每个threadLocal的nextHashCode都不同。但是为什么这里使用了`0x61c88647`这个数字呢？

ThreadLocal其实是使用了开放定址法的线性探测来解决碰撞，而这里利用特殊的哈希码0x61c88647大大降低碰撞的几率。下面是我进行的一段测试：

```java
public static void main(String[] args) {
    for (int i = 0; i < 16; ++i) {
        System.out.print(nextHashCode() & 15);
        System.out.print(" ");
    }
    System.out.println();
    nextHashCode = new AtomicInteger();
    for (int i = 0; i < 32; ++i) {
        System.out.print(nextHashCode() & 31);
        System.out.print(" ");
    }
}
```

输出结果为：

```java
0 7 14 5 12 3 10 1 8 15 6 13 4 11 2 9 
0 7 14 21 28 3 10 17 24 31 6 13 20 27 2 9 16 23 30 5 12 19 26 1 8 15 22 29 4 11 18 25
```

可以看出来产生的哈希码分布真的是很均匀，而且没有任何冲突啊, 太神奇了, [javaspecialists](http://www.javaspecialists.eu/archive/Issue164.html)中的一篇文章有对它的一些描述：

```java
This number represents the golden ratio (sqrt(5)-1) times two to the power of 31 ((sqrt(5)-1) * (2^31)). The result is then a golden number, either 2654435769 or -1640531527.
```

以及

```java
We established thus that the HASH_INCREMENT has something to do with fibonacci hashing, using the golden ratio. If we look carefully at the way that hashing is done in the ThreadLocalMap, we see why this is necessary. The standard java.util.HashMap uses linked lists to resolve clashes. The ThreadLocalMapsimply looks for the next available space and inserts the element there. It finds the first space by bit masking, thus only the lower few bits are significant. If the first space is full, it simply puts the element in the next available space. The HASH_INCREMENT spaces the keys out in the sparce hash table, so that the possibility of finding a value next to ours is reduced.
```

这与[fibonacci hashing](http://brpreiss.com/books/opus4/html/page214.html)(斐波那契散列法)以及黄金分割有关，具体可研究中的 6.4 节Hashing部分

具体可以看看：[ThreadLocal 和神奇的 0x61c88647](http://jerrypeng.me/2013/06/thread-local-and-magical-0x61c88647/)和[Why 0x61c88647?](http://www.javaspecialists.eu/archive/Issue164.html) 可以参考我翻译后的文章：[为什么使用0x61c88647](http://benjaminwhx.com/2018/01/26/为什么使用0x61c88647/)

## set方法

刚刚看到的set方法，它调用了ThreadLocalMap的set方法。

```java
private void set(ThreadLocal<?> key, Object value) {

    Entry[] tab = table;
    int len = tab.length;
    int i = key.threadLocalHashCode & (len-1);

    for (Entry e = tab[i];
         e != null;
         e = tab[i = nextIndex(i, len)]) {
        ThreadLocal<?> k = e.get();

        // 替换原来的值为value
        if (k == key) {
            e.value = value;
            return;
        }

        if (k == null) {
            replaceStaleEntry(key, value, i);
            return;
        }
    }

    tab[i] = new Entry(key, value);
    int sz = ++size;
    if (!cleanSomeSlots(i, sz) && sz >= threshold)
        rehash();
}
```

set方法的逻辑是先去找key hash后所属的Entry位置i

1. 如果i位置的Entry是空的话，让i指向new出来的Entry。
2. 如果i位置的Entry不为空，判断Entry的key是不是和传入的key相同，如果相同覆盖value返回。如果Entry的key是空的话，进行替换过期Entry的操作（大概就是继续找到和key相同key的Entry设置值，并和i进行swap操作，下面讲这个方法），如果Entry的key不等于传入的key，循环重复此操作。直到下一个Entry为空停止。

最后进行清理过期Entry以及扩容的校验操作。

下面看一下replaceStaleEntry这个方法：

```java
private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                               int staleSlot) {
    Entry[] tab = table;
    int len = tab.length;
    Entry e;

    int slotToExpunge = staleSlot;
    // 向左查找最后一个key为null的Entry直到Entry为null停止
    for (int i = prevIndex(staleSlot, len);
         (e = tab[i]) != null;
         i = prevIndex(i, len))
        if (e.get() == null)
            slotToExpunge = i;

    for (int i = nextIndex(staleSlot, len);
         (e = tab[i]) != null;
         i = nextIndex(i, len)) {
        ThreadLocal<?> k = e.get();

        if (k == key) {
            e.value = value;

            tab[i] = tab[staleSlot];
            tab[staleSlot] = e;

            // 如果存在，则开始擦除之前的过期Entry
            if (slotToExpunge == staleSlot)
                slotToExpunge = i;
            cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
            return;
        }

        // 设置需要擦除的过期index为当前的i
        if (k == null && slotToExpunge == staleSlot)
            slotToExpunge = i;
    }

    // key没有找到，在staleSlot位置设置值
    tab[staleSlot].value = null;
    tab[staleSlot] = new Entry(key, value);

    // 找到需要擦除的过期Entry，clean
    if (slotToExpunge != staleSlot)
        cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
}
```

这个方法看起来很复杂，其实大部分的操作都是在进行过期Entry查找的操作（GC会把key变为null，这时候要及时清理Entry，不然会造成内存泄露问题）

1. 从staleSlot开始向左找，找到Entry的key是空的记录index，后面进行清除操作。直到Entry==null退出for循环。
2. 从staleSlot开始向右找，直到Entry==null退出for循环。在这期间，如果找到key和传入的key相等，替换value，并和staleSlot位置的Entry进行互换操作，如果找到Entry的key是空的并且刚才向左找的时候没有找到空记录，设置需要清理的index，后面进行清除操作。其他情况继续循环操作。
3. 上面循环找了之后还没有找到相同key的话，就在staleSlot位置设置一个新的Entry。
4. 根据slotToExpunge进行清除操作。
   我们可以看到，上面的方法只要你进行了操作，都会触发清除操作，目的就是不让内存泄露发生。我们来看看主要的expungeStaleEntry方法。
   ![img](https://benjaminwhx.com/images/threadlocal2.png)

```java
private int expungeStaleEntry(int staleSlot) {
    Entry[] tab = table;
    int len = tab.length;

    // 擦除staleSlot位置的Entry
    tab[staleSlot].value = null;
    tab[staleSlot] = null;
    size--;

    // 当遇到null时进行reHashing
    Entry e;
    int i;
    // 从定位的位置staleSlot一直+1循环，直到Entry==null为止（遇到最大index从0开始）
    for (i = nextIndex(staleSlot, len);
         (e = tab[i]) != null;
         i = nextIndex(i, len)) {
        ThreadLocal<?> k = e.get();
        // Entry的key为空，清空Entry以及它的值
        if (k == null) {
            e.value = null;
            tab[i] = null;
            size--;
        } else {
            // k的hash定位的位置不是当前i的位置，把i的值放到h位置中（如果h位置有值存在，一直向右找到Entry为null的位置）
            int h = k.threadLocalHashCode & (len - 1);
            if (h != i) {
                tab[i] = null;

                while (tab[h] != null)
                    h = nextIndex(h, len);
                tab[h] = e;
            }
        }
    }
    return i;
}
```

通过reHashing清除位于过期位置(staleSlot)和Entry==null的位置之间任何可能冲突的Entry，它的作用就是清理垃圾和解决hash冲突，这样的话有助于让GC去回收内存；

## get方法

和set方法类似，get方法调用了ThreadLocalMap的getEntry方法。

```java
private Entry getEntry(ThreadLocal<?> key) {
    int i = key.threadLocalHashCode & (table.length - 1);
    Entry e = table[i];
    if (e != null && e.get() == key)
        return e;
    else
        return getEntryAfterMiss(key, i, e);
}

private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
    Entry[] tab = table;
    int len = tab.length;

    while (e != null) {
        ThreadLocal<?> k = e.get();
        if (k == key)
            return e;
        if (k == null)
            expungeStaleEntry(i);
        else
            i = nextIndex(i, len);
        e = tab[i];
    }
    return null;
}
```

get操作还是先定位，然后比较Entry的key是不是和传入的相等，不相等一直向右寻找，没有找到则返回null。

## remove方法

```java
public void remove() {
     ThreadLocalMap m = getMap(Thread.currentThread());
     if (m != null)
         m.remove(this);
 }

private void remove(ThreadLocal<?> key) {
    Entry[] tab = table;
    int len = tab.length;
    // 定位Entry的位置
    int i = key.threadLocalHashCode & (len-1);
    // 从定位的位置i一直+1循环，直到Entry==null为止（遇到最大index从0开始）
    for (Entry e = tab[i];
         e != null;
         e = tab[i = nextIndex(i, len)]) {
         // 条目的ThreadLocal和传入的key相同
        if (e.get() == key) {
            // 把ThreadLocal的引用变为null
            e.clear();
            expungeStaleEntry(i);
            return;
        }
    }
}
```

可以看出remove方法也是从定位的位置i一直+1循环，直到Entry==null为止，直到找到key和传入的key一样的记录，把key、value、Entry都变为null，等待回收。

但是这个方法什么时候要调用呢？我们下面来说说内存泄露的问题，以及不调用remove所带来的影响。

# 5、ThreadLocal为什么会内存泄漏

ThreadLocalMap使用ThreadLocal的弱引用作为key，如果一个ThreadLocal没有外部强引用来引用它，那么系统 GC 的时候，这个ThreadLocal势必会被回收，这样一来，ThreadLocalMap中就会出现key为null的Entry，就没有办法访问这些key为null的Entry的value，如果当前线程再迟迟不结束的话，这些key为null的Entry的value就会一直存在一条强引用链：Thread Ref -> Thread -> ThreaLocalMap -> Entry -> value永远无法回收，造成内存泄漏。

其实，ThreadLocalMap的设计中已经考虑到这种情况，也加上了一些防护措施：在ThreadLocal的get(),set(),remove()的时候都会清除线程ThreadLocalMap里所有key为null的value。

但是这些被动的预防措施并不能保证不会内存泄漏：

- 使用线程池的时候，这个线程执行任务结束，ThreadLocal对象被回收了，线程放回线程池中不销毁，这个线程一直不被使用，导致内存泄漏。
- 分配使用了ThreadLocal又不再调用get(),set(),remove()方法，那么这个期间就会发生内存泄漏。

# 6、为什么使用弱引用

从表面上看内存泄漏的根源在于使用了弱引用。网上的文章大多着重分析为什么会内存泄漏，但是另一个问题也同样值得思考：为什么使用弱引用？为什么不用强引用？

我们先来看看官方文档的说法：

> To help deal with very large and long-lived usages, the hash table entries use WeakReferences for keys.
> 为了应对非常大和长时间的用途，哈希表使用弱引用的 key。

下面我们分两种情况讨论：

- **key 使用强引用**：引用的ThreadLocal的对象被回收了，但是ThreadLocalMap还持有ThreadLocal的强引用，如果没有手动删除，ThreadLocal不会被回收，导致Entry内存泄漏。
- **key 使用弱引用**：引用的ThreadLocal的对象被回收了，由于ThreadLocalMap持有ThreadLocal的弱引用，即使没有手动删除，ThreadLocal也会被回收。value在下一次ThreadLocalMap调用set,get的时候会被清除。

比较两种情况，我们可以发现：由于ThreadLocalMap的生命周期跟Thread一样长，如果都没有手动删除对应key，都会导致内存泄漏，但是使用弱引用可以多一层保障：弱引用ThreadLocal不会内存泄漏，对应的value在下一次ThreadLocalMap调用set,get,remove的时候会被清除。

因此，ThreadLocal内存泄漏的根源是：由于ThreadLocalMap的生命周期跟Thread一样长，如果没有手动删除对应key就会导致内存泄漏，而不是因为弱引用。

# 7、ThreadLocal 最佳实践

综合上面的分析，我们可以理解ThreadLocal内存泄漏的前因后果，那么怎么避免内存泄漏呢？**每次使用完ThreadLocal，都调用它的remove()方法，清除数据。**

在使用线程池的情况下，没有及时清理ThreadLocal，不仅是内存泄漏的问题，更严重的是可能导致业务逻辑出现问题。所以，使用ThreadLocal就跟加锁完要解锁一样，用完就清理。