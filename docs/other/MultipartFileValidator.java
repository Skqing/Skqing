import com.xiaour.spring.boot.annotation.ValidFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * 文件校验器
 * 校验器只有在真正使用的时候才会被实例化,不需要自己加实例化注解
 * @author wwx1065938
 * @since 2021/6/23
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
            this.maxSize = parseSize(multipartProperties.getMaxFileSize().toString());
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
        if (size.toUpperCase(Locale.ENGLISH).endsWith("KB")) {
            return Long.valueOf(size.substring(0, size.length() - 2)) * 1024;
        }
        if (size.toUpperCase(Locale.ENGLISH).endsWith("MB")) {
            return Long.valueOf(size.substring(0, size.length() - 2)) * 1024 * 1024;
        }
        if (size.toUpperCase(Locale.ENGLISH).endsWith("B")) {
            return Long.valueOf(size.substring(0, size.length() - 2));
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
