import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.io.FileNotFoundException;


/**
 * @author wwx1065938
 * @since 2021/6/22
 * --------------------------------------------
 *
 * 项目安全：
 * 防重放攻击，接口幂等性
 * CSRF攻击
 * XSS攻击
 * DDOS攻击
 * 网络隔离，内网到外网，外网到内网限制隔离措施
 * 支付回调接口幂等性，防重放攻击
 * 出票回调接口幂等性，防重放攻击，加队列，防止阻塞
 *
 *
 * 开发测试
 * 复杂方法要进行性能测试
 * 复杂业务功能要进行单元测试
 *
 *
 * 日志问题
 * 1. 可查询
 * 2. 可实时改变日志等级
 * 3. 可对异常日志发出告警
 * 4. 回调网关日志根据业务分离
 * 5. 更新接口从前端开始加上调用链标识traceId
 *
 *
 * 模块服务重构
 * 消息发送服务
 * 报表服务
 * 用户服务
 * 去掉MongoDB，减少人力投入
 * 分布式事物必要性评估
 *
 *
 * 保持系统稳定性
 * 关键服务增加多个实例
 * 熔断降级策略
 * 服务内存、IO、CPU监控报警
 * 服务JVM监控报警
 * 服务异常日志监控报警
 * 服务对外接口健康检查
 * Redis集群稳定性
 * rocketmq集群稳定性
 * nacos集群稳定性
 * 数据库主从服务稳定性
 * K8S集群稳定性
 *
 *
 * 数据库：
 * 考虑使用读写分离
 * 使用第三方数据库中间件
 * 数据库指标、慢查询、死锁监控
 *
 *
 * 运维环境
 * 公司做一台远程电脑，可以远程运维
 * 通过软件开发人员可以临时在家里远程办公
 * 开发和测试用同一套环境，但开发不能随便修改测设环境数据库的数据
 *
 * 公司文档
 * 项目的开发、设计，运维文档分文件夹放到钉钉上，给文件夹设置不同的权限
 * 模块设计文档要重写，包括功能、设计思路、和其他模块交互、内外网交互
 * 复杂功能设计文档也推荐写
 *
 * 钉钉管理
 * 取消日报填写，改为周报
 * 增加项目管理功能
 * 移除离职员工
 *
 * 项目管理
 * 找合适的BUG管理系统
 * 基于gitlab流水线和CICD
 *
 * 工作策略
 * 开发新功能时：设计、编码、注释、总结都要做到细节到位，因此需要更多的工作时间。不过这样刚好符合延长项目工期的主管需要。
 */

@Slf4j
@Plugin(name = "ExceptionFilter", category = Node.CATEGORY, elementType = Filter.ELEMENT_TYPE, printObject = true)
public class ExceptionFilter extends AbstractFilter {

    private Result onMatch;
    private Result onMisMatch;
    private Level level;

    public ExceptionFilter(Result onMatch, Result onMisMatch, Level level) {
        super();
        this.onMatch = onMatch;
        this.onMisMatch = onMisMatch;
        this.level = level;
    }

    /**
     * @PluginFactory注解对应的必须使用一个静态的方法，传入的参数用@PluginAttribute修饰
     * @param match
     * @param mismatch
     * @param level
     * @return
     */
    @PluginFactory
    public static ExceptionFilter createFilter(@PluginAttribute("onMatch") final Result match
            , @PluginAttribute("onMismatch") final Result mismatch
            ,@PluginAttribute("level") final Level level) {
        final Result onMatch = match == null ? Result.NEUTRAL : match;
        final Result onMismatch = mismatch == null ? Result.DENY : mismatch;
        return new ExceptionFilter(onMatch, onMismatch, level);
    }

    @Override
    public Result filter(LogEvent event) {
        if (event.getLevel().name().equalsIgnoreCase(this.level.name())) {
            if (event.getThrown() instanceof FileNotFoundException) {
                System.out.println(111);
//                event.getThrown().initCause(new FileNotFoundException("这是自定义的异常"));

                log.error("event.getMessage().toString()这个不打印");
                System.out.println(event.getThrownProxy().getMessage());
                event.getThrown().setStackTrace(new StackTraceElement[0]);
                System.out.println(222);
                return this.onMatch;
            }
        }
        return this.onMisMatch;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return super.filter(logger, level, marker, msg, t);
    }
}
