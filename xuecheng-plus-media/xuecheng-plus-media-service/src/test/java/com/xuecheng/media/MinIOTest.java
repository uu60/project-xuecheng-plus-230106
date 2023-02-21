package com.xuecheng.media;

import io.minio.*;
import io.minio.messages.Item;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;

/**
 * @author Mr.M
 * @version 1.0
 * @description 测试minio上传文件、删除文件、查询文件
 * @date 2022/10/13 14:42
 */
public class MinIOTest {

    static MinioClient minioClient =
            MinioClient.builder()
                    .endpoint("http://172.16.212.10:9000")
                    .credentials("minioadmin", "minioadmin")
                    .build();


    @Test
    public void upload() {

        try {
            UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder()
                    .bucket("testbucket")
                    .object("SPRING.mp4")//同一个桶内对象名不能重复
                    .filename("/Users/dujianzhang/Movies/教程视频/Spring源码视频/002【Spring源码】2.源码学习思路（注意事项）.mp4")
                    .build();
            //上传
            minioClient.uploadObject(uploadObjectArgs);
            System.out.println("上传成功了");
        } catch (Exception e) {
            System.out.println("上传失败");
        }


    }

    @Test
    void deleteTest() {
        String fileMd5 = "4ebae30898f5953daab5ab787d7a7460";
        ListObjectsArgs args1 = ListObjectsArgs.builder().bucket("video").prefix("4/e" +
                "/4ebae30898f5953daab5ab787d7a7460/chunk/").build();
        Iterable<Result<Item>> results = minioClient.listObjects(args1);
        results.forEach(r -> {
            try {
                RemoveObjectArgs args =
                        RemoveObjectArgs.builder().bucket("video").object(r.get().objectName()).build();
                minioClient.removeObject(args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });
    }

    //指定桶内的子目录
    @Test
    public void upload2() {

        try {
            UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder()
                    .bucket("testbucket")
                    .object("1/SPRING.mp4")//同一个桶内对象名不能重复
                    .filename("/Users/dujianzhang/Movies/教程视频/Spring源码视频/002【Spring源码】2.源码学习思路（注意事项）.mp4")
                    .build();
            //上传
            minioClient.uploadObject(uploadObjectArgs);
            System.out.println("上传成功了");
        } catch (Exception e) {
            System.out.println("上传失败");
        }


    }
    //删除文件
    @Test
    public void delete() {

        try {
            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder().bucket("testbucket").object("SPRING.mp4").build();
            minioClient.removeObject(removeObjectArgs);
        } catch (Exception e) {
        }

    }
    //查询文件
    @Test
    public void getFile() {
        GetObjectArgs getObjectArgs = GetObjectArgs.builder().bucket("testbucket").object("1.mp4").build();
        try(
                FilterInputStream inputStream = minioClient.getObject(getObjectArgs);
                FileOutputStream outputStream = new FileOutputStream(new File("D:\\develop\\upload\\1_1.mp4"));
                ) {

            if(inputStream!=null){
                IOUtils.copy(inputStream,outputStream);
            }
        } catch (Exception e) {
        }

    }

}
