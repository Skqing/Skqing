import com.xiaour.spring.boot.annotation.StringFieldValid;
import com.xiaour.spring.boot.utils.HashKit;
import com.xiaour.spring.boot.utils.JsoupUtils;
import com.xiaour.spring.boot.utils.StrUtil;
import lombok.extern.slf4j.Slf4j;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * 字符串验证器
 * @author wwx1065938
 * @since 2021/6/30
 * 原则：
 * 1. 谁提供接口谁就提供返回数据的DTO，为了减少交叉依赖，并且限制接口调用方随便修改返回数据的DTO
 * 2. 谁提供接口谁就提供入参的DTO，因为对于接口的实现者更清楚每个入参的意义和约定
 * 3. DTO和DO的转换要用第三方框架
 * 安全
 * 4. 不要拿前端传过来的排序属性直接传递到DO层，防止注入或者破坏，mapping中禁止使用$
 * 5. 更新接口禁止拿前端传过来的封装类直接传递到DO层，应该new一个专门更新类，针对需要更新的属性设置对应的更新值，防止非法篡改数据库其他字段值
 * 6. 所有更新方法禁止允许GET方式提交，防止CSRF漏洞等
 * 7. 每个人都建一个简单的springboot项目，多用main方法测试自己不确定的代码
 *
 * 项目结构：
 * -common-main-pom
 * -common-db-pom
 * -common-util
 * -common-spring
 * -member
 * -- client
 *     dto
 *     vo
 * -- db
 *     bean
 *     mapper
 *     service
 *      impl
 * -- main
 *     client.impl
 *     dto.converter
 *     service
 *      impl
 *     trans
 *      impl
 *     ...其他
 * 依赖关系
 * main依赖db和client
 * main依赖common-main-pom
 * db依赖common-db-pom
 * common-main-pom依赖common-util和common-spring
 * common-db-pom依赖common-util
 * common-util和common-spring都为公共模块，需要的时候添加，但common-util大部分情况为工具类提供静态方法或静态变量，不需要被spring维护。
 *      而common-spring中是需要被spring维护的，主要用于公共配置或者基于spring的一些基础组件工具。
 *
 * ------------
 *
 * 各种规范，参考《泰山版》规范
 * 数据库设计规范
 * 每个表必须包含一个非业务性的整型自增主键t_id，
 *
 * SQL规范
 * 语法索引，避免使用in，使用like要评审代码
 *
 *
 * Redis缓存使用规范
 * 缓存键、值格式规范
 *
 * 消息队列使用规范
 * 消息队列的订阅和发布需要评审
 * 消息队列的topic名称变量规范
 *
 * 各种静态变量明明和使用规范
 *
 * 多线程使用、异步方法的使用都需要评审
 * 使用@todo标记未完成的代码，使用@fixme标记需要修复的代码
 *
 * 接口调用规范
 * 谁提供服务谁就要提供调用此服务需要的参数类，以及返回结果类，并且要验证入参合法性
 *
 * 日志打印规范
 * 已经明确的异常能不打印堆栈信息就不打印
 *
 *
 * 关键信息脱敏
 *
 *
 * 系统架构图
 * 网络架构图
 * 程序结构图
 * 服务关系图
 */

@Slf4j
public class StringFieldValidator implements ConstraintValidator<StringFieldValid, String> {
    public static final Character[] CHAR_ARRAY = new Character[]{'a', 'b', 'c', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j'
            , 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
            , 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U'
            , 'V', 'W', 'X', 'Y', 'Z'};
    public static final String NUMBER_STR = "0123456789";

    private StringFieldValid constraintAnnotation;

    @Override
    public void initialize(StringFieldValid constraintAnnotation) {
        this.constraintAnnotation = constraintAnnotation;
        //@todo 对constraintAnnotation设置的值进行检查
        if (constraintAnnotation.prefix() != null && constraintAnnotation.prefix().length() >= constraintAnnotation.max()) {
            log.warn("StringFieldValid注解中，prefix的值{}长度超过了max的值{}，请重新配置合理的值", constraintAnnotation.prefix(), constraintAnnotation.max());
        }
        if (constraintAnnotation.suffix() != null && constraintAnnotation.suffix().length() >= constraintAnnotation.max()) {
            log.warn("StringFieldValid注解中，suffix的值{}长度超过了max的值{}，请重新配置合理的值", constraintAnnotation.prefix(), constraintAnnotation.max());
        }
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        if (value == null && constraintAnnotation.notNull()) {
            validMessage("参数不能为空", constraintValidatorContext);
            return false;
        }

        if (value == null && !constraintAnnotation.notNull()) {
            return true;
        }

        if (constraintAnnotation.regex() != null && constraintAnnotation.regex().length() > 0) {
            boolean check = Pattern.compile(constraintAnnotation.regex()).matcher(value).matches();
            if (check) {
                return true;
            } else {
                validMessage("参数规则不合法", constraintValidatorContext);
                return false;
            }
        }

        int lng = value.length();
        if (lng < constraintAnnotation.min() || lng > constraintAnnotation.max()) {
            validMessage("参数长度不合法", constraintValidatorContext);
            return false;
        }

        if (constraintAnnotation.prefix() != null && constraintAnnotation.prefix().length() > 0) {
            if (!value.startsWith(constraintAnnotation.prefix())) {
                validMessage("参数规则不合法", constraintValidatorContext);
                return false;
            }
        }

        if (constraintAnnotation.suffix() != null && constraintAnnotation.suffix().length() > 0) {
            if (!value.endsWith(constraintAnnotation.suffix())) {
                validMessage("参数规则不合法", constraintValidatorContext);
                return false;
            }
        }

        String excludeChar = constraintAnnotation.excludeChar();
        String includeChar = constraintAnnotation.includeChar();

        //@todo 不知道这里高并发会不会有问题
        int includeCharCount = 0;

        for (int i=0; i<lng; i++) {
            char c = value.charAt(i);
            if (c == ' ') {
                if (!constraintAnnotation.blank()) {
                    validMessage("参数不允许有空格", constraintValidatorContext);
                    return false;
                }
            }
            if (StrUtil.isNotEmpty(excludeChar) && excludeChar.contains(String.valueOf(c))) {
                validMessage("参数格式不合法", constraintValidatorContext);
                return false;
            }
            if (StrUtil.isNotEmpty(includeChar)) {
                if (includeChar.contains(String.valueOf(c))) {
                   includeCharCount++;
                }
            }

            if (!constraintAnnotation.number() && NUMBER_STR.contains(String.valueOf(c))) {
                validMessage("参数格式不合法", constraintValidatorContext);
                return false;
            }
        }

        if (StrUtil.isNotEmpty(includeChar) && includeCharCount != includeChar.length()) {
            validMessage("参数格式不合法", constraintValidatorContext);
            return false;
        }

        if (constraintAnnotation.checkXSS()) {
            String escapeVal = StrUtil.escapeHtml(value);
            if (!HashKit.md5(value).equals(HashKit.md5(escapeVal))) {
                validMessage("参数格式不合法", constraintValidatorContext);
                //@todo 检测之后发送告警信息
                return false;
            }
            String cleanVal = JsoupUtils.clean(value);
            if (!HashKit.md5(value).equals(HashKit.md5(cleanVal))) {
                validMessage("参数格式不合法", constraintValidatorContext);
                //@todo 检测之后发送告警信息
                return false;
            }
        }

        return true;
    }

    private void validMessage(String message, ConstraintValidatorContext cvc) {
        cvc.disableDefaultConstraintViolation();
        cvc.buildConstraintViolationWithTemplate(message)
            .addConstraintViolation();
    }
}
