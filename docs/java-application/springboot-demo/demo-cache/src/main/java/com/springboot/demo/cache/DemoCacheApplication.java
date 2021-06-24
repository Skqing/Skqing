package com.springboot.demo.cache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * @author skqing
 */
@SpringBootApplication
//开启缓存，主要用于注解缓存
@EnableCaching
public class DemoCacheApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoCacheApplication.class, args);
    }

}
