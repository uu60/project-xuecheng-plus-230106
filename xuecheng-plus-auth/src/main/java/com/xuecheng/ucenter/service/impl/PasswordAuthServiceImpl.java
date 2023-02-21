package com.xuecheng.ucenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.ucenter.feignclient.CheckCodeClient;
import com.xuecheng.ucenter.mapper.XcUserMapper;
import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;
import com.xuecheng.ucenter.model.po.XcUser;
import com.xuecheng.ucenter.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service("passwordAuthService")
public class PasswordAuthServiceImpl implements AuthService {

    @Autowired
    XcUserMapper xcUserMapper;
    @Autowired
    BCryptPasswordEncoder bCryptPasswordEncoder;
    @Autowired
    CheckCodeClient checkCodeClient;

    @Override
    public XcUserExt execute(AuthParamsDto authParamsDto) {

        // 得到验证码
        String checkcode = authParamsDto.getCheckcode();
        String checkcodeKey = authParamsDto.getCheckcodekey();
        if (StringUtils.isBlank(checkcodeKey) || StringUtils.isBlank(checkcode)) {
            throw new RuntimeException("验证码为空");
        }
        Boolean result = checkCodeClient.verify(checkcodeKey, checkcode);
        if (result == null || !result) {
            throw new RuntimeException("验证码错误");
        }

        String username = authParamsDto.getUsername();
        // 从数据库查询用户信息
        XcUser xcUser = xcUserMapper.selectOne(new LambdaQueryWrapper<XcUser>().eq(XcUser::getUsername,
                username));
        if (xcUser == null) {
            // 账号不存在
            throw new RuntimeException("账号不存在");
        }
        // 获取正确的密码
        String passwordInDb = xcUser.getPassword();
        String passwordInput = authParamsDto.getPassword();
        boolean matches = bCryptPasswordEncoder.matches(passwordInput, passwordInDb);
        if (!matches) {
            throw new RuntimeException("账号或密码错误");
        }
        XcUserExt xcUserExt = new XcUserExt();
        BeanUtils.copyProperties(xcUser, xcUserExt);

        return xcUserExt;
    }
}
