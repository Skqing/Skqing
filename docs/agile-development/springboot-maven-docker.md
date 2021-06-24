

[springboot2.x+docker部署](https://blog.csdn.net/bljbljbljbljblj/article/details/83791961)

[学习Docker之Docker初体验---SpringBoot集成Docker的部署、发布与应用](https://www.jianshu.com/p/efd70ad53602)

#### 一、通过maven插件自动生成镜像并推送到docker服务器

0. 约定
`192.168.31.9`为docker服务器

1. 项目增加插件依赖
```xml


```
2. 配置本地环境变量，增加`DOCKER_HOST`变量，指向docker服务器地址
`DOCKER_HOST=tcp://192.168.31.9:2375`
>注意：如果没有设置 DOCKER_HOST 环境变量，可以命令行显示指定 DOCKER_HOST 来执行，如本机指定 DOCKER_HOST：DOCKER_HOST=tcp://192.168.31.9:2375 mvn clean install docker:build

3. 配置项目Dockerfile，文件在`src->main->docker`目录下
```
FROM java:8

MAINTAINER Sandy xxx@qq.com

VOLUME /ROOT

ADD new-demo-main-0.0.1.jar demo.jar

RUN bash -c 'touch /demo.jar'

RUN cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
&& echo 'Asia/Shanghai' >/etc/timezone

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "demo.jar"]
```
4. 执行命令
`mvn clean package docker:build` 只执行 build 操作

`mvn clean package docker:build -DpushImage` 执行 build 完成后 push 镜像

`mvn clean package docker:build -DpushImageTag` 执行 build 并 push 指定 tag 的镜像 
注意：这里必须指定至少一个 imageTag，它可以配置到 POM 中，也可以在命令行指定。命令行指定如下：mvn clean package docker:build -DpushImageTags -DdockerImageTags=imageTag_1 -DdockerImageTags=imageTag_2，POM 文件中指定配置如下：
```xml
<build>
  <plugins>
    ...
    <plugin>
      <configuration>
        ...
        <imageTags>
           <imageTag>imageTag_1</imageTag>
           <imageTag>imageTag_2</imageTag>
        </imageTags>
      </configuration>
    </plugin>
    ...
  </plugins>
</build>
```

5. 绑定Docker 命令到 Maven 各个阶段
6. 