package com.genie.query.infrastructure.objectstore.local;

import com.genie.query.domain.objectstore.ObjectStore;
import com.genie.query.infrastructure.util.FilePathUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/12
 */
@Slf4j
@Service
public class ObjectStoreLocal implements ObjectStore {

    @Override
    public void uploadFile(String objectStorePath, MultipartFile file) {
        // 验证输入参数
        if (objectStorePath == null || file == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        try {
            String fullPath = FilePathUtils.getFullPath(objectStorePath);
            // 使用绝对路径，避免工作目录差异导致父目录创建与写入路径不一致
            Path targetPath = Paths.get(fullPath).toAbsolutePath().normalize();
            // 先创建所有父目录，再写入文件
            Files.createDirectories(targetPath.getParent());
            // 使用 InputStream + Files.copy，避免 transferTo(File) 在部分环境下父目录未生效的问题
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("上传成功");
        } catch (IOException e) {
            log.error("文件操作失败: " + e.getMessage(), e);
            throw new RuntimeException("文件上传失败", e);
        } catch (Exception e) {
            log.error("上传失败: " + e.getMessage(), e);
            throw new RuntimeException("文件上传过程中发生未知错误", e);
        }
    }

    @Override
    public void saveFile(String objectStorePath, String value) {
        // 将value写入 objectStorePath这个文件
        try {
            String fullPath = FilePathUtils.getFullPath(objectStorePath);
            Path targetPath = Paths.get(fullPath);
            // 确保父目录存在
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            Files.write(Paths.get(fullPath), value.getBytes());
            log.info("保存成功");
        } catch (IOException e) {
            log.error("文件操作失败: " + e.getMessage(), e);
            throw new RuntimeException("文件保存失败", e);
        }
    }

    @Override
    public InputStream downloadFile(String objectStorePath4Origin) {
        String fullPath = FilePathUtils.getFullPath(objectStorePath4Origin);
        try {
            // 将fullPath路径的文件内容读取到InputStream中
            if (!Files.exists(Paths.get(fullPath))) {
                throw new RuntimeException("文件不存在");
            }
            log.info("下载成功");
            return Files.newInputStream(Paths.get(fullPath));
        } catch (IOException e) {
            log.error("文件操作失败: " + e.getMessage(), e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    @Override
    public void deletePath(String objectStorePath) {
        String fullPath = FilePathUtils.getFullPath(objectStorePath);
        // 删除fullPath路径及下所有文件
        try {
            File directoryToDelete = new File(fullPath);
            if (directoryToDelete.exists()) {
                FileUtils.deleteDirectory(directoryToDelete);
                log.info("删除成功: {}", fullPath);
            } else {
                log.info("路径不存在，无需删除: {}", fullPath);
            }
            log.info("删除成功");
        } catch (IOException e) {
            log.error("文件操作失败: " + e.getMessage(), e);
            throw new RuntimeException("文件删除失败", e);
        }
    }

    @Override
    public void copyFile(String objectStorePathOrigin, String objectStorePathParsed) throws IOException {
        Path sourcePath = Paths.get(objectStorePathOrigin);
        Path targetPath = Paths.get(objectStorePathParsed);

        // 检查源文件是否存在
        if (!Files.exists(sourcePath)) {
            throw new IOException("源文件不存在: " + objectStorePathOrigin);
        }

        // 创建目标路径的父目录
        Path targetParent = targetPath.getParent();
        if (targetParent != null) {
            Files.createDirectories(targetParent);
        }

        Files.copy(sourcePath, targetPath);
    }

}
