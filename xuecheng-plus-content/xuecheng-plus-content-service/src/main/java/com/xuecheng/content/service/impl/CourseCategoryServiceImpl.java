package com.xuecheng.content.service.impl;

import com.xuecheng.content.mapper.CourseCategoryMapper;
import com.xuecheng.content.model.dto.CourseCategoryTreeDto;
import com.xuecheng.content.model.po.CourseCategory;
import com.xuecheng.content.service.CourseCategoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CourseCategoryServiceImpl implements CourseCategoryService {
    @Autowired
    CourseCategoryMapper courseCategoryMapper;

    @Override
    public List<CourseCategoryTreeDto> queryTreeNodes() {
        List<CourseCategory> courseCategories = courseCategoryMapper.selectList(null);
        Map<String, CourseCategoryTreeDto> allDtos = new HashMap<>();
        courseCategories.forEach(category -> {
            // 去除根结点
            if (!"1".equals(category.getId())) {
                CourseCategoryTreeDto dto = new CourseCategoryTreeDto();
                BeanUtils.copyProperties(category, dto);
                allDtos.put(dto.getId(), dto);
            }
        });
        List<CourseCategoryTreeDto> result = new ArrayList<>();
        allDtos.forEach((key, dto) -> {
            // 根结点下属结点添加到结果
            if ("1".equals(dto.getParentid())) {
                result.add(dto);
            } else {
                CourseCategoryTreeDto parentDto = allDtos.get(dto.getParentid());
                if (parentDto.getChildrenTreeNodes() == null) {
                    parentDto.setChildrenTreeNodes(new ArrayList<>());
                }
                parentDto.getChildrenTreeNodes().add(dto);
            }
        });
        result.sort(Comparator.comparing(CourseCategory::getId));

        return result;
    }
}
