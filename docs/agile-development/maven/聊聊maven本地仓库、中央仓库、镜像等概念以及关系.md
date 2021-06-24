

## 概要

本篇主要讲述maven的一些比较重要的概念，帮助大家更好的理解maven

还不知道maven是干什么的可以看这篇文章 [maven入门](.\maven入门) 

## 概念

### 仓库(repository)

顾名思义就是相当于一个仓库，我们在 [maven入门](.\maven入门) 讲过跟以往的项目不同maven用一个pom.xml配置文件来管理你的依赖包，那它的包也总要有个存储的地方不然你的程序编译的时候它怎么能依赖到呢。

仓库有分为本地仓库和远程仓库，本地仓库是必须也是根本而远程仓库只是为了方便你查询更多jar包而存在的。你也可以不用远程仓库但那就需要你把所有依赖的jar放到自己的本地仓库中去，这样会很麻烦，因此就有了远程仓库。只要你依赖了某个jar那么maven会自动先去你的本地仓库去寻找这个jar，找不到的话再去远程仓库寻找这个jar，如果找得到就会帮你自动把这个jar下载到本地仓库这样下次就不用再重新下载这个jar了，如果找不到就会报错了，流程如图所示。

![maven依赖包搜索过程](E:\WorkSpace\LearningLibrary\Skqing\docs\agile-development\img\maven依赖包搜索过程.png)

之前我们我们在 [maven全局配置文件settings.xml配置详解.md](maven全局配置文件settings.xml配置详解.md) 说道本地仓库就你的本地电脑里面，那么远程仓库呢？最初的时候远程仓库只有官方提供的一个 http://repo1.maven.org/maven2/ 我们称之为中央仓库，它对应的搜索页面是 http://search.maven.org/ 。那大家思考一个问题，如果全世界只有一个仓库大家编译项目都去这个仓库下载jar，那这一个仓库地址受得了吗？当然它承受不了因此便有了下面的概念叫“镜像仓库”。

### 镜像仓库

镜像仓库是什么，通过“镜像”这两个字就应该知道了，那么我们直接上图：

![maven镜像仓库](E:\WorkSpace\LearningLibrary\Skqing\docs\agile-development\img\maven镜像仓库.png)

镜像仓库就是为了解决中央仓库的压力的，顾名思义就相当于中央仓库的拷贝，特别是国内的特殊网络环境下中央仓库访问很慢因此国内就有很多镜像仓库。比较知名的镜像仓库有 [阿里云](https://maven.aliyun.com/mvn/guide) 的、[华为](https://mirrors.huaweicloud.com/) 的、网易等大公司的。

当然，仓库又分为代理仓库、父子仓库等只不过这些用到的不是很多并且难度不是很大因此暂时不说那么多了。