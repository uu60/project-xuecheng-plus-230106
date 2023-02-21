package com.xuecheng.media;

import org.junit.jupiter.api.Test;
import org.springframework.util.DigestUtils;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 大文件分块合并
 */
public class BigFileTest {

    @Test
    void testChunk() throws Exception {
        // 源文件
        File file = new File("/Users/dujianzhang/Movies/教程视频/Spring源码视频/002【Spring源码】2.源码学习思路（注意事项）.mp4");

        // 分块存储路径
        File dir = new File("/Users/dujianzhang/temp/");

        // 分块大小 1MB
        int chunkSize = 1024 * 1024;

        // 分块数量 ceil向上取整
        long chunkNum = (long) Math.ceil(file.length() * 1.0 / chunkSize);

        RandomAccessFile rr = new RandomAccessFile(file, "r");

        // 缓冲区
        byte[] buffer = new byte[1024];

        // 使用流对象读取源文件，向分块文件写数据，达到分块大小不再写
        for (int i = 0; i < chunkNum; i++) {
            File file1 = new File("/Users/dujianzhang/temp/" + i);
            RandomAccessFile rw = new RandomAccessFile(file1, "rw");
//            boolean newFile = file1.createNewFile();
//            if (newFile) {
            int len = -1;
            while ((len = rr.read(buffer)) != -1) {
                rw.write(buffer, 0, len);
                if (file1.length() >= chunkSize) {
                    break;
                }
            }
//            }
            rw.close();
        }
    }

    @Test
    void testMerge() throws Exception {
        // 源文件
        File file = new File("/Users/dujianzhang/output/002【Spring源码】2.源码学习思路（注意事项）.mp4");
        if (file.exists()) {
            file.delete();
        }
        file.createNewFile();

        // 获取分块文件列表
        File path = new File("/Users/dujianzhang/temp/");
        File[] files = path.listFiles();
        List<File> fileList = Arrays.asList(files);
        fileList = fileList.stream().filter(f -> {
            return !f.getName().equals(".DS_Store");
        }).collect(Collectors.toList());
        Collections.sort(fileList, Comparator.comparingInt(f -> Integer.parseInt(f.getName())));

        byte[] buf = new byte[1024];
        RandomAccessFile rw = new RandomAccessFile(file, "rw");
        fileList.forEach(f -> {
            try {
                RandomAccessFile rr = new RandomAccessFile(f, "r");
                int len = -1;
                while ((len = rr.read(buf)) != -1) {
                    rw.write(buf, 0, len);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        String m1 = DigestUtils.md5DigestAsHex(Files.newInputStream(Paths.get("/Users/dujianzhang/Movies/教程视频/Spring" +
                "源码视频/002" +
                "【Spring源码】2.源码学习思路（注意事项）.mp4")));
        String m2 = DigestUtils.md5DigestAsHex(Files.newInputStream(Paths.get("/Users/dujianzhang/output/002【Spring" +
                "源码】2" +
                ".源码学习思路（注意事项）.mp4")));
        System.out.println(m1.equals(m2));
    }

}
