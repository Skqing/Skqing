### Spring的两种代理方式

AOP是Spring的重要组成部分，而AOP正是通过代理实现的。如果代理对象实现了接口，则默认使用jdk动态代理，也可强制使用cglib代理，如果未实现接口则只能使用cglib代理。
 应用：对一个操作前后事务的开启和提交/回滚

#### 静态代理

```java
public interface Fly {
    void fly();
}
```



```java
public class Bird implements Fly{

    @Override
    public void fly() {
        // TODO Auto-generated method stub
        System.out.println("飞啊飞~");
    }
}
```



```java
public class ProxyBird implements Fly{

    //维护一个被代理对象
    Fly bird;
    
    public Fly getBird() {
        return bird;
    }

    public void setBird(Fly bird) {
        this.bird = bird;
    }

    @Override
    public void fly() {
        // TODO Auto-generated method stub
        System.out.println("我代理增强一下你再飞~");
        bird.fly();
    }

}
```



```cpp
public class TestBird {
    public static void main(String[] args) {
        Fly bird=new Bird();
        ProxyBird proxyBird=new ProxyBird();
        proxyBird.setBird(bird);
        proxyBird.fly();
    }
}
```

![img](https:////upload-images.jianshu.io/upload_images/2465279-8e5a843687a615dd.png?imageMogr2/auto-orient/strip|imageView2/2/w/323/format/webp)

#### JDK动态代理

```java
public interface UserDao {
    void addUser();
}
```



```java
public class UserDaoImpl implements UserDao{

    @Override
    public void addUser() {
        // TODO Auto-generated method stub
        System.out.println("添加用户~");
    }

}
```



```java
public class TestJDK {
    public static void main(String[] args) {
        UserDao userDao=new UserDaoImpl();
        UserDao proxy=(UserDao)Proxy.newProxyInstance(userDao.getClass().getClassLoader(), userDao.getClass().getInterfaces(), new InvocationHandler() {
            
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                // TODO Auto-generated method stub
                System.out.println("invoke方法前做一些代理");
                method.invoke(userDao, args);
                System.out.println("invoke方法后做一些代理");
                return proxy;
            }
        });
        proxy.addUser();
    }
}
```

![img](https:////upload-images.jianshu.io/upload_images/2465279-ffa65f11e078e0aa.png?imageMogr2/auto-orient/strip|imageView2/2/w/247/format/webp)

#### CGLIB代理

```java
public class UserService {
    
    public void addUser(){
        System.out.println("我不需要接口就可以被代理哦~");
    }
}
```



```java
public class Testcglb {
    public static void main(String[] args) {
        UserService userService=new UserService();
        Enhancer enhancer = new Enhancer();  
        enhancer.setSuperclass(userService.getClass());  
          
        // 3、设置回调函数  
        enhancer.setCallback(new MethodInterceptor() {  
              
            @Override  
            public Object intercept(Object proxy, Method method, Object[] args,  
                    MethodProxy methodProxy) throws Throwable {  
                if(method.getName().equals("addUser")){  
                    System.out.println("userService的add方法被拦截了。。。。");  
                    Object invoke = method.invoke(proxy, args);  
                    System.out.println("真实方法拦截之后。。。。");  
                    return invoke;  
                }  
                  
                // 不拦截  
                return method.invoke(proxy, args);  
            }  
        });  
        UserService proxy = (UserService) enhancer.create();  
          
        proxy.run();  
    }
}
```

总结：

静态代理基于代理模式编写代码逻辑简单，但灵活性低

JDK动态代理不需要其他第三方依赖

CGLIB动态代理逻辑稍微复杂，需要依赖第三方库，需要被代理类有实现接口，但灵活性高功能稍强



转载自：https://www.jianshu.com/p/b685466b1001