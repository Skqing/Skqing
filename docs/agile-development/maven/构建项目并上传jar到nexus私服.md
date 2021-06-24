### 构建项目并上传jar到nexus私服



现在的项目都是微服务模式，每个人负责不同的模块，这时候我们需要包项目打包上传到公司的私服仓库以供其他同事引用下载，参考以下步骤配置即可：

1. pom.xml文件添加distributionManagement节点
```xml
<!-- 使用分发管理将本项目打成jar包，直接上传到指定服务器 -->
<distributionManagement>
    <!--正式版本-->
    <repository>
        <!-- nexus服务器中用户名：在settings.xml中<server>的id-->
        <id>yang</id>
        <!-- 这个名称自己定义 -->
        <name>Release repository</name>
        <url>http://192.168.1.105:8081/repository/yang/</url>
    </repository>
    <!--快照
    <snapshotRepository>
        <id>nexus-snapshots</id>
        <name>Snapshots repository</name>
        <url>http://192.168.1.105/repository/yang/</url>
    </snapshotRepository>-->
</distributionManagement>
```
说明：当你在settings.xml中只有一个<server>配置节点时，这里的<id>和settings.xml里面的<server>节点里面的<id>对应或者不对应都可以上传jar

2. settings.xml添加server节点
```xml
<!--maven连接nexus需要验证用户名和密码-->
<server>
    <id>yang</id>
    <username>admin</username>
    <password>admin123</password>
</server>
```
说明：这里的用户名和密码就是公司私服nexus的账号和密码

3. 执行maven命令上传到私有仓库
`mvn clean deploy` 或 `mvn clean deploy -e`
安装到本地仓库并上传到私有仓库：`mvn clean install deploy` 或 `mvn clean install deploy -e`