package com.xuecheng.content.service.jobhandler;

import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.service.CoursePublishService;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MessageProcessAbstract;
import com.xuecheng.messagesdk.service.MqMessageService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 课程发布任务
 */
@Component
@Slf4j
public class CoursePublishTask extends MessageProcessAbstract {

    @Autowired
    CoursePublishService coursePublishService;


    @XxlJob("CoursePublishJobHandler")
    public void coursePublishJobHandler() throws Exception {
        // 分片参数
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        log.debug("shardIndex = " + shardIndex + ", shardTotal = " + shardTotal);
        process(shardIndex, shardTotal, "course_publish", 5, 60);
    }

    /**
     * 课程发布的执行逻辑
     *
     * @param mqMessage 执行任务内容
     * @return
     */
    @Override
    public boolean execute(MqMessage mqMessage) {
        Long courseId = Long.valueOf(mqMessage.getBusinessKey1());
        log.debug("开始执行课程的发布任务，课程id：{}", courseId);

        // 将课程信息进行静态化
        generateCourseHtml(mqMessage, courseId);

        // 将静态页面上传到minio

        // 存储到redis

        // 创建课程索引
        saveCourseIndex(mqMessage, courseId);

        return true;
    }

    private void saveCourseIndex(MqMessage mqMessage, Long courseId) {
        log.debug("开始进行课程静态化，课程id：{}", courseId);
        Long id = mqMessage.getId();

        MqMessageService mqMessageService = getMqMessageService();
        // 判断任务是否完成
        int stageTwo = mqMessageService.getStageTwo(id);
        if (stageTwo > 0) {
            log.debug("创建课程索引已经完成，任务信息：{}", mqMessage);
            return;
        }

        coursePublishService.saveCourseIndex(courseId);

        getMqMessageService().completedStageTwo(id);
    }

    private void generateCourseHtml(MqMessage mqMessage, Long courseId) {
        log.debug("开始进行课程静态化，课程id：{}", courseId);
        Long id = mqMessage.getId();

        MqMessageService mqMessageService = getMqMessageService();
        // 判断任务是否完成
        int stageOne = mqMessageService.getStageOne(id);
        if (stageOne > 0) {
            log.debug("静态化课程信息任务已经完成，任务信息：{}", mqMessage);
            return;
        }
        // 生成静态文件
        File file = coursePublishService.generateCourseHtml(courseId);
        if (file == null) {
            XueChengPlusException.cast("课程静态化异常");
        }
        // 将html文件上传到minio
        coursePublishService.uploadCourseHtml(courseId, file);
        // 给当前任务打上完成标记
        getMqMessageService().completedStageOne(id);
    }

}
