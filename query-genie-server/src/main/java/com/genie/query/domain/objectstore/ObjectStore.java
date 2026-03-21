package com.genie.query.domain.objectstore;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
public interface ObjectStore {

    // 上传文件
    void uploadFile(String objectStorePath, MultipartFile file);

    // 保存文件
    void saveFile(String objectStorePath, String value);

    // 下载文件
    InputStream downloadFile(String objectStorePath);

    // 删除路径及下所有文件
    void deletePath(String objectStorePath);

    void copyFile(String objectStorePathOrigin, String objectStorePathParsed) throws IOException;
}
