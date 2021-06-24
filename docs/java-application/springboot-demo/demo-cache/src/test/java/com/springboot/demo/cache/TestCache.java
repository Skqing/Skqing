package com.springboot.demo.cache;

import com.springboot.demo.cache.service.ITestService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Sandy
 * @version 1.0.0
 * @time 2019/4/10 11:32
 * @desc
 */

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = DemoCacheApplication.class)
public class TestCache {

    @Autowired
    private ITestService testService;

    @Test
    public void test() {
        String id = "ming";
        System.out.println("第一次访问没有缓存--------");
        long oneNow = System.currentTimeMillis();
        System.out.println(testService.testCache(id));
        System.out.println("耗时:" + (System.currentTimeMillis() - oneNow) + "ms");


        System.out.println("第二次访问有缓存--------");
        long twoNow = System.currentTimeMillis();
        System.out.println(testService.testCache(id));
        System.out.println("耗时:" + (System.currentTimeMillis() - twoNow) + "ms");


        System.out.println("更新缓存信息--------");
        long threeNow = System.currentTimeMillis();
        System.out.println(testService.testCachePut(id));
        System.out.println("耗时:" + (System.currentTimeMillis() - threeNow) + "ms");


        System.out.println("获取更新后的缓存信息-------");
        long fourNow = System.currentTimeMillis();
        System.out.println(testService.testCache(id));
        System.out.println("耗时:" + (System.currentTimeMillis() - fourNow) + "ms");


        System.out.println("移除缓存------并且调用testCache方法");
        testService.removeCache(id);
        long fiveNow = System.currentTimeMillis();
        System.out.println(testService.testCache(id));
        System.out.println("耗时:" + (System.currentTimeMillis() - fiveNow) + "ms");
    }

}
