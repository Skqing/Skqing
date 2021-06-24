package com.springboot.demo.lock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 参考
 * http://www.itmuch.com/spring-boot/global-lock/
 * @author skqing
 */
@SpringBootApplication
public class DemoLockApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoLockApplication.class, args);
    }

}
