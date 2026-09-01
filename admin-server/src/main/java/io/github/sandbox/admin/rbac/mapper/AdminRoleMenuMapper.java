package io.github.sandbox.admin.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.rbac.entity.AdminRoleMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** admin_role_menu Mapper（联合主键，物理删改口径） */
public interface AdminRoleMenuMapper extends BaseMapper<AdminRoleMenu> {

    /** 角色已授权菜单ID列表 */
    @Select("SELECT menu_id FROM admin_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);
}
