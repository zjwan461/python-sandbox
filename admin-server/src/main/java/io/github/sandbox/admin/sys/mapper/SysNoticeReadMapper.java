package io.github.sandbox.admin.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.sys.entity.SysNoticeRead;
import org.apache.ibatis.annotations.Mapper;

/** 公告已读状态 Mapper（T-0042） */
@Mapper
public interface SysNoticeReadMapper extends BaseMapper<SysNoticeRead> {
}
