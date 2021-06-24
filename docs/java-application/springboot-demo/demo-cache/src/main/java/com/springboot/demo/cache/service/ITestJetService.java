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
