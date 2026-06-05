package com.github.mail.utils;


/**
 * 生成minio对象存储路径
 * @author Aster
 * @date 2025/12/29
 */
public class PathUtil {

    public static String buildOriginalObjectKey(Long documentId, String originalFilename) {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        return "documents/" + documentId + "/original" + ext;
    }

    public static String buildTextObjectKey(Long documentId) {
        return "documents/" + documentId + "/parsed.txt";
    }
}
