### 定时任务的作用
在项目开发是难免会要用到定时任务，它主要用来处理一些数据统计、分析、日志处理等时效性不是很高的业务。

### 定时任务的选择
定时任务的框架也比较多，单机版的，分布式的各种各样，正如点进来的标题所说，今天介绍的是如何使用spring内置的简单定时任务。

### 按照步骤一步步来
1. 加上支持计划任务的注解（不需要额外引入依赖）
```java
@SpringBootApplication
@EnableScheduling()
public class DemoScheduledApplication {

    public static void main(String[] args) {
        SpringApplication.run(StartApplication.class, args);
    }

}
```
2. 创建任务类，加上方法注解运行定时任务
```java

```


```java
package org.springframework.scheduling.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Dave Syer
 * @author Chris Beams
 * @since 3.0
 * @see EnableScheduling
 * @see ScheduledAnnotationBeanPostProcessor
 * @see Schedules
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(Schedules.class)
public @interface Scheduled {

	/**
	 * A special cron expression value that indicates a disabled trigger: {@value}.
	 * <p>This is primarily meant for use with ${...} placeholders, allowing for
	 * external disabling of corresponding scheduled methods.
	 * @since 5.1
	 */
	String CRON_DISABLED = "-";

	/**
	 * 定时表达式
	 */
	String cron() default "";

	/**
	 * 指定时区
	 */
	String zone() default "";

	/**
	 * fixedDelay配置了上一次任务的结束时间到下一次任务的开始时间的间隔，每次任务都会执行，它的间隔时间是根据上次的任务结束的时候开始计时的。比如一个方法上设置了fixedDelay=5*1000，那么当该方法某一次执行结束后，开始计算时间，当时间达到5秒，就开始再次执行该方法。
	 */
	long fixedDelay() default -1;

	/**
	 * fixedDelay的字符串传值
	 */
	String fixedDelayString() default "";

	/**
	 * fixedRate配置了上一次任务的开始时间到下一次任务的开始时间的间隔，每次任务都会执行，它的间隔时间是根据上次任务开始的时候计时的。比如当方法上设置了fiexdRate=5*1000，该执行该方法所花的时间是2秒，那么3秒后就会再次执行该方法。
但是这里有个坑，当任务执行时长超过设置的间隔时长，那会是什么结果呢。打个比方，比如一个任务本来只需要花2秒就能执行完成，我所设置的fixedRate=5*1000，但是因为网络问题导致这个任务花了7秒才执行完成。当任务开始时Spring就会给这个任务计时，5秒钟时候Spring就会再次调用这个任务，可是发现原来的任务还在执行，这个时候第二个任务就阻塞了（这里只考虑单线程的情况下，多线程后面再讲），甚至如果第一个任务花费的时间过长，还可能会使第三第四个任务被阻塞。被阻塞的任务就像排队的人一样，一旦前一个任务没了，它就立马执行。
	 */
	long fixedRate() default -1;

	/**
	 * fixedRate的字符串传值
	 */
	String fixedRateString() default "";

	/**
	 * fixedDelay的起始执行时间，例如5秒后再按照fixedDelay每个n秒执行任务
	 */
	long initialDelay() default -1;

	/**
	 * initialDelay的字符串传值
	 */
	String initialDelayString() default "";
}
```

### 常用表达式例子
```txt
　　0 0 2 1 * ? *   表示在每月的1日的凌晨2点调整任务
　　0 15 10 ? * MON-FRI   表示周一到周五每天上午10:15执行作业
　　0 15 10 ? 6L 2002-2006   表示2002-2006年的每个月的最后一个星期五上午10:15执行作
　　0 0 10,14,16 * * ?   每天上午10点，下午2点，4点 
　　0 0/30 9-17 * * ?   朝九晚五工作时间内每半小时 
　　0 0 12 ? * WED    表示每个星期三中午12点 
　　0 0 12 * * ?   每天中午12点触发 
　　0 15 10 ? * *    每天上午10:15触发 
　　0 15 10 * * ?     每天上午10:15触发 
　　0 15 10 * * ? *    每天上午10:15触发 
　　0 15 10 * * ? 2005    2005年的每天上午10:15触发 
　　0 * 14 * * ?     在每天下午2点到下午2:59期间的每1分钟触发 
　　0 0/5 14 * * ?    在每天下午2点到下午2:55期间的每5分钟触发 
　　0 0/5 14,18 * * ?     在每天下午2点到2:55期间和下午6点到6:55期间的每5分钟触发 
　　0 0-5 14 * * ?    在每天下午2点到下午2:05期间的每1分钟触发 
　　0 10,44 14 ? 3 WED    每年三月的星期三的下午2:10和2:44触发 
　　0 15 10 ? * MON-FRI    周一至周五的上午10:15触发 
　　0 15 10 15 * ?    每月15日上午10:15触发 
　　0 15 10 L * ?    每月最后一日的上午10:15触发 
　　0 15 10 ? * 6L    每月的最后一个星期五上午10:15触发 
　　0 15 10 ? * 6L 2002-2005   2002年至2005年的每月的最后一个星期五上午10:15触发 
　　0 15 10 ? * 6#3   每月的第三个星期五上午10:15触发
```

### 总结和提示
1、fixedRate配置了上一次任务的开始时间到下一次任务的开始时间的间隔，每次任务都会执行；

2、fixedDelay配置了上一次任务的结束时间到下一次任务的开始时间的间隔，每次任务都会执行；

3、cron表达式配置了在哪一刻执行任务，会在配置的任务开始时间判断任务是否可以执行，如果能则执行，不能则会跳过本次执行；

4、如果是强调任务间隔的定时任务，建议使用fixedDelay，如果是强调任务在某时某分某刻执行的定时任务，建议使用cron表达式。

5、不建议使用fixedRate，当中间发生了一长时间的任务后fixedRate的任务间的等待都被抹除掉

6、注意同步或者异步执行任务，通常用异步，但此时注意线程池的配置。

### 进阶
[SpringBoot几种定时任务的实现方式](http://www.wanqhblog.top/2018/02/01/SpringBootTaskSchedule/)

[理解Spring定时任务@Scheduled的两个属性fixedRate和fixedDelay](https://blog.csdn.net/czx2018/article/details/83501945)

[Spring定时任务@Scheduled注解使用方式浅窥（cron表达式、fixedRate和fixedDelay）](https://segmentfault.com/a/1190000015253688)

[理解Spring定时任务的fixedRate和fixedDelay](https://www.cnblogs.com/javahr/p/8318642.html)

[cron表达式详解](https://www.cnblogs.com/javahr/p/8318728.html)

[在线cron表达式生成](http://cron.qqe2.com/)

下一篇：[定时任务的表达式可动态配置](./dynamic-config.md)