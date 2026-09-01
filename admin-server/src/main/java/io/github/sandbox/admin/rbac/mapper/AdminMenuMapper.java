package io.github.sandbox.admin.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.rbac.entity.AdminMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** admin_menu Mapper */
public interface AdminMenuMapper extends BaseMapper<AdminMenu> {

    /** 查询用户经角色授权的全部启用菜单（含目录/菜单/按钮，去重） */
    @Select("""
            SELECT DISTINCT m.* FROM admin_menu m
            JOIN admin_role_menu rm ON rm.menu_id = m.id
            JOIN admin_user_role ur ON ur.role_id = rm.role_id
            JOIN admin_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
            WHERE ur.user_id = #{userId} AND m.deleted = 0 AND m.status = 1
            ORDER BY m.parent_id, m.sort_order
            """)
    List<AdminMenu> selectMenusByUserId(@Param("userId") Long userId);

    /** 统计某角色授权引用的菜单数（角色删除校验用） */
    @Select("SELECT COUNT(*) FROM admin_role_menu WHERE role_id = #{roleId}")
    long countRoleMenuByRoleId(@Param("roleId") Long roleId);
}
