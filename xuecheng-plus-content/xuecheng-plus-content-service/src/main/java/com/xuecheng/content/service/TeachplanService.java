package com.xuecheng.content.service;

import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.SaveTeachplanDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.TeachplanMedia;

import java.util.List;

public interface TeachplanService {

    List<TeachplanDto> findTeachplanTree(Long courseId);

    /**
     * 保存课程计划
     * @param dto
     */
    void saveTeachplan(SaveTeachplanDto dto);

    /**
     * 根据计划id删除课程或章节
     * @param teachplanId 课程或章节id
     */
    void deleteTeachplan(Long teachplanId);

    /**
     * 上移计划
     * @param teachplanId id
     */
    void moveUpTeachplan(Long teachplanId);

    /**
     * 下移计划
     *
     * @param teachplanId id
     */
    void moveDownTeachplan(Long teachplanId);

    TeachplanMedia associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto);
}
