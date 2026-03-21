package com.genie.query.infrastructure.util;
import java.nio.file.Paths;

public class FilePathUtils {
    private static final String LOCAL_OBJECT_STORE_PATH = "./objectStore";

    public static String getFullPath(String objectStorePath) {
        // 规范化路径，防止路径遍历攻击
        String normalizedPath = Paths.get(objectStorePath).normalize().toString();
        // 验证路径不包含上级目录访问
        if (normalizedPath.contains("../") || normalizedPath.contains("..\\") ||
                normalizedPath.startsWith("../") || normalizedPath.startsWith("..\\") ||
                normalizedPath.equals("..")) {
            throw new SecurityException("非法路径: " + objectStorePath);
        }
        // 构建完整路径
        return LOCAL_OBJECT_STORE_PATH + normalizedPath;
    }
}
