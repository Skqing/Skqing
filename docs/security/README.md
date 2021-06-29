最近公司再做安全方面的工作，我正好负责了这一块的一部分，因此根据工作中的相关经验做一下总结。

现代社会人们对于安全和隐私问题越来越看重了，因此对于程序开发者即便你不是专门从事安全相关的方面但了解一些安全知识还是很有必要的。

我们大致可以把安全问题按照下面的分类：

- 系统安全

- 网站安全
  - 命令注入
  - SQL注入
  - XSS注入
  - 上传文件漏洞
  - 下载文件漏洞
  - 不安全重定向漏洞
  - CSV注入
  - CSRF攻击
  - CORS跨域漏洞
  - JSONP劫持漏洞
  - SSRF攻击
  - XXE漏洞
  - 反序列化漏洞
  - 日志注入（CRLF注入）
  - 正则表达式漏洞
  - 资源耗尽
  - 逻辑漏洞
    - 短信轰炸 横向短信轰炸和纵向短信轰炸
    - 权限控制 未授权访问、水平越权、垂直越权
    -  绕过安全filter
    - 条件竞争


未看[一网打尽！每个程序猿都该了解的黑客技术大汇总](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484082&idx=1&sn=af11ee383fad0670adecb658a701f1b7&source=41#wechat_redirect) :star::star::star::star:

研究HTTPS原理


**【网络安全系列】系列** :star::star::star:

1. [拆了公司发的中秋礼包，我竟然要被全员批评！](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247487027&idx=1&sn=a71d5cb291b6030fb309b8b31a85af02)

2. [轩辕，网络安全这条路，怎么走？](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247486440&idx=1&sn=bd4949562817dc506294e6a3696dd5db)

3. [CPU深夜狂飙，一帮大佬都傻眼了···](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247488709&idx=1&sn=4ecca4e0dc6a63c3f47f4522d7c2415d)

4. [一个小小指针，竟把Linux内核攻陷了！](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247488996&idx=1&sn=7b806541ba4458bc4f2c83e7f743bdd2)

5. [发现一个木马，竟然偷传我珍藏几十G的视频！](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247489337&idx=1&sn=12b4021ccce926e4853b677842c2855c)

6. [哈哈哈哈，这个勒索软件笑死我了！](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247489779&idx=1&sn=f52ccc74688026aeebecd6a549c9ddac)

7. [肝了7天，让你从小白变黑客！](https://mp.weixin.qq.com/s/0ZGUaiduRA5pB-lOUJrJLQ)

**【趣话网络安全】系列** :star::star::star:

1. [我是一个explorer的线程](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484107&idx=1&sn=2623a1e7ffdfdc9779946df279d5179f)

2. [我是一个杀毒软件线程](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484105&idx=1&sn=9748a196ca2e8a0dd53d2809838e6954)

3. [我是一个IE浏览器线程](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484104&idx=1&sn=d7880c11c9c9de4694f0b0f6ebfe5a96)

4. [比特宇宙－TCP/IP的诞生](https://github.com/Skqing/3kqing.github.com/issues/5)

5. [产品vs程序员：你知道www是怎么来的吗？](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484102&idx=1&sn=750e535cd401d56f069f6a7c3fe11eb8)

6. [我是一个流氓软件线程](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484101&idx=1&sn=ed5f0d79e92a813f559e13b18e347420)

7. [默认浏览器争霸传奇](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484100&idx=1&sn=0b72dbf0d977f8ac7fd1e5cbb45fd59c)

8. [远去的传说：安全软件群雄混战史](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484099&idx=1&sn=77b719d61881b27556a81efa9471470e)

9. [一个HTTP数据包的奇幻之旅](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484098&idx=1&sn=1c6a80bd949f875fa361f7b47654e5bd)

10. [一条SQL注入引出的惊天大案](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484095&idx=1&sn=1101f7e4150ada7777321a1362189660)

11. [DDoS攻击：无限战争](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484094&idx=1&sn=e476edc73341133f52242109ec6ae5fd)

12. [一个DNS数据包的惊险之旅](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484093&idx=1&sn=3c902e3fc269bb1339384975b88e3348)

13. [堆栈里的秘密行动：劫持执行流](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484088&idx=1&sn=7aa4d0b0c3e18eec07889823a3f4815b)

14. [路由器里的广告秘密](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484090&idx=1&sn=b2077a6c797d3de184923227e2ec0d97)

15. [谁动了你的HTTPS流量？](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484087&idx=1&sn=2a97d7268a800cba3c3028c1ac7e2e6c)

16. [一个神秘URL酿大祸，差点让我背锅！](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247484217&idx=1&sn=028cc3a01471248eb79edc92250a33d8)

17. [非中间人就没法劫持TCP了吗？](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247485358&idx=1&sn=de1448cdcea7af96296897a050005f03)

18. [完了！TCP出了大事！](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247485390&idx=1&sn=6c7a3bb9931bf36ebdb88093af3e91f2)

19. [我偷偷监听了他们的通信流量···](https://mp.weixin.qq.com/s?__biz=MzIyNjMxOTY0NA==&mid=2247490464&idx=1&sn=4a783ebd87efab729fda8c656600bc41)

[]()

[]()

[]()

[]()

[]()
