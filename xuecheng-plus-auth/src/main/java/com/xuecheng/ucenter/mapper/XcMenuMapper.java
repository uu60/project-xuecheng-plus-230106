package com.xuecheng.ucenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuecheng.ucenter.model.po.XcMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author itcast
 */
public interface XcMenuMapper extends BaseMapper<XcMenu> {

    @Select("select menu_id from xc_permission where role_id in (select role_id from xc_user_role where user_id=#{userId})")
    List<XcMenu> selectPermissionByUserId(@Param("userId") String userId);
}
