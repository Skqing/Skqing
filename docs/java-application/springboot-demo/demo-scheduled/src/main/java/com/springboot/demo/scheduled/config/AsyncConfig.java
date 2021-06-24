package com.springboot.demo.scheduled.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * @author Skqing
 * @version 1.0.0
 * @time 2019/4/5 23:34
 * @desc 配置计划任务多线程参数
 * 参考：http://www.wanqhblog.top/2018/02/01/SpringBootTaskSchedule/
 */

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 此处成员变量应该使用@Value从配置中读取
     */
    private int corePoolSize = 10;
    private int maxPoolSize = 200;
    private int queueCapacity = 10;
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.initialize();
        return executor;
    }

}
