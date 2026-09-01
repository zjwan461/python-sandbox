package io.github.sandbox.admin.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.rbac.entity.AdminUserRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** admin_user_role Mapper（联合主键，无逻辑删除） */
public interface AdminUserRoleMapper extends BaseMapper<AdminUserRole> {

    /** 查询引用了指定角色的用户ID列表（角色删除校验用） */
    @Select("SELECT user_id FROM admin_user_role WHERE role_id = #{roleId}")
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);
}
