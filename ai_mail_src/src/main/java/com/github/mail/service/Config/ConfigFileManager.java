package com.github.mail.service.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.mail.model.config.AppConfig;
import com.github.mail.utils.AppPaths;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * 配置文件管理器
 * 仅负责文件IO操作，使用Jackson ObjectMapper处理JSON
 *
 * @author System
 * @date 2026/01/06
 */
@Slf4j
public class ConfigFileManager {

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        // 设置美化输出
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 读取配置文件
     * 如果文件不存在，返回默认配置
     *
     * @return 配置对象
     */
    public static AppConfig readConfig() {
        Path configPath = AppPaths.getConfigFilePath();

        if (!AppPaths.configFileExists()) {
            log.warn("配置文件不存在，将使用默认配置: {}", configPath.toAbsolutePath());
            return createDefaultConfig();
        }

        try {
            log.debug("读取配置文件: {}", configPath.toAbsolutePath());
            String content = Files.readString(configPath);
            AppConfig config = objectMapper.readValue(content, AppConfig.class);
            log.debug("成功读取配置文件");
            return config;
        } catch (IOException e) {
            log.error("读取配置文件失败: {}", configPath.toAbsolutePath(), e);
            throw new RuntimeException("读取配置文件失败", e);
        }
    }

    /**
     * 写入配置文件
     *
     * @param config 配置对象
     */
    public static void writeConfig(AppConfig config) {
        Path configPath = AppPaths.getConfigFilePath();
        Path configDir = AppPaths.getConfigDirPath();
        Path tempPath = configPath.resolveSibling(configPath.getFileName() + ".tmp");

        try {
            // 确保目录存在
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            // 序列化（建议格式化，方便人读）
            String content = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(config);

            // 1️⃣ 写入临时文件
            Files.writeString(
                    tempPath,
                    content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            // 2️⃣ 强制刷盘（非常关键）
            try (FileChannel channel = FileChannel.open(tempPath, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            // 3️⃣ 原子替换
            Files.move(
                    tempPath,
                    configPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

            log.info("成功原子写入配置文件: {}", configPath.toAbsolutePath());

        } catch (IOException e) {
            log.error("写入配置文件失败: {}", configPath.toAbsolutePath(), e);
            throw new RuntimeException("写入配置文件失败", e);
        }
    }

    /**
     * 创建默认配置并写入文件
     *
     * @return 默认配置对象
     */
    public static AppConfig createDefaultConfig() {
        AppConfig defaultConfig = new AppConfig();
        writeConfig(defaultConfig);
        log.info("已创建默认配置文件: {}", AppPaths.getConfigFilePath().toAbsolutePath());
        return defaultConfig;
    }

}