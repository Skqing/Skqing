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
