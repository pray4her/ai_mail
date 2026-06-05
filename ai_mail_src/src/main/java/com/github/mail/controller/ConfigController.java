package com.github.mail.controller;

import com.github.mail.model.config.AppConfig;
import com.github.mail.service.Config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置控制器
 * 提供配置读取和修改的REST API
 * 
 * @author System
 * @date 2026/01/06
 */
@Slf4j
@RestController
@RequestMapping("/api/config")
public class ConfigController {
    
    private final ConfigService configService;
    
    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }
    
    /**
     * 获取配置
     * 
     * @return 配置对象
     */
    @GetMapping
    public ResponseEntity<AppConfig> getConfig() {
        log.debug("收到获取配置请求");
        AppConfig config = configService.getConfig();
        log.debug("返回配置信息");
        return ResponseEntity.ok(config);
    }
    
    /**
     * 保存配置
     * 
     * @param config 配置对象
     * @return 操作结果
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> saveConfig(@RequestBody AppConfig config) {
        log.debug("收到保存配置请求");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            configService.saveConfig(config);
            response.put("success", true);
            response.put("message", "配置保存成功");
            log.info("配置保存成功");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("配置参数校验失败: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("保存配置时发生异常", e);
            response.put("success", false);
            response.put("message", "系统异常，配置保存失败");
            return ResponseEntity.internalServerError().body(response);
        }
    }
}