package io.github.sandbox.admin.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.rbac.entity.AdminRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** admin_role Mapper */
public interface AdminRoleMapper extends BaseMapper<AdminRole> {

    /** 查询用户已绑定的启用角色（按 sort_order 排序） */
    @Select("""
            SELECT r.* FROM admin_role r
            JOIN admin_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.deleted = 0 AND r.status = 1
            ORDER BY r.sort_order
            """)
    List<AdminRole> selectRolesByUserId(@Param("userId") Long userId);
}
