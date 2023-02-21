package com.xuecheng.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.content.mapper.CourseBaseMapper;
import com.xuecheng.content.mapper.CourseCategoryMapper;
import com.xuecheng.content.mapper.CourseMarketMapper;
import com.xuecheng.content.model.dto.AddCourseDto;
import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.EditCourseDto;
import com.xuecheng.content.model.dto.QueryCourseParamsDto;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.CourseCategory;
import com.xuecheng.content.model.po.CourseMarket;
import com.xuecheng.content.service.CourseBaseInfoService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class CourseBaseInfoServiceImpl implements CourseBaseInfoService {

    @Autowired
    CourseBaseMapper courseBaseMapper;
    @Autowired
    CourseMarketMapper courseMarketMapper;
    @Autowired
    CourseCategoryMapper courseCategoryMapper;
    @Autowired
    CourseMarketServiceImpl courseMarketService;

    @Override
    public PageResult<CourseBase> queryCourseBaseList(PageParams params, QueryCourseParamsDto queryCourseParamsDto) {
        LambdaQueryWrapper<CourseBase> queryWrapper = new LambdaQueryWrapper<>();
        // 拼接查询条件
        // 根据课程名称模糊查询
        queryWrapper.like(StringUtils.isNotEmpty(queryCourseParamsDto.getCourseName()), CourseBase::getName,
                queryCourseParamsDto.getCourseName());

        // 根据课程审核状态查询
        queryWrapper.eq(StringUtils.isNotEmpty(queryCourseParamsDto.getAuditStatus()), CourseBase::getAuditStatus,
                queryCourseParamsDto.getAuditStatus());

        // 根据课程发布状态
        queryWrapper.eq(StringUtils.isNotEmpty(queryCourseParamsDto.getPublishStatus()), CourseBase::getStatus,
                queryCourseParamsDto.getPublishStatus());

        //分页参数
        Page<CourseBase> page = new Page<>(params.getPageNo(), params.getPageSize());

        // 分页查询
        Page<CourseBase> courseBasePage = courseBaseMapper.selectPage(page, queryWrapper);

        // 准备返回数据
        List<CourseBase> items = courseBasePage.getRecords();
        long total = courseBasePage.getTotal();
        return new PageResult<CourseBase>(items, total, params.getPageNo(), params.getPageSize());
    }

    @Transactional
    @Override
    public CourseBaseInfoDto createCourseBase(Long companyId, AddCourseDto dto) {
        // 对数据进行封装，调用mapper进行数据持久化
        CourseBase courseBase = new CourseBase();
        // 拷贝相同属性
        BeanUtils.copyProperties(dto, courseBase);
        // 设置机构id
        courseBase.setCompanyId(companyId);
        // 创建时间
        courseBase.setCreateDate(LocalDateTime.now());
        // 审核状态默认为未提交
        courseBase.setAuditStatus("202002");
        // 发布状态默认为未发布
        courseBase.setStatus("203001");
        // 课程基本表插入
        int insert = courseBaseMapper.insert(courseBase);
        // 获取课程id
        Long courseId = courseBase.getId();

        // 市场营销
        CourseMarket courseMarket = new CourseMarket();
        BeanUtils.copyProperties(dto, courseMarket);
        courseMarket.setId(courseId);
        // 检查如果课程收费，价格必须输入
        boolean isInsert1 = saveCourseMarket(courseMarket);
        if (insert <= 0 || !isInsert1) {
            XueChengPlusException.cast("课程插入失败");
        }

        CourseBaseInfoDto courseBaseInfoDto = new CourseBaseInfoDto();
        BeanUtils.copyProperties(courseBase, courseBaseInfoDto);
        BeanUtils.copyProperties(courseMarket, courseBaseInfoDto);
        // 根据课程分类的id查询分类的名称
        CourseCategory mtCategory = courseCategoryMapper.selectById(courseBaseInfoDto.getMt());
        CourseCategory stCategory = courseCategoryMapper.selectById(courseBaseInfoDto.getSt());
        courseBaseInfoDto.setStName(stCategory.getName());
        courseBaseInfoDto.setMtName(mtCategory.getName());

        return courseBaseInfoDto;
    }

    // 抽取对营销信息的保存
    private boolean saveCourseMarket(CourseMarket courseMarket) {
        if ("201001".equals(courseMarket.getCharge()) && (courseMarket.getPrice() == null || courseMarket.getPrice() <= 0)) {
            XueChengPlusException.cast("课程价格非法");
        }
        // 对营销表有则更新，没有则添加
        return courseMarketService.saveOrUpdate(courseMarket);
    }

    @Override
    public CourseBaseInfoDto getCourseBaseInfo(Long courseId) {
        CourseBaseInfoDto courseBaseInfoDto = new CourseBaseInfoDto();

        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        BeanUtils.copyProperties(courseBase, courseBaseInfoDto);
        // 查询分类名称
        String stName = courseCategoryMapper.selectById(courseBase.getSt()).getName();
        String mtName = courseCategoryMapper.selectById(courseBase.getMt()).getName();
        courseBaseInfoDto.setStName(stName);
        courseBaseInfoDto.setMtName(mtName);

        CourseMarket courseMarket = courseMarketMapper.selectById(courseId);
        if (courseMarket != null) {
            BeanUtils.copyProperties(courseMarket, courseBaseInfoDto);
        }
        courseBaseInfoDto.setCharge("201000");

        return courseBaseInfoDto;
    }

    @Override
    @Transactional
    public CourseBaseInfoDto updateCourseBase(Long companyId, EditCourseDto dto) {
        // 校验
        Long id = dto.getId();
        CourseBase courseBase = courseBaseMapper.selectById(id);
        if (courseBase == null) {
            XueChengPlusException.cast("课程不存在");
        }
        // 只能修改本机构的课程
        if (courseBase.getCompanyId().equals(companyId)) {
            XueChengPlusException.cast("只能修改本机构的课程");
        }

        // 封装数据
        // base
        BeanUtils.copyProperties(dto, courseBase);
        courseBase.setChangeDate(LocalDateTime.now());
        courseBaseMapper.updateById(courseBase);

        // market
        CourseMarket courseMarket = new CourseMarket();
        BeanUtils.copyProperties(dto, courseMarket);
        saveCourseMarket(courseMarket);

        return getCourseBaseInfo(dto.getId());
    }


}
