import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.ThrowablePatternConverter;
import org.apache.logging.log4j.core.util.Constants;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MultiformatMessage;
import org.apache.logging.log4j.util.MultiFormatStringBuilderFormattable;
import org.apache.logging.log4j.util.StringBuilderFormattable;

/**
 * 会对异常做特定编码处理的格式转换类
 * 使用时，在layout中添加 %eEx即可
 * @author wwx1065938
 * @since 2021/6/22
 * @todo 并不是很完美，参考ThrowablePatternConverter，MessagePatternConverter进行优化
 * https://bbs.huaweicloud.com/blogs/259253
 * --------------------------------------------
 * 地图、地理位置、地理特征、地貌地势
 *
 * 影视分享：
 * 影视解说UP
 * 国产电视剧吐槽UP
 *
 * 法律普及：
 * 1.小区安全
 * 高空抛物罪
 * 27层以上电瓶车上楼违法
 * 电瓶车电梯自燃
 * 宠物问题
 * 2.
 *
 */

@Plugin(name = "NotSafeThrowablePatternConverter", category = PatternConverter.CATEGORY)
// 自己定义的layout键值
@ConverterKeys({"uEx"})
public class NotSafeThrowablePatternConverter extends ThrowablePatternConverter {
    private static final String NOLOOKUPS = "nolookups";

    private final String[] formats;
    private final Configuration config;
    private final boolean noLookups;

    protected NotSafeThrowablePatternConverter(String name, String style, String[] options, Configuration config) {
        super(name, style, options, config);
        this.formats = options;
        this.config = config;
        final int noLookupsIdx = loadNoLookups(options);
        this.noLookups = Constants.FORMAT_MESSAGES_PATTERN_DISABLE_LOOKUPS || noLookupsIdx >= 0;
    }

    /**
     * log4j2中使用反射调用newInstance静态方法进行构造，因此必须要实现这个方法。
     * @param options
     * @param config
     * @return
     */
    public static NotSafeThrowablePatternConverter newInstance(final String[] options, final Configuration config) {
        return new NotSafeThrowablePatternConverter("NotSafeThrowablePatternConverter", "throwable", options, config);
    }

    @Override
    public void format(final LogEvent event, final StringBuilder toAppendTo) {
        Throwable throwable = event.getThrown();
        if (throwable == null) {
            final Message msg = event.getMessage();
            if (msg instanceof StringBuilderFormattable) {
                final StringBuilder workingBuilder = toAppendTo;
                final int offset = workingBuilder.length();
                if (msg instanceof MultiFormatStringBuilderFormattable) {
                    ((MultiFormatStringBuilderFormattable) msg).formatTo(formats, workingBuilder);
                } else {
                    ((StringBuilderFormattable) msg).formatTo(workingBuilder);
                }

                if (event.getLoggerName().contains("com.huawei.it.publicsaas")) {
                    //过滤日志中的回车换行
                    for (int i = 0; i < workingBuilder.length() - 1; i++) {
                        if (workingBuilder.charAt(i) == '\r' || workingBuilder.charAt(i) == '\n') {
                            workingBuilder.setCharAt(i, '_');
                        }
                    }
                }
                // TODO can we optimize this?
                if (config != null && !noLookups) {
                    for (int i = offset; i < workingBuilder.length() - 1; i++) {
                        if (workingBuilder.charAt(i) == '$' && workingBuilder.charAt(i + 1) == '{') {
                            final String value = workingBuilder.substring(offset, workingBuilder.length());
                            workingBuilder.setLength(offset);
                            workingBuilder.append(config.getStrSubstitutor().replace(event, value));
                        }
                    }
                }
                return;
            }
            if (msg != null) {
                String result;
                if (msg instanceof MultiformatMessage) {
                    result = ((MultiformatMessage) msg).getFormattedMessage(formats);
                } else {
                    result = msg.getFormattedMessage();
                }
                if (result != null) {
                    toAppendTo.append(config != null && result.contains("${")
                            ? config.getStrSubstitutor().replace(event, result) : result);
                } else {
                    toAppendTo.append("null");
                }
            }
            return;
        }
        // 使用自定义的EncodeThrowableProxy，里面重写了ThrowableProxy的getMessage方法
        EncodeThrowableProxy proxy = new EncodeThrowableProxy(throwable);
        // 添加到toAppendTo
        proxy.formatExtendedStackTraceTo(toAppendTo, options.getIgnorePackages(), options.getTextRenderer(), getSuffix(event), options.getSeparator());
    }

    private int loadNoLookups(final String[] options) {
        if (options != null) {
            for (int i = 0; i < options.length; i++) {
                final String option = options[i];
                if (NOLOOKUPS.equalsIgnoreCase(option)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 进行过特定编码处理的ThrowableProxy
     */
    static class EncodeThrowableProxy extends ThrowableProxy {
        public EncodeThrowableProxy(Throwable throwable) {
            super(throwable);
        }

        // 将\r和\n进行编码，避免日志注入
        @Override
        public String getMessage() {
//            String encodeMessage = super.getMessage().replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n");
            String encodeMessage = super.getMessage();
            if (super.getThrowable() instanceof java.io.FileNotFoundException) {
                encodeMessage = "(系统找不到指定的路径。)";
            }
            if (super.getThrowable() instanceof java.sql.SQLException
                || super.getThrowable() instanceof java.net.BindException
                || super.getThrowable() instanceof java.util.ConcurrentModificationException
                || super.getThrowable() instanceof javax.naming.InsufficientResourcesException
                || super.getThrowable() instanceof java.util.MissingResourceException
                || super.getThrowable() instanceof java.util.jar.JarException
                || super.getThrowable() instanceof java.lang.OutOfMemoryError
                || super.getThrowable() instanceof java.lang.StackOverflowError
                || super.getThrowable() instanceof java.security.acl.NotOwnerException) {
                encodeMessage = "__";
            }
            return encodeMessage;
        }
    }
}
