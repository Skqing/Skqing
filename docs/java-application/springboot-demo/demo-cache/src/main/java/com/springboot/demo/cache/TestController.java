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
