import com.xiaour.spring.boot.annotation.MobileNumber;
import com.xiaour.spring.boot.utils.StrUtil;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * 手机号码验证器
 * @author wwx1065938
 * @since 2021/7/7
 * 房产
 *  -新房
 *  -二手房
 *
 * 装修
 *  -精装房改造
 *  -毛坯房装修
 *    -设计
 *      -方案
 *    -材料
 *    -瓦工
 *    -油工
 *    -木工
 *    -空调
 *    -暖气
 *    -家具
 *    -家电
 *
 *
 * 业主
 *  -小区入住
 *  -维权
 *
 * 汽车
 *  -交规
 *  -新车
 *  -二手车
 *  -附件
 *  -保养
 *  -保险
 *
 * 旅游
 *  -市区游
 *  -周边游
 *  -组队游
 *  -出省游
 *
 * 美食
 *  -武汉特色
 *  -
 *  -
 *  -素食主义
 *
 * 亲子
 *
 *
 * 文化
 *  -
 *  -宗教
 *
 * 情感
 *
 * 骑行
 * 徒步
 * 历史
 * 学习
 * 读书
 *
 *
 * 自然资源
 *  -矿产资源
 *  -水土资源
 * 大企业
 *
 * 运维管理：
 * 定期推荐一些活跃的社区或板块
 * 根据用户行为分析用户画像，推荐合适的社区或板块给用户
 *
 *
 * 生活
 *  -回收利用
 *    -旧衣物回收
 *    -旧书籍回收
 *
 *
 * 社会
 *  -最新政策
 *  -民生
 *  -公益
 *    -养老院
 *    -儿童福利院
 *    -残疾人助力
 *    -义工
 *
 */
public class MobileNumberValidator implements ConstraintValidator<MobileNumber, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        if (!StrUtil.isNotBlank(value)) {
            validMessage("手机号码为空", constraintValidatorContext);
            return false;
        }
        if (StrUtil.isMobileNumber(value)) {
            validMessage("手机号码格式错误", constraintValidatorContext);
            return false;
        }

        return true;
    }

    private void validMessage(String message, ConstraintValidatorContext cvc) {
        cvc.disableDefaultConstraintViolation();
        cvc.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
    }
}
