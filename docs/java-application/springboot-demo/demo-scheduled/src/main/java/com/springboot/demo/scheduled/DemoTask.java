package com.springboot.demo.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author Sandy
 * @version 1.0.0
 * @time 2019/4/9 15:02
 * @desc
 */

@Slf4j
@Component
public class DemoTask {

    @Scheduled(cron = "0/5 * * * * *")
    public void scheduled(){
        log.info("=====>>>>>使用cron  {}",System.currentTimeMillis());
    }
    @Scheduled(fixedRate = 5000)
    public void scheduled1() {
        log.info("=====>>>>>使用fixedRate{}", System.currentTimeMillis());
    }
    @Scheduled(fixedDelay = 5000)
    public void scheduled2() {
        log.info("=====>>>>>fixedDelay{}",System.currentTimeMillis());
    }

}
