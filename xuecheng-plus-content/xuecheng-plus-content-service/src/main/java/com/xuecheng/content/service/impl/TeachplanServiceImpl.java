package com.xuecheng.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.mapper.TeachplanMapper;
import com.xuecheng.content.mapper.TeachplanMediaMapper;
import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.SaveTeachplanDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.Teachplan;
import com.xuecheng.content.model.po.TeachplanMedia;
import com.xuecheng.content.service.TeachplanService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author dujianzhang
 */
@Service
public class TeachplanServiceImpl implements TeachplanService {

    @Autowired
    TeachplanMapper teachplanMapper;
    @Autowired
    TeachplanMediaMapper teachplanMediaMapper;

    @Override
    public List<TeachplanDto> findTeachplanTree(Long courseId) {
        return teachplanMapper.selectTreeNodes(courseId);
    }

    /**
     * 要同时实现新增、修改
     *
     * @param dto
     */
    @Override
    public void saveTeachplan(SaveTeachplanDto dto) {
        Teachplan teachplan = teachplanMapper.selectById(dto.getId());
        // 新增
        if (teachplan == null) {
            teachplan = new Teachplan();

            // 计算默认顺序orderby
            int teachplanCount = getTeachplanCount(dto.getCourseId(), dto.getParentid());
            teachplan.setOrderby(teachplanCount + 1);
            BeanUtils.copyProperties(dto, teachplan);
            teachplanMapper.insert(teachplan);
        }
        BeanUtils.copyProperties(dto, teachplan);
        teachplanMapper.updateById(teachplan);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void deleteTeachplan(Long teachplanId) {
        // 查询要删除的计划
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);
        boolean isChapter = teachplan.getParentid() == 0;
        // 记录所有删除的计划id
        List<Long> deletedLessonIds = new ArrayList<>();

        // 如果是章节，删除所有子计划
        if (isChapter) {
            LambdaQueryWrapper<Teachplan> wrapper = new LambdaQueryWrapper<Teachplan>().eq(Teachplan::getParentid,
                    teachplanId);
            // 收集所有删除的计划id
            teachplanMapper.selectList(wrapper).forEach(plan -> deletedLessonIds.add(plan.getId()));
            // 删除这些子计划和自己
            teachplanMapper.delete(wrapper.or().eq(Teachplan::getId, teachplanId));
        } else { // 如果是子计划，只删除自己
            deletedLessonIds.add(teachplanId);
        }

        // 所有删除的结点还要删除媒资内容
        if (!deletedLessonIds.isEmpty()) {
            teachplanMapper.deleteBatchIds(deletedLessonIds);
            teachplanMediaMapper.delete(new LambdaQueryWrapper<TeachplanMedia>().eq(TeachplanMedia::getTeachplanId,
                    teachplanId));
        }

        // 删除后要把后面的课程排序都-1
        teachplanMapper.updateOrderbys(teachplan.getCourseId(), teachplan.getOrderby(), teachplan.getParentid());
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void moveUpTeachplan(Long teachplanId) {
        moveTeachplan(teachplanId, true);
    }

    @Override
    public void moveDownTeachplan(Long teachplanId) {
        moveTeachplan(teachplanId, false);
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public TeachplanMedia associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto) {
        // 教学计划的id
        Long teachplanId = bindTeachplanMediaDto.getTeachplanId();
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);
        // 约束校验
        // 教学计划不存在无法绑定
        if (teachplan == null) {
            XueChengPlusException.cast("教学计划不存在");
        }

        // 只有二级目录才可以绑定视频
        Integer grade = teachplan.getGrade();
        if (grade != 2) {
            XueChengPlusException.cast("只有二级目录才可以绑定视频");
        }

        // 删除原来的绑定关系
        teachplanMediaMapper.delete(new LambdaQueryWrapper<TeachplanMedia>().eq(TeachplanMedia::getTeachplanId,
                teachplanId));

        // 添加新的绑定关系
        TeachplanMedia teachplanMedia = new TeachplanMedia();
        teachplanMedia.setTeachplanId(teachplanId);
        teachplanMedia.setMediaFilename(bindTeachplanMediaDto.getFileName());
        teachplanMedia.setMediaId(bindTeachplanMediaDto.getMediaId());
        teachplanMedia.setCreateDate(LocalDateTime.now());
        teachplanMedia.setCourseId(teachplan.getCourseId());
        teachplanMediaMapper.insert(teachplanMedia);
        return teachplanMedia;
    }


    private void moveTeachplan(Long teachplanId, boolean up) {
        // 找到要向上移动的计划
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);

        // 如果是第一个，不能继续上移
        if (up && teachplan.getOrderby() == 1) {
            XueChengPlusException.cast("课程或章节已经是最上面的");
        }
        // 如果是最后一个，不能下移
        // 表示同一课程下同级的计划
        LambdaQueryWrapper<Teachplan> sameLevelPlan = new LambdaQueryWrapper<Teachplan>().eq(Teachplan::getCourseId,
                teachplan.getCourseId()).eq(Teachplan::getParentid, teachplan.getParentid());
        if (!up) {
            Integer count = teachplanMapper.selectCount(sameLevelPlan);
            if (Objects.equals(teachplan.getOrderby(), count)) {
                XueChengPlusException.cast("课程或章节已经是最下面的");
            }
        }

        // 找到相邻的一个
        Teachplan relatedPlan =
                teachplanMapper.selectOne(sameLevelPlan.eq(Teachplan::getOrderby, teachplan.getOrderby() + (up ? -1 :
                        1)));
        relatedPlan.setOrderby(relatedPlan.getOrderby() + (up ? 1 : -1));
        teachplan.setOrderby(teachplan.getOrderby() + (up ? -1 : 1));

        teachplanMapper.updateById(relatedPlan);
        teachplanMapper.updateById(teachplan);
    }

    private int getTeachplanCount(Long courseId, Long parentId) {
        LambdaQueryWrapper<Teachplan> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Teachplan::getCourseId, courseId);
        queryWrapper.eq(Teachplan::getParentid, parentId);
        return teachplanMapper.selectCount(queryWrapper);
    }
}
