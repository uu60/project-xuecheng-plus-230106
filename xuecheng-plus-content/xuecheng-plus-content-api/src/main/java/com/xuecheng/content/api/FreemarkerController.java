package com.xuecheng.content.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller // 返回页面
public class FreemarkerController {

    @GetMapping("/testfreemarker")
    public ModelAndView test() {
        ModelAndView modelAndView = new ModelAndView();
        // 准备模型数据
        modelAndView.addObject("name", "djz");
        // 设置视图的名称
        modelAndView.setViewName("test");
        return modelAndView;
    }
}
