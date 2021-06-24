package com.springboot.demo.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * @author Sandy
 * @version 1.0.0
 * @time 2019/4/10 9:41
 * @desc
 */

@Slf4j
@Controller
public class TestController {

    @Resource
    private RedisLockRegistry redisLockRegistry;

    @GetMapping("test")
    public void test() throws InterruptedException {
        Lock lock = redisLockRegistry.obtain("lock");
        boolean b1 = lock.tryLock(3, TimeUnit.SECONDS);
        log.info("b1 is : {}", b1);

        TimeUnit.SECONDS.sleep(5);

        boolean b2 = lock.tryLock(3, TimeUnit.SECONDS);
        log.info("b2 is : {}", b2);

        lock.unlock();
        lock.unlock();
    }
}
