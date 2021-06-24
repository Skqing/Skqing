## 通过读Seata的源码学到了不少的好东西

源码地址：[https://github.com/seata/seata](https://github.com/seata/seata)

1. 原子判断变量客户端是否已经初始化

   ```java
   public class GlobalTransactionScanner extends AbstractAutoProxyCreator
       implements ConfigurationChangeListener, InitializingBean, ApplicationContextAware, DisposableBean {
   
       private final AtomicBoolean initialized = new AtomicBoolean(false);
   
       @Override
       public void afterPropertiesSet() {
           if (disableGlobalTransaction) {
               if (LOGGER.isInfoEnabled()) {
                   LOGGER.info("Global transaction is disabled.");
               }
               ConfigurationCache.addConfigListener(ConfigurationKeys.DISABLE_GLOBAL_TRANSACTION,
                       (ConfigurationChangeListener)this);
               return;
           }
           //这里的作用就是判断是否已经初始化，如果未初始化则设置变量值为true并开始执行初始化
           //比较并赋值，这连个动作之间不会被打断，任何内部或者外部的语句都不可能在两个动作之间运行，为多线程的控制提供了解决的方案。
           //initialized.getAndSet(false); 更多内容参考：https://blog.csdn.net/zhushuai1221/article/details/94313313
           if (initialized.compareAndSet(false, true)) {
               initClient();
           }
       }
   }
   ```

2. 单例

   ```java
   public final class TmNettyRemotingClient extends AbstractNettyRemotingClient {
   
       //使用volatile修饰，更多参考：https://www.cnblogs.com/zhengbin/p/5654805.html
       private static volatile TmNettyRemotingClient instance;
   
       //私有构造方法
       private TmNettyRemotingClient(NettyClientConfig nettyClientConfig,
                                     EventExecutorGroup eventExecutorGroup,
                                     ThreadPoolExecutor messageExecutor) {
           super(nettyClientConfig, eventExecutorGroup, messageExecutor, NettyPoolKey.TransactionRole.TMROLE);
           this.signer = EnhancedServiceLoader.load(AuthSigner.class);
       }
   
       public static TmNettyRemotingClient getInstance(String applicationId, String transactionServiceGroup, String accessKey, String secretKey) {
           TmNettyRemotingClient tmRpcClient = getInstance();
           tmRpcClient.setApplicationId(applicationId);
           tmRpcClient.setTransactionServiceGroup(transactionServiceGroup);
           tmRpcClient.setAccessKey(accessKey);
           tmRpcClient.setSecretKey(secretKey);
           return tmRpcClient;
       }
   
       public static TmNettyRemotingClient getInstance() {
           if (instance == null) {
               //使用同步代码块
               synchronized (TmNettyRemotingClient.class) {
                   //二次空判断
                   if (instance == null) {
                       NettyClientConfig nettyClientConfig = new NettyClientConfig();
                       final ThreadPoolExecutor messageExecutor = new ThreadPoolExecutor(
                               nettyClientConfig.getClientWorkerThreads(), nettyClientConfig.getClientWorkerThreads(),
                               KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                               new LinkedBlockingQueue<>(MAX_QUEUE_SIZE),
                               new NamedThreadFactory(nettyClientConfig.getTmDispatchThreadPrefix(),
                                       nettyClientConfig.getClientWorkerThreads()),
                               RejectedPolicies.runsOldestTaskPolicy());
                       instance = new TmNettyRemotingClient(nettyClientConfig, null, messageExecutor);
                   }
               }
           }
           return instance;
       }
   }
   ```

3. computeIfAbsend的疑惑？

   ```java
   public class EnhancedServiceLoader {
   
       private static class InnerEnhancedServiceLoader<S> {
           /**
            * Get the ServiceLoader for the specified Class
            *
            * @param type the type of the extension point
            * @param <S>  the type
            * @return the service loader
            */
           private static <S> InnerEnhancedServiceLoader<S> getServiceLoader(Class<S> type) {
               if (type == null) {
                   throw new IllegalArgumentException("Enhanced Service type == null");
               }
               //这句话按照Java8之后的写法可以直接写成下面这种方式
               //SERVICE_LOADERS.computeIfAbsent(type, k -> new InnerEnhancedServiceLoader<>(type));
               //暂时不理解为何又封装了一个CollectionUtils.computeIfAbsent()这个方法
               return (InnerEnhancedServiceLoader<S>)CollectionUtils.computeIfAbsent(SERVICE_LOADERS, type,
                   key -> new InnerEnhancedServiceLoader<>(type));
           }
   
   }
   ```

CollectionUtils中的方法:

```java
/**
 * Compute if absent.
 * Use this method if you are frequently using the same key,
 * because the get method has no lock.
 *
 * @param map             the map
 * @param key             the key
 * @param mappingFunction the mapping function
 * @param <K>             the type of key
 * @param <V>             the type of value
 * @return the value
 */
public static <K, V> V computeIfAbsent(Map<K, V> map, K key, Function<? super K, ? extends V> mappingFunction) {
    V value = map.get(key);
    if (value != null) {
        return value;
    }
    return map.computeIfAbsent(key, mappingFunction);
}
```

4. Spi扩展机制

```java
public class DefaultResourceManager implements ResourceManager {

    //这里用到了Java Spi扩展机制，可插拔，后面再做深入研究
    protected void initResourceManagers() {
        //init all resource managers
        List<ResourceManager> allResourceManagers = EnhancedServiceLoader.loadAll(ResourceManager.class);
        if (CollectionUtils.isNotEmpty(allResourceManagers)) {
            for (ResourceManager rm : allResourceManagers) {
                resourceManagers.put(rm.getBranchType(), rm);
            }
        }
    }

}
```

5. 全局事务拦截器

   ```java
   public class GlobalTransactionalInterceptor implements ConfigurationChangeListener, MethodInterceptor {
   
       //这里的反射写法可以参考
       @Override
       public Object invoke(final MethodInvocation methodInvocation) throws Throwable {
           Class<?> targetClass =
               methodInvocation.getThis() != null ? AopUtils.getTargetClass(methodInvocation.getThis()) : null;
           Method specificMethod = ClassUtils.getMostSpecificMethod(methodInvocation.getMethod(), targetClass);
           if (specificMethod != null && !specificMethod.getDeclaringClass().equals(Object.class)) {
               final Method method = BridgeMethodResolver.findBridgedMethod(specificMethod);
               final GlobalTransactional globalTransactionalAnnotation =
                   getAnnotation(method, targetClass, GlobalTransactional.class);
               final GlobalLock globalLockAnnotation = getAnnotation(method, targetClass, GlobalLock.class);
               boolean localDisable = disable || (degradeCheck && degradeNum >= degradeCheckAllowTimes);
               if (!localDisable) {
                   if (globalTransactionalAnnotation != null) {
                       return handleGlobalTransaction(methodInvocation, globalTransactionalAnnotation);
                   } else if (globalLockAnnotation != null) {
                       return handleGlobalLock(methodInvocation, globalLockAnnotation);
                   }
               }
           }
           return methodInvocation.proceed();
       }
   }
   ```

6. 几种单例写法

内部类写法：

   ```java
   public class DataSourceProxyHolder {
       private DataSourceProxyHolder() {
       }
   
       /**
        * the type holder
        */
       private static class Holder {
           private static final DataSourceProxyHolder INSTANCE;
   
           static {
               INSTANCE = new DataSourceProxyHolder();
           }
       }
   
       /**
        * Get DataSourceProxyHolder instance
        *
        * @return the INSTANCE of DataSourceProxyHolder
        */
       public static DataSourceProxyHolder get() {
           return Holder.INSTANCE;
       }
   }
   ```

同步代码块写法：

```java
public final class TmNettyRemotingClient extends AbstractNettyRemotingClient {

    private TmNettyRemotingClient(NettyClientConfig nettyClientConfig,
                                  EventExecutorGroup eventExecutorGroup,
                                  ThreadPoolExecutor messageExecutor) {
        super(nettyClientConfig, eventExecutorGroup, messageExecutor, NettyPoolKey.TransactionRole.TMROLE);
        this.signer = EnhancedServiceLoader.load(AuthSigner.class);
    }

    /**
     * Gets instance.
     * @return the instance
     */
    public static TmNettyRemotingClient getInstance() {
        if (instance == null) {
            synchronized (TmNettyRemotingClient.class) {
                if (instance == null) {
                    NettyClientConfig nettyClientConfig = new NettyClientConfig();
                    final ThreadPoolExecutor messageExecutor = new ThreadPoolExecutor(
                            nettyClientConfig.getClientWorkerThreads(), nettyClientConfig.getClientWorkerThreads(),
                            KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(MAX_QUEUE_SIZE),
                            new NamedThreadFactory(nettyClientConfig.getTmDispatchThreadPrefix(),
                                    nettyClientConfig.getClientWorkerThreads()),
                            RejectedPolicies.runsOldestTaskPolicy());
                    instance = new TmNettyRemotingClient(nettyClientConfig, null, messageExecutor);
                }
            }
        }
        return instance;
    }
}
```

最简单的写法：

```java
public class ShutdownHook extends Thread {
    private static final ShutdownHook SHUTDOWN_HOOK = new ShutdownHook("ShutdownHook");

    private ShutdownHook(String name) {
        super(name);
    }

    public static ShutdownHook getInstance() {
        return SHUTDOWN_HOOK;
    }
}
```