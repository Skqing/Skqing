### 为什么需要缓存
知道内存和硬盘是什么吧，听说过CPU一级缓存二级缓存吗？缓存本质上是解决硬件性能差异性问题的。比如硬盘存储量大但速度慢，CPU速度快如果一直等着去读取硬盘的内容肯定会塞车，因此中间需要一层缓存，先把数据放到内存第二次读取的时候直接从内存读取即可，因为内存的速度比较快。当然了实际上内存的速度还是跟不上CPU的速度，所以CPU内部也分了一级二级缓存，所以缓存本质上是为了解决硬件性能差异问题的。

同样，网站的缓存也是为了解决访问量大，但后台数据库反应慢的问题，使用缓存避免把数据库搞垮，并且提高网站反应速度，毕竟缓存一般是在内存中存取数据，而数据库通常是在硬盘中存取数据。

### 缓存的诸子百家
首先缓存分为本地缓存和远程缓存和二级缓存（既支持本地缓存也支持远程缓存），本地缓存是只在应用容器内把数据缓存起来，远程缓存是把数据放到第三方服务中，例如Redis。本地缓存一般用于单机应用，分布式应用必须要用远程缓存，不然应用缓存不同步，跟网站的session共享是一个意思。
目前比较常见的本地缓存有：EhCache、JCache等
远程缓存框架有（现在大部分远程缓存都是二级缓存）：springboot cache、[JetCache](https://github.com/alibaba/jetcache/wiki/Home_CN)，[J2Cache](https://gitee.com/ld/J2Cache)等
远程缓存服务有：Redis、Memcached、MongoDB

### 背景和特性
对于一些cache框架或产品，我们可以发现一些明显不足。

Spring cache：无法满足本地缓存和远程缓存同时使用，使用远程缓存时无法自动刷新

Guava cache：内存型缓存，占用内存，无法做分布式缓存

redis/memcache：分布式缓存，缓存失效时，会导致数据库雪崩效应

Ehcache：内存型缓存，可以通过RMI做到全局分布缓存，效果差

基于以上的一些不足，大杀器缓存框架JetCache出现，基于已有缓存的成熟产品，解决了上面产品的缺陷。主要表现在

（1）分布式缓存和内存型缓存可以共存，当共存时，优先访问内存，保护远程缓存；也可以只用某一种，分布式 or 内存

（2）自动刷新策略，防止某个缓存失效，访问量突然增大时，所有机器都去访问数据库，可能导致数据库挂掉

（3）利用不严格的分布式锁，对同一key，全局只有一台机器自动刷新


### 使用springboot  cache
1. 添加依赖
```xml
<!--提供注解方式缓存-->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<!--Redis提供远程缓存服务-->
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

2. 添加配置
```yml
spring:
  redis:
    host: 127.0.0.1
    port: 6379
    jedis:
      pool:
        max-active: 50
        # 连接池最大阻塞等待时间,使用负值表示无限制。
        max-wait: -1s
        # 连接池最大空闲数,使用负值表示无限制。
        max-idle: 50
        # 连接池最小空闲连接，只有设置为正值时候才有效
        min-idle: 1
    timeout: 300ms
```

3. 开启注解
```java
@SpringBootApplication
//开启缓存，主要用于注解缓存
@EnableCaching
public class DemoCacheApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoCacheApplication.class, args);
    }

}
```
3. 服务层使用注解
```java
package com.springboot.demo.cache.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * @author Sandy
 * @version 1.0.0
 * @time 2019/4/10 9:41
 * @desc
 */

@Service
//公共配置  可以在类上注释 注释本类的 缓存相关公共配置
//@CacheConfig(cacheNames = TestCacheService.CACHE_KEY)
public class TestServiceImpl implements ITestService {

    public static final String CACHE_KEY = "test-cache";

    /**
     * 获取信息  第二次访问会取缓存
     *
     * @author ming
     * @date 2018-07-11 17:41:47
     */
    @Cacheable(cacheNames = CACHE_KEY)
    public String testCache(String id) {
        return getString(id);
    }


    /**
     * 更新信息   更新缓存
     *
     * @author ming
     * @date 2018-07-12 09:50:53
     */
    @CachePut(cacheNames = CACHE_KEY)
    public String testCachePut(String id) {
        return getString(id + "update");
    }

    /**
     * 清除缓存
     *
     * @author ming
     * @date 2018-07-12 09:51:22
     */
    @CacheEvict(cacheNames = CACHE_KEY)
    public void removeCache(String id) {
        System.out.println("删除缓存 ");
    }


    /**
     * 获取string 模拟调用方法
     *
     * @author ming
     * @date 2018-07-11 17:41:58
     */
    private String getString(String id) {
        try {
            Thread.sleep(3000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return id + "load";
    }
}
```
就是这么简单就可以使用缓存了

### 让springboot cache支持分布式session
1. 增加依赖
```xml
<dependency>
	<groupId>org.springframework.session</groupId>
	<artifactId>spring-session-data-redis</artifactId>
</dependency>
```

2. 简单修改一下配置即可
```yml
spring:
  redis:
    host: 127.0.0.1
    port: 6379
    jedis:
      pool:
        max-active: 50
        # 连接池最大阻塞等待时间,使用负值表示无限制。
        max-wait: -1s
        # 连接池最大空闲数,使用负值表示无限制。
        max-idle: 50
        # 连接池最小空闲连接，只有设置为正值时候才有效
        min-idle: 1
    timeout: 300ms
  session:
    # session 存储方式 支持redis、mongo、jdbc、hazelcast
    store-type: redis
```

3. 如何验证
```java
package com.springboot.demo.cache;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.CreateCache;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;
import java.util.concurrent.TimeUnit;

/**
 * @author Sandy
 * @version 1.0.0
 * @time 2019/4/10 9:41
 * @desc
 */

@Controller
public class TestController {

    @RequestMapping(value = "/login")
    public @ResponseBody String login(HttpSession session) {
        session.setAttribute("name", "lao wang");

        return "session ok";
    }

    @RequestMapping(value = "/getUser")
    public @ResponseBody Object getUser(HttpSession session) {
        return session.getAttribute("name");
    }
}

```
修改store-type的值为NONE或者REDIS进行下面的测试
1. 启动服务
2. 请求http://localhost:8080/login
3. 然后请求http://localhost:8080/getUser有数据返回
4. 重启服务重新请求http://localhost:8080/getUser看是否有数据请求
如果session存储在本地，应用重启后内存中session丢失第二次请求不会有数据，如果session在Redis中，第二次请求仍然会有数据，前提是不要关闭浏览器。

### 使用JetCache
[JetCache](https://github.com/alibaba/jetcache/wiki/Home_CN)是一个基于Java的缓存系统封装，提供统一的API和注解来简化缓存的使用。 JetCache提供了比SpringCache更加强大的注解，可以原生的支持TTL、两级缓存、分布式自动刷新，还提供了Cache接口用于手工缓存操作。 当前有四个实现，RedisCache、TairCache（此部分未在github开源）、CaffeineCache(in memory)和一个简易的LinkedHashMapCache(in memory)，要添加新的实现也是非常简单的。

1. 添加依赖
```xml
<dependency>
	<groupId>com.alicp.jetcache</groupId>
	<artifactId>jetcache-starter-redis-lettuce</artifactId>
	<version>2.5.11</version>
</dependency>
```

2. 服务接口注解方式使用
```java
package com.springboot.demo.cache.service;

import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheUpdate;
import com.alicp.jetcache.anno.Cached;
import com.springboot.demo.cache.bean.User;

/**
 * @author Sandy
 * @version 1.0.0
 * @time 2019/4/10 9:41
 * @desc
 */
public interface ITestJetService {

    @Cached(name="userCache.", key="#userId", expire = 3600)
    User getUserById(long userId);

    @CacheUpdate(name="userCache.", key="#user.userId", value="#user")
    void updateUser(User user);

    @CacheInvalidate(name="userCache.", key="#userId")
    void deleteUser(long userId);
}

```

3. API方式使用
```java
package com.springboot.demo.cache;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.CreateCache;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;
import java.util.concurrent.TimeUnit;

/**
 * @author Sandy
 * @version 1.0.0
 * @time 2019/4/10 9:41
 * @desc
 */

@Controller
public class TestController {

    @CreateCache(name = "session_test", expire = 180, timeUnit = TimeUnit.SECONDS, cacheType = CacheType.REMOTE)
    private Cache<String, String> sessionCache;


    @RequestMapping(value = "/jetCache")
    public @ResponseBody String jetCache(@RequestParam("test") String test) {
        sessionCache.put("testkey", test);

        return sessionCache.get("testkey");
    }
}

```

### session超时时间设置
(暂未解决)

### 参考
[JetCache官方文档](https://github.com/alibaba/jetcache/wiki/Home_CN)

[史上最全面的Spring Boot Cache使用与整合](https://www.cnblogs.com/yueshutong/p/9381540.html)