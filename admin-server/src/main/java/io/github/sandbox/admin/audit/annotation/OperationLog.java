package io.github.sandbox.admin.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 写操作审计注解（T-0020）：标注于 Controller 方法，成功后自动落 admin_op_log。
 *
 * <p>module 取值口径（design.md §5.2）：user / role / menu / client / apikey /
 * ratelimit / session / bridge / sysconfig；
 * type 收敛为 add / edit / delete / enable / disable / revoke / reset / force。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 模块 */
    String module();

    /** 操作类型 */
    String type();
}
