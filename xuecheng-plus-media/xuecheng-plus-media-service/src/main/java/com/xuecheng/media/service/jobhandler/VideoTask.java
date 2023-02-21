package com.xuecheng.media.service.jobhandler;

import com.xuecheng.base.utils.Mp4VideoUtil;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileProcessService;
import com.xuecheng.media.service.MediaFileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.concurrent.*;

@Component
@Slf4j
public class VideoTask {

    @Autowired
    MediaFileProcessService mediaFileProcessService;
    @Autowired
    MediaFileService mediaFileService;

    @Value("${videoprocess.ffmpegpath}")
    String ffmpegPath;


    /**
     * 视频处理任务
     *
     * @throws Exception
     */
    @XxlJob("videoJobHandler")
    public void videoJobHandler() throws Exception {

        // 分片参数
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();

        // 查询待处理任务
        List<MediaProcess> mediaProcessList = mediaFileProcessService.getMediaProcessList(shardTotal, shardIndex, 2);
        if (mediaProcessList == null || mediaProcessList.isEmpty()) {
            log.debug("当前无待处理任务");
        }

        // 启动多线程去处理
        ExecutorService pool = new ThreadPoolExecutor(
                mediaProcessList.size(),
                mediaProcessList.size(),
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        CountDownLatch countDownLatch = new CountDownLatch(mediaProcessList.size());
        mediaProcessList.forEach(mediaProcess -> {
            pool.execute(() -> {
                try {
                    // 视频处理状态
                    String status = mediaProcess.getStatus();
                    if ("2".equals(status)) {
                        log.debug("视频已处理，不再执行");
                        return;
                    }
                    // 任务执行逻辑
                    String bucket = mediaProcess.getBucket();
                    String filePath = mediaProcess.getFilePath();
                    String fileId = mediaProcess.getFileId();
                    String filename = mediaProcess.getFilename();

                    // 将原始视频下载到本地
                    File source = null;
                    File target = null;

                    try {
                        source = File.createTempFile("source", ".avi");
                        target = File.createTempFile("target", ".mp4");
                    } catch (Exception e) {
                        log.error("处理视频前创建文件出错");
                        return;
                    }
                    try {
                        mediaFileService.downloadFileFromMinio(source, bucket, filePath);
                    } catch (Exception e) {
                        log.error("下载原始文件过程出错");
                        e.printStackTrace();
                    }
                    //创建工具类对象
                    Mp4VideoUtil videoUtil = new Mp4VideoUtil(ffmpegPath, source.getAbsolutePath(), fileId + ".mp4",
                            target.getAbsolutePath());
                    //开始视频转换，成功将返回success
                    String result = videoUtil.generateMp4();
                    String processedStatus = "3";
                    String url = null;
                    if ("success".equals(result)) {
                        // 转换成功
                        String mp4FilePath = getFilePathByMd5(fileId, ".mp4");
                        try {
                            // 上传到minio
                            mediaFileService.addMediaFilesToMinio(target.getAbsolutePath(), bucket, mp4FilePath);
                        } catch (Exception e) {
                            log.debug("上传文件出错：{}", e.getMessage());
                            return;
                        }
                        processedStatus = "2";
                        url = "/" + bucket + "/" + mp4FilePath;
                    }
                    try {
                        mediaFileProcessService.saveProcessFinishStatus(mediaProcess.getId(), processedStatus, fileId,
                                url, result);
                    } catch (Exception e) {
                        log.error("保存任务处理结果出错");
                    }
                } finally {
                    countDownLatch.countDown();
                }
            });
        });

        // 等待任务完成
        countDownLatch.await(30, TimeUnit.MINUTES);
    }

    private String getFilePathByMd5(String fileMd5, String fileExt) {
        return fileMd5.charAt(0) + "/" + fileMd5.charAt(1) + "/" + fileMd5 + "/" + fileMd5 + fileExt;
    }
}
