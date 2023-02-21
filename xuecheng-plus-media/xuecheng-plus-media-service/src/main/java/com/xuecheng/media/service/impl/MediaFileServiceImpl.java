package com.xuecheng.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.base.model.RestResponse;
import com.xuecheng.media.mapper.MediaFilesMapper;
import com.xuecheng.media.mapper.MediaProcessMapper;
import com.xuecheng.media.model.dto.QueryMediaParamsDto;
import com.xuecheng.media.model.dto.UploadFileParamsDto;
import com.xuecheng.media.model.dto.UploadFileResultDto;
import com.xuecheng.media.model.po.MediaFiles;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileService;
import io.minio.*;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;

/**
 * @author Mr.M
 * @version 1.0
 * @description
 * @date 2022/9/10 8:58
 */
@Slf4j
@Service
public class MediaFileServiceImpl implements MediaFileService {

    @Autowired
    MediaFilesMapper mediaFilesMapper;

    @Autowired
    MinioClient minioClient;

    @Autowired
    MediaFileService selfProxy;

    @Autowired
    MediaProcessMapper mediaProcessMapper;

    @Value("${minio.bucket.files}")
    private String filesBucket;
    @Value("${minio.bucket.videofiles}")
    private String videoFilesBucket;

    @Override
    public PageResult<MediaFiles> queryMediaFiles(Long companyId, PageParams pageParams,
                                                  QueryMediaParamsDto queryMediaParamsDto) {
        String filename = queryMediaParamsDto.getFilename();
        String fileType = queryMediaParamsDto.getFileType();
        String auditStatus = queryMediaParamsDto.getAuditStatus();
        Page<MediaFiles> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());

        Page<MediaFiles> mediaFilesPage = mediaFilesMapper.selectPage(page,
                new LambdaQueryWrapper<MediaFiles>().eq(MediaFiles::getCompanyId, companyId).like(!StringUtils.isEmpty(filename), MediaFiles::getFilename, filename).eq(!StringUtils.isEmpty(fileType), MediaFiles::getFileType, fileType).eq(!StringUtils.isEmpty(auditStatus), MediaFiles::getAuditStatus, auditStatus));
        return new PageResult<>(mediaFilesPage.getRecords(), mediaFilesPage.getTotal(), pageParams.getPageNo(),
                pageParams.getPageSize());
    }

    @Override
    public UploadFileResultDto uploadFile(Long companyId, UploadFileParamsDto uploadFileParamsDto, byte[] bytes,
                                          String folder, String objectName) {
        if (StringUtils.isEmpty(folder)) {
            // 自动生成
            folder = getFileFolder(new Date(), true, true, true);
        }

        if (folder.lastIndexOf("/") != folder.length() - 1) {
            folder += "/";
        }

        // 如果文件名为空，使用文件md5值
        String md5 = DigestUtils.md5DigestAsHex(bytes);
        String filename = uploadFileParamsDto.getFilename();
        if (StringUtils.isEmpty(objectName)) {
            objectName = md5 + filename.substring(filename.lastIndexOf("."));
        }

        // 拼接路径名
        objectName = folder + objectName;

        addMediaFilesToMinio(bytes, filesBucket, objectName);

        MediaFiles mediaFiles = selfProxy.addMediaFilesToDb(companyId, md5, uploadFileParamsDto, filesBucket,
                objectName);

        UploadFileResultDto uploadFileResultDto = new UploadFileResultDto();
        BeanUtils.copyProperties(mediaFiles, uploadFileResultDto);

        return uploadFileResultDto;
    }

    @Override
    @Transactional
    public MediaFiles addMediaFilesToDb(Long companyId, String fileMd5, UploadFileParamsDto uploadFileParamsDto,
                                        String bucketName, String objectName) {
        // 保存到数据库
        MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
        if (mediaFiles == null) {
            // 插入文件表
            mediaFiles = new MediaFiles();
            // 封装数据
            BeanUtils.copyProperties(uploadFileParamsDto, mediaFiles);
            mediaFiles.setId(fileMd5);
            mediaFiles.setFileId(fileMd5);
            mediaFiles.setCompanyId(companyId);
            String filename = uploadFileParamsDto.getFilename();
            mediaFiles.setFilename(filename);
            mediaFiles.setBucket(bucketName);
            mediaFiles.setFilePath(getFilePathByMd5(fileMd5, filename.contains(".") ?
                    filename.substring(filename.lastIndexOf(".")) : null));
            // 图片、mp4可以直接设置URL
            String extension = null;
            if (StringUtils.isNotEmpty(filename) && filename.contains(".")) {
                extension = filename.substring(filename.lastIndexOf("."));
            }
            // 媒体类型
            String mimeType = getMimeTypeByExtension(extension);
            if (mimeType.contains("image") || mimeType.contains("video")) {
                mediaFiles.setUrl("/" + bucketName + "/" + objectName);
            }
            mediaFiles.setCreateDate(LocalDateTime.now());
            mediaFiles.setStatus("1");
            mediaFiles.setAuditStatus("002003");

            mediaFilesMapper.insert(mediaFiles);

            // 对avi视频添加到待处理任务表
            if (mimeType.equals("video/x-msvideo")) {
                MediaProcess mediaProcess = new MediaProcess();
                BeanUtils.copyProperties(mediaFiles, mediaProcess);
                mediaProcess.setStatus("1"); // 未处理
                mediaProcessMapper.insert(mediaProcess);
            }
        }
        return mediaFiles;
    }

    @Override
    public RestResponse<Boolean> checkFile(String fileMd5) {
        // 在文件表存在，并且在文件系统存在，此文件才存在
        MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
        if (mediaFiles == null) {
            return RestResponse.success(false);
        }
        // 查看文件系统是否存在
        GetObjectArgs args =
                GetObjectArgs.builder().bucket(mediaFiles.getBucket()).object(mediaFiles.getFilePath()).build();
        try {
            InputStream is = minioClient.getObject(args);
            if (is == null) {
                return RestResponse.success(false);
            }
        } catch (Exception e) {
            return RestResponse.success(false);
        }
        // 文件已存在
        return RestResponse.success(true);
    }

    @Override
    public RestResponse<Boolean> checkChunk(String fileMd5, int chunkIndex) {
        // 分块文件的路径
        String chunkFilePath = getChunkFilePath(fileMd5, chunkIndex);

        // 查询文件系统
        GetObjectArgs args = GetObjectArgs.builder().bucket(videoFilesBucket).object(chunkFilePath).build();
        try {
            InputStream is = minioClient.getObject(args);
            if (is == null) {
                return RestResponse.success(false);
            }
        } catch (Exception e) {
            return RestResponse.success(false);
        }
        // 文件已存在
        return RestResponse.success(true);
    }

    private String getChunkFileFolderPath(String fileMd5) {
        return fileMd5.charAt(0) + "/" + fileMd5.charAt(1) + "/" + fileMd5 + "/" + "chunk" + "/";
    }

    private String getChunkFilePath(String fileMd5, int chunkId) {
        return getChunkFileFolderPath(fileMd5) + chunkId;
    }

    @Override
    public RestResponse uploadChunk(String fileMd5, int chunk, byte[] bytes) {
        // 分块存在
        if (checkChunk(fileMd5, chunk).getResult()) {
            return RestResponse.success(true);
        }
        String chunkFilePath = getChunkFilePath(fileMd5, chunk);
        try {
            addMediaFilesToMinio(bytes, videoFilesBucket, chunkFilePath);
        } catch (Exception e) {
            return RestResponse.success(false);
        }
        return RestResponse.success(true);
    }

    @Override
    public RestResponse mergechunks(Long companyId, String fileMd5, int chunkTotal,
                                    UploadFileParamsDto uploadFileParamsDto) {
        String filename = uploadFileParamsDto.getFilename();
        String extension = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : null;

//        // 因为minio太慢添加的逻辑
//        try {
//            // 如果已经存在
//            if (minioClient.getObject(GetObjectArgs.builder().bucket(videoFilesBucket).object(fileMd5.charAt(0) +
//            "/" + fileMd5.charAt(1) + "/" + fileMd5 + "/" + fileMd5 + extension).build()) != null) {
//                return RestResponse.success(true);
//            }
//        } catch (Exception ignored) {
//
//        }

        // 下载分块
        File[] chunkFiles = downloadChunkFiles(fileMd5, chunkTotal);

        // 创建临时文件作为合并文件
        File mergeFile = null;
        try {
            try {
                mergeFile = File.createTempFile("merge", extension);
            } catch (IOException e) {
                XueChengPlusException.cast("创建临时文件合并出错");
                e.printStackTrace();
            }

            // 合并分块
            byte[] buf = new byte[1024];
            try (RandomAccessFile rw = new RandomAccessFile(mergeFile, "rw")) {
                Arrays.stream(chunkFiles).forEach(f -> {
                    try (RandomAccessFile rr = new RandomAccessFile(f, "r")) {
                        int len = -1;
                        while ((len = rr.read(buf)) != -1) {
                            rw.write(buf, 0, len);
                        }
                    } catch (Exception e) {
                        XueChengPlusException.cast("合并文件过程出错");
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // 校验
            try {
                String mergeMd5 = DigestUtils.md5DigestAsHex(Files.newInputStream(mergeFile.toPath()));
                if (!mergeMd5.equals(fileMd5)) {
                    log.debug("合并文件校验不通过：{}", mergeFile.getAbsolutePath());
                    XueChengPlusException.cast("合并文件校验不通过");
                }
            } catch (Exception e) {
                // 删掉所有分块
                ListObjectsArgs args =
                        ListObjectsArgs.builder().bucket(videoFilesBucket).prefix(getChunkFileFolderPath(fileMd5)).build();
                Iterable<Result<Item>> results = minioClient.listObjects(args);
                results.forEach(r -> {
                    try {
                        RemoveObjectArgs args1 =
                                RemoveObjectArgs.builder().bucket(videoFilesBucket).object(r.get().objectName()).build();
                        minioClient.removeObject(args1);
                    } catch (Exception ignored) {

                    }
                });
                XueChengPlusException.cast("合并文件校验失败");
            }

            // 合并后文件上传到文件系统
            String objectName = getFilePathByMd5(fileMd5, extension);
            addMediaFilesToMinio(mergeFile.getAbsolutePath(), videoFilesBucket, objectName);

            uploadFileParamsDto.setFileSize(mergeFile.length());

            // 将文件信息入库
            addMediaFilesToDb(companyId, fileMd5, uploadFileParamsDto, videoFilesBucket, objectName);

            return RestResponse.success(true);
        } finally {
            // 删除临时分块文件
            if (chunkFiles != null) {
                Arrays.stream(chunkFiles).forEach(file -> {
                    if (file.exists()) {
                        file.delete();
                    }
                });
            }
            if (mergeFile != null && mergeFile.exists()) {
                mergeFile.delete();
            }
        }
    }

    @Override
    public MediaFiles getFileById(String id) {
        MediaFiles mediaFiles = mediaFilesMapper.selectById(id);
        if (mediaFiles == null) {
            XueChengPlusException.cast("文件不存在");
        }
        String url = mediaFiles.getUrl();
        if (StringUtils.isEmpty(url)) {
            XueChengPlusException.cast("文件还没有处理，请稍后预览");
        }
        return mediaFiles;
    }

    private String getFilePathByMd5(String fileMd5, String fileExt) {
        return fileMd5.charAt(0) + "/" + fileMd5.charAt(1) + "/" + fileMd5 + "/" + fileMd5 + fileExt;
    }

    private File[] downloadChunkFiles(String fileMd5, int chunkTotal) {
        // 得到分块文件所在目录
        String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);
        // 下载
        File[] chunkFiles = new File[chunkTotal];
        for (int i = 0; i < chunkTotal; i++) {
            String chunkFilePath = chunkFileFolderPath + i;

            File chunkFile = null;
            try {
                chunkFile = File.createTempFile("chunk", null);
            } catch (IOException e) {
                XueChengPlusException.cast("创建分块临时文件出错");
                e.printStackTrace();
            }

            downloadFileFromMinio(chunkFile, videoFilesBucket, chunkFilePath);
            chunkFiles[i] = chunkFile;
        }
        return chunkFiles;
    }

    @Override
    public void downloadFileFromMinio(File outputFile, String bucketName, String objectName) {
        GetObjectArgs args = GetObjectArgs.builder().bucket(bucketName).object(objectName).build();
        try (InputStream inputStream = minioClient.getObject(args); OutputStream outputStream =
                new FileOutputStream(outputFile)) {
            IOUtils.copy(inputStream, outputStream);
        } catch (Exception e) {
            XueChengPlusException.cast("从minio下载文件出错");
            e.printStackTrace();
        }
    }

    // 将文件上传至文件系统
    private void addMediaFilesToMinio(byte[] bytes, String bucketName, String objectName) {
        String extension = null;
        if (objectName.contains(".")) {
            extension = objectName.substring(objectName.lastIndexOf("."));
        }
        String contentType = getMimeTypeByExtension(objectName);

        // 上传到minio
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        PutObjectArgs putObjectArgs = PutObjectArgs.builder().bucket(bucketName).object(objectName)
                // 参数: 输入流, 对象大小, 分片大小(-1表示5M, 最大不要超过5T, 最多10000)
                .stream(byteArrayInputStream, byteArrayInputStream.available(), -1).contentType(contentType).build();
        try {
            minioClient.putObject(putObjectArgs);
        } catch (Exception e) {
            log.error("上传文件到文件系统失败：{}", e.getMessage());
            e.printStackTrace();
            XueChengPlusException.cast("上传文件到文件系统失败");
        }
    }

    private String getMimeTypeByExtension(String extension) {
        // 默认未知的二进制流
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        // 取objectName中的扩展名
        if (StringUtils.isNotEmpty(extension)) {
            ContentInfo extensionMatch = ContentInfoUtil.findExtensionMatch(extension);
            if (extensionMatch != null) {
                contentType = extensionMatch.getMimeType();
            }
        }
        return contentType;
    }

    @Override
    // 将文件上传到文件系统
    public void addMediaFilesToMinio(String filePath, String bucket, String objectName) {
        try {
            UploadObjectArgs uploadObjectArgs =
                    UploadObjectArgs.builder().bucket(bucket).object(objectName).filename(filePath).build();
            //上传
            minioClient.uploadObject(uploadObjectArgs);
            log.debug("文件上传成功：{}", filePath);
        } catch (Exception e) {
            XueChengPlusException.cast("文件上传到文件系统失败");
        }
    }

    //根据日期拼接目录
    private String getFileFolder(Date date, boolean year, boolean month, boolean day) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        //获取当前日期字符串
        String dateString = sdf.format(new Date());
        //取出年、月、日
        String[] dateStringArray = dateString.split("-");
        StringBuffer folderString = new StringBuffer();
        if (year) {
            folderString.append(dateStringArray[0]);
            folderString.append("/");
        }
        if (month) {
            folderString.append(dateStringArray[1]);
            folderString.append("/");
        }
        if (day) {
            folderString.append(dateStringArray[2]);
            folderString.append("/");
        }
        return folderString.toString();
    }

}
