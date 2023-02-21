package com.xuecheng.content.feignclient;

import org.springframework.web.multipart.MultipartFile;

public class MediaServiceClientFallback implements MediaServiceClient {
    @Override
    public String upload(MultipartFile filedata, String folder, String objectName) {
        // 降级方法，拿不到异常信息
        return null;
    }
}
