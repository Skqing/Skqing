### 真正解决maven私服仓库和远程仓库共存问题



我们要实现的功能就是构建项目是无论是依赖中央仓库的包还是公司私有仓库的包都不会报错。

那我们分析一下，如果是公司的内部包肯定是要从公司私有仓库去下载的，如果是非内部包的话是有两种情况的：

1. 先从私有仓库下载，没有这个包的时候再搜索远程仓库（假设只用阿里云的镜像），具体实现有两个步骤：

1)  配置settings.xml的镜像
settings.xml

```xml
<mirror>
  <id>aliyunmaven</id>
  <mirrorOf>*</mirrorOf>
  <name>阿里云公共仓库</name>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```
这样配置之后所有包都从阿里云镜像下载，但这样的话内部包也从阿里云下载肯定会失败的，所以还要在项目的顶级pom.xml里面加入如下配置：
pom.xml

```xml
<repositories>
    <repository>
        <id>maven_nexus_212</id>
        <name>maven_nexus_212</name>
        <layout>default</layout>
        <url>http://192.168.31.212:8081/repository/maven-public/</url>
        <releases>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </releases>
        <snapshots>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </snapshots>
    </repository>
</repositories>
```

这样一来maven构建时会先从maven_nexus_212的私有仓库去下载包，但因为我们在setting.xml里面配置的mirror是<mirrorOf>*</mirrorOf>，这个时候maven_nexus_212这个内部仓库也被阿里云的仓库给代理了，因此需要修改setting.xml的镜像的<mirrorOf>配置
settings.xml

```xml
<mirror>
  <id>aliyunmaven</id>
  <mirrorOf>*,!maven_nexus_212</mirrorOf>
  <name>阿里云公共仓库</name>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```
这个意思就是所有的包下载都会走阿里云镜像，除了要从maven_nexus_212下载的包。这样一来就实现了我们的目的。



2. 只配置私有仓库，这个时候要配置公司的私有仓库对远程仓库进行代理

我们去掉settings.xml里面自己定义的镜像，只在pom.xml里面加上公司的私有仓库配置：

settings.xml
```xml
<!--
<mirror>
  <id>aliyunmaven</id>
  <mirrorOf>*,!maven_nexus_212</mirrorOf>
  <name>阿里云公共仓库</name>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
-->
```

pom.xml
```xml
<repositories>
    <repository>
        <id>maven_nexus_212</id>
        <name>maven_nexus_212</name>
        <layout>default</layout>
        <url>http://192.168.31.212:8081/repository/maven-public/</url>
        <releases>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </releases>
        <snapshots>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </snapshots>
    </repository>
</repositories>
```

这个时候无论内部包还是外部包都是从公司私有仓库去下载，这个时候外部包肯定是无法下载的，那就需要我们在公司的私有仓库里修改配置。

我们先看一下私有仓库的仓库关系：http://192.168.31.212:8081/#admin/repository/repositories

![](E:\WorkSpace\LearningLibrary\Skqing\docs\agile-development\maven\img\20210428132541.png)

默认公司的私有仓库是有这么多的，并且我们项目里面依赖的是maven-public，那我们点开maven-public看看它的属性：


![](E:\WorkSpace\LearningLibrary\Skqing\docs\agile-development\maven\img\20210428132512.png)

看到了吧，这个仓库下包含三个成员。maven-releases这个仓库是我们上传公司的生产版本的包，maven-snapshots这个仓库是我们上传公司的快照版本的包，这也是为什么我们只依赖了maven-public就能下载我们公司内部的包了。那maven-central呢是干嘛用的，我们去找一下这个仓库打开它的属性看一下：

![](E:\WorkSpace\LearningLibrary\Skqing\docs\agile-development\maven\img\20210428133208.png)

看到标记红色的那里就豁然开朗了，实际上这个仓库对中央仓库（默认配置）进行了代理，理论上你下载远程包也是可以从私有仓库上下载下来的只是有时候这个远程仓库可能网络不稳定导致下载失败然。所以我们可以修改一下这个默认的代理仓库，比如改为国内阿里云的。

以上两种方案推荐用第一种，毕竟公司私有仓库不是谁都能去修改这个配置的，而自己的settings.xml和pom.xml都是可以自己控制的。