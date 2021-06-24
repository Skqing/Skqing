package com.springboot.demo.cache.service;

/**
 * @author Sandy
 * @version 1.0.0
 * @time 2019/4/10 9:41
 * @desc
 */
public interface ITestService {
    String testCache(String id);
    String testCachePut(String id);
    void removeCache(String id);

}
