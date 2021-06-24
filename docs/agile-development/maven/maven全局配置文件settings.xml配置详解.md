# 一、概要

## 1、settings.xml的作用

它是用来设置Maven参数的配置文件。并且，settings.xml是Maven的全局配置文件。settings.xml中包含类似本地仓库、远程仓库和联网使用的代理信息等配置。

## 2、settings.xml文件位置

settings.xml文件一般存在于Maven的安装目录的conf子目录下面，或者是用户目录的.m2子目录下面。

## 3、配置的优先级

其实相对于多用户的PC机而言，在Maven安装目录的conf子目录下面的settings.xml才是真正的全局的配置。而用户目录的.m2子目录下面的settings.xml的配置只是针对当前用户的。当这两个文件同时存在的时候，那么对于相同的配置信息用户目录下面的settings.xml中定义的会覆盖Maven安装目录下面的settings.xml中的定义。用户目录下的settings.xml文件一般是不存在的，但是Maven允许我们在这里定义我们自己的settings.xml，如果需要在这里定义我们自己的settings.xml的时候就可以把Maven安装目录下面的settings.xml文件拷贝到用户目录的.m2目录下，然后改成自己想要的样子。

用户目录的settings.xml文件的位置：

```java
#Windows
C:\Users\你当前的用户名\.m2\settings.xml  例如： C:\Users\Administrator\.m2\settings.xml
#Linux
/你当前的账户名/.m2/settings.xml 例如： /root/.m2/settings.xml

```

# 二、settings.xml元素详解

## 1、顶级元素概览

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                          https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <localRepository/>
  <interactiveMode/>
  <usePluginRegistry/>
  <offline/>
  <pluginGroups/>
  <servers/>
  <mirrors/>
  <proxies/>
  <profiles/>
  <activeProfiles/>
</settings>
```

### 1.1、LocalRepository

**作用**：该值表示构建系统本地仓库的路径。
其默认值：~/.m2/repository。(当你C盘空间不够的时候要把仓库挪到别的盘，那就需要这个设置)

```xml
<localRepository>${user.home}/.m2/repository</localRepository>
```

### 1.2、InteractiveMode

**作用**：表示maven是否需要和用户交互以获得输入。
如果maven需要和用户交互以获得输入，则设置成true，反之则应为false。默认为true。

```xml
<interactiveMode>true</interactiveMode>
```

### 1.3、UsePluginRegistry

**作用**：maven是否需要使用plugin-registry.xml文件来管理插件版本。
如果需要让maven使用文件~/.m2/plugin-registry.xml来管理插件版本，则设为true。默认为false。

```xml
<usePluginRegistry>false</usePluginRegistry>
```

### 1.4、Offline

**作用**：这个属性表示在Maven进行项目编译和部署等操作时是否允许Maven进行联网来下载所需要的信息。
如果构建系统需要在离线模式下运行，则为true，默认为false。
当由于网络设置原因或者安全因素，构建服务器不能连接远程仓库的时候，该配置就十分有用。

```xml
<offline>false</offline>
```

### 1.5、PluginGroups

 **作用**：在**pluginGroups**元素下面可以定义一系列的**pluginGroup**元素。表示当通过**plugin**的前缀来解析plugin的时候到哪里寻找。**pluginGroup**元素指定的是**plugin的groupId**。默认情况下，Maven会自动把**org.apache.maven.plugins** 和 **org.codehaus.mojo** 添加到**pluginGroups**下。

```xml
<pluginGroups>
  <!--plugin的组织Id（groupId） -->
  <pluginGroup>org.codehaus.mojo</pluginGroup>
</pluginGroups>
```

### 1.6、Servers

**作用**：一般，仓库的下载和部署是在pom.xml文件中的**repositories** 和 **distributionManagement** 元素中定义的。然而，一般类似用户名、密码（有些仓库访问是需要安全认证的）等信息不应该在**pom.xml**文件中配置，这些信息可以配置在 **settings.xml** 中。（如果公司有私服仓库那就要在这里配置）

```xml
<!--配置服务端的一些设置。一些设置如安全证书不应该和pom.xml一起分发。这种类型的信息应该存在于构建服务器上的settings.xml文件中。 -->
<servers>
  <!--服务器元素包含配置服务器时需要的信息 -->
  <server>
    <!--这是server的id（注意不是用户登陆的id），该id与distributionManagement中repository元素的id相匹配。 -->
    <id>server001</id>
    <!--鉴权用户名。鉴权用户名和鉴权密码表示服务器认证所需要的登录名和密码。 -->
    <username>my_login</username>
    <!--鉴权密码 。鉴权用户名和鉴权密码表示服务器认证所需要的登录名和密码。密码加密功能已被添加到2.1.0 +。详情请访问密码加密页面 -->
    <password>my_password</password>
    <!--鉴权时使用的私钥位置。和前两个元素类似，私钥位置和私钥密码指定了一个私钥的路径（默认是${user.home}/.ssh/id_dsa）以及如果需要的话，一个密语。将来passphrase和password元素可能会被提取到外部，但目前它们必须在settings.xml文件以纯文本的形式声明。 -->
    <privateKey>${usr.home}/.ssh/id_dsa</privateKey>
    <!--鉴权时使用的私钥密码。 -->
    <passphrase>some_passphrase</passphrase>
    <!--文件被创建时的权限。如果在部署的时候会创建一个仓库文件或者目录，这时候就可以使用权限（permission）。这两个元素合法的值是一个三位数字，其对应了unix文件系统的权限，如664，或者775。 -->
    <filePermissions>664</filePermissions>
    <!--目录被创建时的权限。 -->
    <directoryPermissions>775</directoryPermissions>
  </server>
</servers>
```

### 1.7、Mirrors

**作用**：用于定义一系列的远程仓库的镜像。我们可以在pom中定义一个下载工件的时候所使用的远程仓库。但是有时候这个远程仓库会比较忙，所以这个时候人们就想着给它创建镜像以缓解远程仓库的压力，也就是说会把对远程仓库的请求转换到对其镜像地址的请求。每个远程仓库都会有一个id，这样我们就可以创建自己的mirror来关联到该仓库，那么以后需要从远程仓库下载工件的时候Maven就可以从我们定义好的mirror站点来下载，这可以很好的缓解我们远程仓库的压力。在我们定义的mirror中每个远程仓库都只能有一个mirror与它关联，也就是说你不能同时配置多个mirror的mirrorOf指向同一个repositoryId。（这是一个很重要但很多人搞不清的属性，后面文章会详细介绍）

```xml
<mirrors>
  <!-- 给定仓库的下载镜像。 -->
  <mirror>
    <!-- 该镜像的唯一标识符。id用来区分不同的mirror元素。 -->
    <id>mirrorId</id>
    <!-- 镜像名称 -->
    <name>PlanetMirror Australia</name>
    <!-- 该镜像的URL。构建系统会优先考虑使用该URL，而非使用默认的服务器URL。 -->
    <url>http://downloads.planetmirror.com/pub/maven2</url>
    <!-- 被镜像的服务器的id。例如，如果我们要设置了一个Maven中央仓库（http://repo.maven.apache.org/maven2/）的镜像，就需要将该元素设置成central。这必须和中央仓库的id central完全一致。 -->
    <mirrorOf>repositoryId</mirrorOf>
  </mirror>
</mirrors>
```

### 1.8、Proxies

**作用**：用来配置不同的代理。

```xml
<proxies>
  <!--代理元素包含配置代理时需要的信息 -->
  <proxy>
    <!--代理的唯一定义符，用来区分不同的代理元素。 -->
    <id>myproxy</id>
    <!--该代理是否是激活的那个。true则激活代理。当我们声明了一组代理，而某个时候只需要激活一个代理的时候，该元素就可以派上用处。 -->
    <active>true</active>
    <!--代理的协议。 协议://主机名:端口，分隔成离散的元素以方便配置。 -->
    <protocol>http</protocol>
    <!--代理的主机名。协议://主机名:端口，分隔成离散的元素以方便配置。 -->
    <host>proxy.somewhere.com</host>
    <!--代理的端口。协议://主机名:端口，分隔成离散的元素以方便配置。 -->
    <port>8080</port>
    <!--代理的用户名，用户名和密码表示代理服务器认证的登录名和密码。 -->
    <username>proxyuser</username>
    <!--代理的密码，用户名和密码表示代理服务器认证的登录名和密码。 -->
    <password>somepassword</password>
    <!--不该被代理的主机名列表。该列表的分隔符由代理服务器指定；例子中使用了竖线分隔符，使用逗号分隔也很常见。 -->
    <nonProxyHosts>*.google.com|ibiblio.org</nonProxyHosts>
  </proxy>
</proxies>
```

### 1.9、Profiles

**作用**：根据环境参数来调整构建配置的列表。
settings.xml中的profile元素是pom.xml中profile元素的裁剪版本。它包含了id、activation、repositories、pluginRepositories和 properties元素。这里的profile元素只包含这五个子元素是因为这里只关心构建系统这个整体（这正是settings.xml文件的角色定位），而非单独的项目对象模型设置。如果一个settings.xml中的profile被激活，它的值会覆盖任何其它定义在pom.xml中带有相同id的profile。当所有的约束条件都满足的时候就会激活这个profile。

```xml
<profiles>
    <profile>
　　<!-- profile的唯一标识 -->
        <id>test</id>     
        <!-- 自动触发profile的条件逻辑 -->
        <activation>
            <activeByDefault>false</activeByDefault>
            <jdk>1.6</jdk>
            <os>
                <name>Windows 7</name>
                <family>Windows</family>
                <arch>x86</arch>
                <version>5.1.2600</version>
            </os>
            <property>
                <name>mavenVersion</name>
                <value>2.0.3</value>
            </property>
            <file>
                <exists>${basedir}/file2.properties</exists>
                <missing>${basedir}/file1.properties</missing>
            </file>
        </activation>
        <!-- 扩展属性列表 -->
        <properties />
        <!-- 远程仓库列表 -->
        <repositories />
        <!-- 插件仓库列表 -->
        <pluginRepositories />
      ...
    </profile>
</profiles>
```

#### 1.9.1、Activation

**作用**：自动触发profile的条件逻辑。这是profile中最重要的元素。跟pom.xml中的profile一样，settings.xml中的profile也可以在特定环境下改变一些值，而这些环境是通过activation元素来指定的。activation元素并不是激活profile的唯一方式。settings.xml文件中的activeProfile元素可以包含profile的id。profile也可以通过在命令行，使用-P标记和逗号分隔的列表来显式的激活（如，-P test）。
**jdk**：表示当jdk的版本满足条件的时候激活，在这里是1.6。这里的版本还可以用一个范围来表示，如　

<jdk>[1.4,1.7)</jdk> 表示1.4、1.5和1.6满足；
<jdk>[1.4,1.7]</jdk> 表示1.4、1.5、1.6和1.7满足；

**os**：表示当操作系统满足条件的时候激活。
**property**：property是键值对的形式，表示当Maven检测到了这样一个键值对的时候就激活该profile。

##### (1)下面的示例表示当存在属性hello的时候激活该profile。

```xml
<property>
    <name>hello</name>
</property>
```

##### (2)下面的示例表示当属性hello的值为world的时候激活该profile。

```xml
<property>
    <name>hello</name>
    <value>world</value>
</property>
```

这个时候如果要激活该profile的话，可以在调用Maven指令的时候加上参数hello并指定其值为world，如：

```bash
mvn compile –Dhello=world
```

**file**：表示当文件存在或不存在的时候激活，exists表示存在，missing表示不存在。如下面例子表示当文件hello/world不存在的时候激活该profile。

```xml
<profile>
    <activation>
        <file>
            <missing>hello/world</missing>
        </file>
    </activation>
</profile>
```

**activeByDefault**：当其值为true的时候表示如果没有其他的profile处于激活状态的时候，该profile将自动被激活。
**properties**：用于定义属性键值对的。当该profile是激活状态的时候，properties下面指定的属性都可以在pom.xml中使用。对应profile的扩展属性列表。
maven属性和ant中的属性一样，可以用来存放一些值。这些值可以在pom.xml中的任何地方使用标记${X}来使用，这里X是指属性的名称。属性有五种不同的形式，并且都能在settings.xml文件中访问。

```xml
<!--
  1. env.X: 在一个变量前加上"env."的前缀，会返回一个shell环境变量。例如,"env.PATH"指代了$path环境变量（在Windows上是%PATH%）。
  2. project.x：指代了POM中对应的元素值。例如: <project><version>1.0</version></project>通过${project.version}获得version的值。
  3. settings.x: 指代了settings.xml中对应元素的值。例如：<settings><offline>false</offline></settings>通过 ${settings.offline}获得offline的值。
  4. Java System Properties: 所有可通过java.lang.System.getProperties()访问的属性都能在POM中使用该形式访问，例如 ${java.home}。
  5. x: 在<properties/>元素中，或者外部文件中设置，以${someVar}的形式使用。
 -->
<properties>
    <user.install>${user.home}/our-project</user.install>
</properties>
```

注：如果该profile被激活，则可以在pom.xml中使用${user.install} 。

**repositories**：用于定义远程仓库的，当该profile是激活状态的时候，这里面定义的远程仓库将作为当前pom的远程仓库。它是maven用来填充构建系统本地仓库所使用的一组远程仓库。

```xml
<repositories>
  <!--包含需要连接到远程仓库的信息 -->
  <repository>
    <!--远程仓库唯一标识 -->
    <id>codehausSnapshots</id>
    <!--远程仓库名称 -->
    <name>Codehaus Snapshots</name>
    <!--如何处理远程仓库里发布版本的下载 -->
    <releases>
      <!--true或者false表示该仓库是否为下载某种类型构件（发布版，快照版）开启。 -->
      <enabled>false</enabled>
      <!--该元素指定更新发生的频率。Maven会比较本地POM和远程POM的时间戳。这里的选项是：always（一直），daily（默认，每日），interval：X（这里X是以分钟为单位的时间间隔），或者never（从不）。 -->
      <updatePolicy>always</updatePolicy>
      <!--当Maven验证构件校验文件失败时该怎么做-ignore（忽略），fail（失败），或者warn（警告）。 -->
      <checksumPolicy>warn</checksumPolicy>
    </releases>
    <!--如何处理远程仓库里快照版本的下载。有了releases和snapshots这两组配置，POM就可以在每个单独的仓库中，为每种类型的构件采取不同的策略。例如，可能有人会决定只为开发目的开启对快照版本下载的支持。参见repositories/repository/releases元素 -->
    <snapshots>
      <enabled />
      <updatePolicy />
      <checksumPolicy />
    </snapshots>
    <!--远程仓库URL，按protocol://hostname/path形式 -->
    <url>http://snapshots.maven.codehaus.org/maven2</url>
    <!--用于定位和排序构件的仓库布局类型-可以是default（默认）或者legacy（遗留）。Maven 2为其仓库提供了一个默认的布局；然而，Maven 1.x有一种不同的布局。我们可以使用该元素指定布局是default（默认）还是legacy（遗留）。 -->
    <layout>default</layout>
  </repository>
</repositories>
```

(1) releases、snapshots：这是对于工件的类型的限制。
(2) enabled：表示这个仓库是否允许这种类型的工件
(3) updatePolicy：表示多久尝试更新一次。可选值有always、daily、interval:minutes（表示每多久更新一次）和never。
(4) checksumPolicy：当Maven在部署项目到仓库的时候会连同校验文件一起提交，checksumPolicy表示当这个校验文件缺失或不正确的时候该如何处理，可选项有ignore、fail和warn。
**pluginRepositories**：在Maven中有两种类型的仓库，一种是存储工件的仓库，另一种就是存储plugin插件的仓库。pluginRepositories的定义和repositories的定义类似，它表示Maven在哪些地方可以找到所需要的插件。和repository类似，只是repository是管理jar包依赖的仓库，pluginRepositories则是管理插件的仓库。maven插件是一种特殊类型的构件。由于这个原因，插件仓库独立于其它仓库。pluginRepositories元素的结构和repositories元素的结构类似。每个pluginRepository元素指定一个Maven可以用来寻找新插件的远程地址。

```xml
<pluginRepositories>
  <!-- 包含需要连接到远程插件仓库的信息.参见profiles/profile/repositories/repository元素的说明 -->
  <pluginRepository>
    <releases>
      <enabled />
      <updatePolicy />
      <checksumPolicy />
    </releases>
    <snapshots>
      <enabled />
      <updatePolicy />
      <checksumPolicy />
    </snapshots>
    <id />
    <name />
    <url />
    <layout />
  </pluginRepository>
</pluginRepositories>
```

示例：

```xml
<activation>
  <!--profile默认是否激活的标识 -->
  <activeByDefault>false</activeByDefault>
  <!--当匹配的jdk被检测到，profile被激活。例如，1.4激活JDK1.4，1.4.0_2，而!1.4激活所有版本不是以1.4开头的JDK。 -->
  <jdk>1.5</jdk>
  <!--当匹配的操作系统属性被检测到，profile被激活。os元素可以定义一些操作系统相关的属性。 -->
  <os>
    <!--激活profile的操作系统的名字 -->
    <name>Windows XP</name>
    <!--激活profile的操作系统所属家族(如 'windows') -->
    <family>Windows</family>
    <!--激活profile的操作系统体系结构 -->
    <arch>x86</arch>
    <!--激活profile的操作系统版本 -->
    <version>5.1.2600</version>
  </os>
  <!--如果Maven检测到某一个属性（其值可以在POM中通过${name}引用），其拥有对应的name = 值，Profile就会被激活。如果值字段是空的，那么存在属性名称字段就会激活profile，否则按区分大小写方式匹配属性值字段 -->
  <property>
    <!--激活profile的属性的名称 -->
    <name>mavenVersion</name>
    <!--激活profile的属性的值 -->
    <value>2.0.3</value>
  </property>
  <!--提供一个文件名，通过检测该文件的存在或不存在来激活profile。missing检查文件是否存在，如果不存在则激活profile。另一方面，exists则会检查文件是否存在，如果存在则激活profile。 -->
  <file>
    <!--如果指定的文件存在，则激活profile。 -->
    <exists>${basedir}/file2.properties</exists>
    <!--如果指定的文件不存在，则激活profile。 -->
    <missing>${basedir}/file1.properties</missing>
  </file>
</activation>
```

### 1.10、ActiveProfiles

**作用**：手动激活profiles的列表，按照profile被应用的顺序定义activeProfile。
该元素包含了一组activeProfile元素，每个activeProfile都含有一个profile id。任何在activeProfile中定义的profile id，不论环境设置如何，其对应的 profile都会被激活。如果没有匹配的profile，则什么都不会发生。
例如，env-test是一个activeProfile，则在pom.xml（或者profile.xml）中对应id的profile会被激活。如果运行过程中找不到这样一个profile，Maven则会像往常一样运行。

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                      https://maven.apache.org/xsd/settings-1.0.0.xsd">
  ...
  <activeProfiles>
    <!-- 要激活的profile id -->
    <activeProfile>env-test</activeProfile>
  </activeProfiles>
  ...
</settings>
```

以上内容只是介绍了每个属性的意思，后面文章会详细介绍一些重点常用属性的用法和一些问题。

原文链接：https://www.cnblogs.com/soupk/p/9303611.html