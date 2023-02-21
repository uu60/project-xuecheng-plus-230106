package com.xuecheng.content.service;

import com.xuecheng.content.model.dto.CourseCategoryTreeDto;

import java.util.List;

public interface CourseCategoryService {
    /**
     * 课程分类查询
     * @return 根结点下边所有子结点
     */
    List<CourseCategoryTreeDto> queryTreeNodes();
}
