在web项目开发过程中,文件上传是一个很常见的功能,但是我们常常需要对上传文件的大小,文件的格式,上传的文件是否空等进行判断,SpringMVC中我们只需要简单的配置就可以实现上传文件功并对上传的文件限制,Springboot中默认开启文件上传配置，默认采用Servlet3.0文件上传的API实现，单文件限制是1MB,单次请求允许总上传文件为10MB, 我们可以通过如下属性修改默认配置
```properties
spring.http.multipart.max-file-size: 1MB
spring.http.multipart.max-request-size: 10MB
```

或通过配置 Bean  MultipartConfigElement 来覆盖默认配置,但是Spring中文件上传配置是针对全局的,那么如果项目中不同的文件上传接口对文件大小限制不一样,改怎么办呢? 有人会说: 读取文件信息,然后用if条件判断啊,那对于多个接口每个接口都要读文件信息,都要写好几个if 去判断代码是不是很冗余,时间都用在了写if上了,代码也显得很low。又有人说我们可以将文件验证抽象成一个公共的方法，对于文件的大小，文件格式要求作为参数，就可以减少冗余代码了。这是一个办法，但是最终还是至少要写一个 if 去判断最终的验证结果并做处理。那么还有什么优雅的方式吗？Spring中确实是没有提供针对单个文件上传接口限制的配置，但是Spring中可以很容易集成JSR-303标准验证注解，帮助我们对方法参数的验证，SpringBoot中也是默认开启了支持，然而这些标准注解中也没有提供对文件验证的实现。该怎么办呢，Spring中我们可以很容易的实现自定义的验证注解逻辑。下面就开始实现：

首先自定义验证注解 @ValidFile 验证注解中必须要有如下三个方法，这是验证注解必须的有的否则会抛出异常。
```java
String message() default "The uploaded file is not verified.";
Class<?>[] groups() default {};
Class<? extends Payload>[] payload() default {};
```
其余为自定义属性
```java
//支持的文件后缀类型,默认全部,AliasFor("value")
String[] endsWith() default {};
 //文件后缀是否区分大小写
 boolean ignoreCase() default true;
 //上传的文件是否允许为空
 boolean allowEmpty() default false;
 //文件上传最大值,默认不限制但必须小于等于SpringMVC中文件上传配置，如果大于则根本不会走到自定义注解验证实现逻辑tomcat内部已经抛出异常，可以定义全局异常处理提示前端
 String maxSize() default DEFAULT_MAXSIZE;
  //文件上传最小值，默认不限制
 String minSize() default "0";
```
其次编写验证逻辑实现类 MultipartFileValidator，该类必须实现 ConstraintValidator接口，该类有两个泛型参数，第一个表示处理哪个验证注解类，第二参数为被注解标记的类型
具体代码如下：
```java

/**
 * 默认:<br/>
 * 上传文件不能为空<br/>
 * 允许上传所有后缀格式的文件<br/>
 * 文件后缀名不区分大小写<br/>
 * 文件最大不超过springmvc配置的文件大小<br/>
 * 文件最小不小于0 kb <br/>
 * Created by bruce on 2018/8/30 16:34
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultipartFileValidator.class)
public @interface ValidFile {
 
     String DEFAULT_MAXSIZE = "-1";
 
    /**
     * AliasFor("endsWith")
     */
    String[] value() default {};
 
    /**
     * 支持的文件后缀类型,默认全部,AliasFor("value")
     */
    String[] endsWith() default {};
 
    /**
     * 文件后缀是否区分大小写
     */
    boolean ignoreCase() default true;
 
    /**
     * 上传的文件是否允许为空
     */
    boolean allowEmpty() default false;
 
    /**
     * Max file size. Values can use the suffixes "MB" or "KB" to indicate megabytes or
     * kilobytes respectively.<br/>
     * 默认不限制但必须小于等于SpringMVC中文件上传配置
     */
    String maxSize() default DEFAULT_MAXSIZE;
 
    /**
     * Min file size. Values can use the suffixes "MB" or "KB" to indicate megabytes or
     * kilobytes respectively. default byte
     */
    String minSize() default "0";
 
    String message() default "The uploaded file is not verified.";
 
    Class<?>[] groups() default {};
 
    Class<? extends Payload>[] payload() default {};
}
```

验证逻辑代码：
```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.MultipartProperties;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
 
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * 校验器只有在真正使用的时候才会被实例化,不需要自己加实例化注解
 * Created by bruce on 2018/8/31 0:23
 */
public class MultipartFileValidator implements ConstraintValidator<ValidFile, MultipartFile> {
 
    @Autowired
    private MultipartProperties multipartProperties;
 
    private long maxSize = -1;
    private long minSize = 0;
 
    private ValidFile fileValid;
    private ArrayList<String> extension = new ArrayList<>();
 
    @Override
    public void initialize(ValidFile constraintAnnotation) {
        this.fileValid = constraintAnnotation;
        //支持的文件扩展名集合
        Collections.addAll(extension, fileValid.value());
        Collections.addAll(extension, fileValid.endsWith());
        //文件上传的最大值
        if (constraintAnnotation.maxSize().equals(ValidFile.DEFAULT_MAXSIZE)) {
            //默认最大值采用Spring中配置的单文件大小
            this.maxSize = parseSize(multipartProperties.getMaxFileSize());
        } else {
            this.maxSize = parseSize(constraintAnnotation.maxSize());
        }
        //文件上传的最小值
        this.minSize = parseSize(constraintAnnotation.minSize());
    }
 
    private void validMessage(String message, ConstraintValidatorContext cvc) {
        cvc.disableDefaultConstraintViolation();
        cvc.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
    }
 
    private long parseSize(String size) {
        Assert.hasLength(size, "Size must not be empty");
        if (size.toUpperCase(Locale.ENGLISH).endsWith("B")) {
            return Long.valueOf(size.substring(0, size.length() - 2));
        }
        if (size.toUpperCase(Locale.ENGLISH).endsWith("KB")) {
            return Long.valueOf(size.substring(0, size.length() - 2)) * 1024;
        }
        if (size.toUpperCase(Locale.ENGLISH).endsWith("MB")) {
            return Long.valueOf(size.substring(0, size.length() - 2)) * 1024 * 1024;
        }
        return Long.valueOf(size);
    }
 
    @Override
    public boolean isValid(MultipartFile multipartFile, ConstraintValidatorContext cvc) {
        String fieldName = multipartFile.getName();
        //上传的文件是空的情况
        if (multipartFile.isEmpty()) {
            if (fileValid.allowEmpty()) {
                return true;
            }
            validMessage("上传文件不能为空" + ",参数名:" + fieldName, cvc);
            return false;
        }
        //上传的文件不是空的情况,验证其他条件是否成立
        //获取文件名,如果上传文件后缀名不区分大小写则统一转成小写
        String originalFilename = multipartFile.getOriginalFilename();
        if (fileValid.ignoreCase()) {
            originalFilename = originalFilename.toLowerCase();
        }
        //TODO 可以添加读取文件头信息
        if (extension.size() > 0 && extension.stream().noneMatch(originalFilename::endsWith)) {
            validMessage("上传文件类型不符合要求" + ",参数名:" + fieldName, cvc);
            return false;
        }
        //上传文件字节数
        long size = multipartFile.getSize();
        if (size < this.minSize) {
            validMessage("上传文件不能小于指定最小值" + ",参数名:" + fieldName, cvc);
            return false;
        }
        if (size > this.maxSize) {
            validMessage("上传文件不能大于指定最大值" + ",参数名:" + fieldName, cvc);
            return false;
        }
        return true;
    }
}
```

使用Demo，注意添加@Validated注解：
```java
@Validated
@RestController
@RequestMapping("/import")
public class ImportController {
 
    @PostMapping("/test1")
    public String test1(@RequestParam("file")
                        @ValidFile(value = {".xlsx", ".xls"}, maxSize = "2MB") MultipartFile multipartFile,
                        HttpServletRequest request) throws IOException {
        Map<String, Object> result = new HashMap<>();
        result.put("getName", multipartFile.getName());
        result.put("getSize", multipartFile.getSize());
        result.put("length", multipartFile.getBytes().length);
        result.put("getContentType", multipartFile.getContentType());
        result.put("getOriginalFilename", multipartFile.getOriginalFilename());
        result.put("isEmpty", multipartFile.isEmpty());
        result.put("request", request.getContentType());
 
        return JSON.toJSONString(result, SerializerFeature.PrettyFormat);
    }
 
}
```

总结：通过自定义验证注解，可以很简单实现针对单个文件上传接口文件大小，格式的限制，我们可以直接关注业务代码的编写避免各种使用if对文件的验证，该验证注解的缺点是只是简单的验证文件的后缀，这是不全的，稍微安全的方式是读取文件的文件头进行验证。这个就大家自己实现吧，还有一点需要注意的是注解中允许上传文件的最大值设置须小于等于SpringMVC中文件上传配置，因为如果大于则根本不会走到自定义注解验证实现逻辑tomcat内部已经抛出异常，可以定义全局异常处理提示前端。




————————————————

版权声明：本文为CSDN博主「brucelwl」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。

原文链接：https://blog.csdn.net/u013202238/article/details/82290856







