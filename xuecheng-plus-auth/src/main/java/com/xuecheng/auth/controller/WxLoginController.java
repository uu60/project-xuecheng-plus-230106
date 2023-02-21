package com.xuecheng.auth.controller;

import com.xuecheng.ucenter.model.po.XcUser;
import com.xuecheng.ucenter.service.AuthService;
import com.xuecheng.ucenter.service.impl.WxAuthServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import java.io.IOException;

@Controller
public class WxLoginController {

    @Autowired
    WxAuthServiceImpl wxAuthService;

    @RequestMapping("/wxLogin")
    public String wxLogin(String code, String state) throws IOException {
        // 拿授权码申请令牌，查询用户
        XcUser xcUser = wxAuthService.wxAuth(code);
        if (xcUser == null) {
            // 重定向
            return "redirect:http://www.xuecheng-plus.com/error.html";
        } else {
            String username = xcUser.getUsername();
            // 重定向到登录页面，自动登录
            return "redirect:http://www.xuecheng-plus.com/sign.html?username=" + username + "&authType=wx";
        }
    }
}
