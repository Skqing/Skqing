### 【细谈Java并发】为Future增加Callback

转载自[为Future增加Callback](https://benjaminwhx.com/2018/08/26/%E4%B8%BAFuture%E5%A2%9E%E5%8A%A0Callback/)

Future是Java5增加的类，它用来描述一个异步计算的结果。你可以使用 `isDone` 方法检查计算是否完成，或者使用 `get` 方法阻塞住调用线程，直到计算完成返回结果。你也可以使用 `cancel` 方法停止任务的执行。

```java
public class FutureDemo {

    public static void main(String[] args) {
        ExecutorService es = Executors.newFixedThreadPool(10);
        Future<Integer> f = es.submit(() ->{
            // 长时间的任务计算
            Thread.sleep(10000);
            // 返回结果
            return 100;
        });

        // 做一些其他操作
        // ....

        Integer result = f.get();
        System.out.println(result);

//        while (f.isDone()) {
//            System.out.println(result);
//        }
    }
}
```

在这个例子中，我们往线程池中提交了一个任务并立即返回了一个Future对象，接着可以做一些其他操作，最后利用它的 `get` 方法阻塞等待结果或 `isDone`方法轮询等待结果（关于Future的原理可以参考之前的文章：[【细谈Java并发】谈谈FutureTask](./%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E8%B0%88%E8%B0%88FutureTask/)）

虽然这些方法提供了异步执行任务的能力，但是对于结果的获取却还是很不方便，只能通过阻塞或者轮询的方式得到任务的结果。

阻塞的方式显然和我们的异步编程的初衷相违背，轮询的方式又会耗费无谓的CPU资源，而且也不能及时的得到计算结果，为什么不能用观察者设计模式当计算结果完成及时通知监听者呢？

很多语言，比如Node.js，采用Callback的方式实现异步编程。Java的一些框架，比如Netty，自己扩展了Java的 `Future` 接口，提供了 `addListener` 等多个扩展方法。Google的guava也提供了通用的扩展Future：`ListenableFuture` 、 `SettableFuture` 以及辅助类 `Futures` 等，方便异步编程。为此，Java终于在8这个版本中增加了一个能力更强的Future类：`CompletableFuture` 。它提供了非常强大的Future的扩展功能，可以帮助我们简化异步编程的复杂性，提供了函数式编程的能力，可以通过回调的方式处理计算结果。

# Netty-Future

首先引入Maven依赖：

```java
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-all</artifactId>
    <version>4.1.29.Final</version>
</dependency>
public class NettyFutureDemo {

    public static void main(String[] args) throws InterruptedException {
        EventExecutorGroup group = new DefaultEventExecutorGroup(4); // 4 threads
        System.out.println("begin:" + DateUtils.getNow());
        Future<Integer> f = group.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                System.out.println("开始耗时计算:" + DateUtils.getNow());
                Thread.sleep(3000);
                System.out.println("结束耗时计算:" + DateUtils.getNow());
                return 100;
            }
        });
        f.addListener(new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> objectFuture) throws Exception {
                System.out.println("计算结果:" + objectFuture.get());
            }
        });
        System.out.println("end:" + DateUtils.getNow());
        new CountDownLatch(1).await();//不让守护线程退出
    }
}
```

输出结果：

```java
begin:2018-08-26 04:56:40:779
end:2018-08-26 04:56:40:783
开始耗时计算:2018-08-26 04:56:40:783
结束耗时计算:2018-08-26 04:56:43:789
计算结果:100
```

从结果可以看出，耗时计算结束后自动触发Listener的完成方法，避免了主线程无谓的阻塞等待，那么它究竟是怎么做到的呢？

## 一探源码

`DefaultEventExecutorGroup` 实现了 `EventExecutorGroup` 接口，而 `EventExecutorGroup` 则是实现了JDK `ScheduledExecutorService` 接口的线程组接口，所以它拥有线程池的所有方法。然而它却把所有返回 `java.util.concurrent.Future` 的方法重写为返回 `io.netty.util.concurrent.Future` ，把所有返回 `java.util.concurrent.ScheduledFuture` 的方法重写为返回 `io.netty.util.concurrent.ScheduledFuture` 。

```java
public interface EventExecutorGroup extends ScheduledExecutorService, Iterable<EventExecutor> {
    /**
     * 从组里返回一个EventExecutor
     */
    EventExecutor next();

    Iterator<EventExecutor> iterator();

    Future<?> submit(Runnable task);
    <T> Future<T> submit(Runnable task, T result);
    <T> Future<T> submit(Callable<T> task);

    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}
```

`EventExecutorGroup` 的submit方法因为 `newTaskFor` 的重写导致返回了netty的 `Future` 实现类，而这个实现类正是 `PromiseTask` 。

```java
@Override
public <T> Future<T> submit(Callable<T> task) {
    return (Future<T>) super.submit(task);
}

@Override
protected final <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
    return new PromiseTask<T>(this, callable);
}
```

`PromiseTask` 的实现很简单，它缓存了要执行的 `Callable` 任务，并在run方法中完成了任务调用和Listener的通知。

```java
@Override
public void run() {
    try {
        if (setUncancellableInternal()) {
            V result = task.call();
            setSuccessInternal(result);
        }
    } catch (Throwable e) {
        setFailureInternal(e);
    }
}

@Override
public Promise<V> setSuccess(V result) {
    if (setSuccess0(result)) {
        notifyListeners();
        return this;
    }
    throw new IllegalStateException("complete already: " + this);
}

@Override
public Promise<V> setFailure(Throwable cause) {
    if (setFailure0(cause)) {
        notifyListeners();
        return this;
    }
    throw new IllegalStateException("complete already: " + this, cause);
}
```

任务调用成功或者失败都会调用 `notifyListeners` 来通知Listener，所以大家得在回调的函数里调用 `isSuccess` 方法来检查状态。

这里有一个疑惑，会不会 `Future` 在调用 `addListener` 方法的时候任务已经执行完成了，这样子会不会通知就会失败了啊？

```java
@Override
public Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
    synchronized (this) {
        addListener0(listener);
    }

    if (isDone()) {
        notifyListeners();
    }

    return this;
}
```

可以发现，在Listener添加成功之后，会立即检查状态，如果任务已经完成立刻进行回调，所以这里不用担心啦。

# Guava-Future

首先引入guava的Maven依赖：

```java
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>22.0</version>
</dependency>
public class GuavaFutureDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("begin:" + DateUtils.getNow());
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        ListeningExecutorService service = MoreExecutors.listeningDecorator(executorService);
        ListenableFuture<Integer> future = service.submit(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                System.out.println("开始耗时计算:" + DateUtils.getNow());
                Thread.sleep(3000);
                System.out.println("结束耗时计算:" + DateUtils.getNow());
                return 100;
            }
        });
        future.addListener(new Runnable() {
            @Override
            public void run() {
                System.out.println("调用成功");
            }
        }, executorService);
        System.out.println("end:" + DateUtils.getNow());
        new CountDownLatch(1).await();
    }
}
```

`ListenableFuture` 可以通过 `addListener` 方法增加回调函数，一般用于不在乎执行结果的地方。如果需要在执行成功时获取结果或者执行失败时获取异常信息，需要用到 `Futures` 工具类的 `addCallback` 方法：

```java
Futures.addCallback(future, new FutureCallback<Integer>() {
    @Override
    public void onSuccess(@Nullable Integer result) {
        System.out.println("调用成功，计算结果:" + result);
    }

    @Override
    public void onFailure(Throwable t) {
        System.out.println("调用失败");
    }
}, executorService);
```

前面提到除了 `ListenableFuture` 外，还有一个 `SettableFuture` 类也支持回调能力。它实现自 `ListenableFuture` ，所以拥有 `ListenableFuture` 的所有能力。

```java
public class GuavaFutureDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("begin:" + DateUtils.getNow());
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        ListenableFuture<Integer> future = submit(executorService);
        Futures.addCallback(future, new FutureCallback<Integer>() {
            @Override
            public void onSuccess(@Nullable Integer result) {
                System.out.println("调用成功，计算结果:" + result);
            }

            @Override
            public void onFailure(Throwable t) {
                System.out.println("调用失败:" + t.getMessage());
            }
        }, executorService);
        Thread.sleep(1000);
        System.out.println("end:" + DateUtils.getNow());
        new CountDownLatch(1).await();
    }

    private static ListenableFuture<Integer> submit(Executor executor) {
        SettableFuture<Integer> future = SettableFuture.create();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println("开始耗时计算:" + DateUtils.getNow());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("结束耗时计算:" + DateUtils.getNow());
                // 设置返回值
                future.set(100);
                // 设置异常信息
//                future.setException(new RuntimeException("custom error!"));
            }
        });
        return future;
    }
}
```

看起来用法上没有太多差别，但是有一个很容易被忽略的重要问题。**当 `SettableFuture` 的这种方式最后调用了 `cancel` 方法后，线程池中的任务还是会继续执行，而通过 `submit` 方法返回的 `ListenableFuture` 方法则会立即取消执行，这点尤其要注意。**

## 一探源码

和Netty的Future一样，Guava也是通过实现了自定义的 `ExecutorService` 实现类 `ListeningExecutorService` 来重写了 `submit` 方法。

```java
public interface ListeningExecutorService extends ExecutorService {
  <T> ListenableFuture<T> submit(Callable<T> task);
  ListenableFuture<?> submit(Runnable task);
  <T> ListenableFuture<T> submit(Runnable task, T result);
}
```

同样的，`newTaskFor` 方法也被进行了重写，返回了自定义的Future类：`TrustedListenableFutureTask`

```java
@Override
protected final <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
    return TrustedListenableFutureTask.create(runnable, value);
}

@Override
protected final <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
    return TrustedListenableFutureTask.create(callable);
}
```

任务调用会走 `TrustedFutureInterruptibleTask` 的run方法：

```java
@Override
public void run() {
    TrustedFutureInterruptibleTask localTask = task;
    if (localTask != null) {
        localTask.run();
    }
}

@Override
public final void run() {
    if (!ATOMIC_HELPER.compareAndSetRunner(this, null, Thread.currentThread())) {
        return; // someone else has run or is running.
    }
    try {
        // 抽象方法，子类进行重写
        runInterruptibly();
    } finally {
        if (wasInterrupted()) {
            while (!doneInterrupting) {
                Thread.yield();
            }
        }
    }
}
```

最终还是调用到 `TrustedFutureInterruptibleTask` 的 `runInterruptibly` 方法，等待任务完成后调用 `set` 方法。

```java
@Override
void runInterruptibly() {
    if (!isDone()) {
        try {
            set(callable.call());
        } catch (Throwable t) {
            setException(t);
        }
    }
}

protected boolean set(@Nullable V value) {
    Object valueToSet = value == null ? NULL : value;
    // CAS设置值
    if (ATOMIC_HELPER.casValue(this, null, valueToSet)) {
        complete(this);
        return true;
    }
    return false;
}
```

在 `complete` 方法的最后会获取到Listener进行回调。

上面提到的 `SettableFuture` 和 `ListenableFuture` 的 `cancel` 方法效果不同，原因在于一个重写了 `afterDone` 方法而一个没有。

下面是 `ListenableFuture` 的 `afterDone` 方法：

```java
@Override
protected void afterDone() {
    super.afterDone();

    if (wasInterrupted()) {
        TrustedFutureInterruptibleTask localTask = task;
        if (localTask != null) {
            localTask.interruptTask();
        }
    }

    this.task = null;
}
```

`wasInterrupted` 用来判断是否调用了 `cancel` （cancel方法会设置一个取消对象Cancellation到value中）

```java
protected final boolean wasInterrupted() {
    final Object localValue = value;
    return (localValue instanceof Cancellation) && ((Cancellation) localValue).wasInterrupted;
}
```

`interruptTask` 方法通过线程的 `interrupt` 方法真正取消线程任务的执行：

```java
final void interruptTask() {
    Thread currentRunner = runner;
    if (currentRunner != null) {
        currentRunner.interrupt();
    }
    doneInterrupting = true;
}
```

# CompletableFuture

最后我们来说说Java8提供的一种更为高级的回调方式：`CompletableFuture` 。

```java
public class CompletableFutureTest {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("begin:" + DateUtils.getNow());
        CompletableFuture<Integer> completableFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("开始耗时计算:" + DateUtils.getNow());
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("结束耗时计算:" + DateUtils.getNow());
            return 100;
        });
        completableFuture.whenComplete((result, e) -> {
            System.out.println("回调结果:" + result);
        });
        System.out.println("end:" + DateUtils.getNow());
        new CountDownLatch(1).await();
    }
}
```

# 总结

由此看来，为Future模式增加回调功能是非常有必要的。它不需要阻塞等待结果的返回并且不需要消耗无谓的CPU资源去轮询处理状态，JDK8之前使用Netty或者Guava提供的工具类，JDK8之后则可以使用自带的 `CompletableFuture` 类。

快给你用到 `Future` 的地方增加Callback吧~