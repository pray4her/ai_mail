package com.github.mail.utils;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用路径管理工具类
 * 集中管理配置文件路径
 * 
 * @author System
 * @date 2026/01/06
 */
@Slf4j
public class AppPaths {
    
    /**
     * 配置文件目录名称
     */
    private static final String CONFIG_DIR = "config";
    
    /**
     * 配置文件名称
     */
    private static final String CONFIG_FILE = "config.json";
    
    /**
     * 获取配置文件路径
     * 固定路径：${user.dir}/config/config.json
     * 
     * @return 配置文件Path对象
     */
    public static Path getConfigFilePath() {
        String userDir = System.getProperty("user.dir");
        Path configPath = Paths.get(userDir, CONFIG_DIR, CONFIG_FILE);
        log.debug("配置文件路径: {}", configPath.toAbsolutePath());
        return configPath;
    }
    
    /**
     * 获取配置文件所在目录路径
     * 
     * @return 配置目录Path对象
     */
    public static Path getConfigDirPath() {
        String userDir = System.getProperty("user.dir");
        Path configDir = Paths.get(userDir, CONFIG_DIR);
        log.debug("配置目录路径: {}", configDir.toAbsolutePath());
        return configDir;
    }
    
    /**
     * 检查配置文件是否存在
     * 
     * @return true-存在，false-不存在
     */
    public static boolean configFileExists() {
        return getConfigFilePath().toFile().exists();
    }
    
    /**
     * 检查配置目录是否存在
     * 
     * @return true-存在，false-不存在
     */
    public static boolean configDirExists() {
        return getConfigDirPath().toFile().exists();
    }
}