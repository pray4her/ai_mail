package com.github.mail.controller;

import com.github.mail.service.MailOperation.MailOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 邮件操作控制器 TODO： 未使用，此处邮件操作相关功能不一定必要
 * @author Aster
 * @date 2025/12/25
 */
@Slf4j
@RestController
@RequestMapping("api/mail/operation")
@RequiredArgsConstructor
public class MailOperationController {

    private final MailOperationService mailStateChange;

    /**
     * 移动邮件到指定文件夹
     */
    @PostMapping("/move")
    public ResponseEntity<String> moveMail(@RequestParam String emailId, @RequestParam String targetFolder) {
        try {
            mailStateChange.moveMail(emailId, targetFolder);
            return ResponseEntity.ok("邮件移动成功: " + emailId + " -> " + targetFolder);
        } catch (Exception e) {
            log.error("移动邮件失败: emailId={}, targetFolder={}", emailId, targetFolder, e);
            return ResponseEntity.status(500).body("移动邮件失败: " + e.getMessage());
        }
    }

    /**
     * 复制邮件到指定文件夹
     */
    @PostMapping("/copy")
    public ResponseEntity<String> copyMail(@RequestParam String emailId, @RequestParam String targetFolder) {
        try {
            mailStateChange.copyMail(emailId, targetFolder);
            return ResponseEntity.ok("邮件复制成功: " + emailId + " -> " + targetFolder);
        } catch (Exception e) {
            log.error("复制邮件失败: emailId={}, targetFolder={}", emailId, targetFolder, e);
            return ResponseEntity.status(500).body("复制邮件失败: " + e.getMessage());
        }
    }

    /**
     * 标记邮件为已读
     */
    @PostMapping("/mark-read")
    public ResponseEntity<String> markAsRead(@RequestParam String emailId) {
        try {
            mailStateChange.markAsRead(emailId);
            return ResponseEntity.ok("邮件标记为已读成功: " + emailId);
        } catch (Exception e) {
            log.error("标记邮件为已读失败: emailId={}", emailId, e);
            return ResponseEntity.status(500).body("标记邮件为已读失败: " + e.getMessage());
        }
    }

    /**
     * 标记邮件为未读
     */
    @PostMapping("/mark-unread")
    public ResponseEntity<String> markAsUnread(@RequestParam String emailId) {
        try {
            mailStateChange.markAsUnread(emailId);
            return ResponseEntity.ok("邮件标记为未读成功: " + emailId);
        } catch (Exception e) {
            log.error("标记邮件为未读失败: emailId={}", emailId, e);
            return ResponseEntity.status(500).body("标记邮件为未读失败: " + e.getMessage());
        }
    }

    /**
     * 标记邮件为已回复
     */
    @PostMapping("/mark-replied")
    public ResponseEntity<String> markAsReplied(@RequestParam String emailId) {
        try {
            mailStateChange.markAsReplied(emailId);
            return ResponseEntity.ok("邮件标记为已回复成功: " + emailId);
        } catch (Exception e) {
            log.error("标记邮件为已回复失败: emailId={}", emailId, e);
            return ResponseEntity.status(500).body("标记邮件为已回复失败: " + e.getMessage());
        }
    }

    /**
     * 标记邮件为已删除
     */
    @PostMapping("/mark-deleted")
    public ResponseEntity<String> markAsDeleted(@RequestParam String emailId) {
        try {
            mailStateChange.markAsDeleted(emailId);
            return ResponseEntity.ok("邮件标记为已删除成功: " + emailId);
        } catch (Exception e) {
            log.error("标记邮件为已删除失败: emailId={}", emailId, e);
            return ResponseEntity.status(500).body("标记邮件为已删除失败: " + e.getMessage());
        }
    }

    /**
     * 为邮件添加标签
     */
    @PostMapping("/add-label")
    public ResponseEntity<String> addLabel(@RequestParam String emailId, @RequestParam String label) {
        try {
            mailStateChange.addLabel(emailId, label);
            return ResponseEntity.ok("为邮件添加标签成功: " + emailId + " -> " + label);
        } catch (Exception e) {
            log.error("为邮件添加标签失败: emailId={}, label={}", emailId, label, e);
            return ResponseEntity.status(500).body("为邮件添加标签失败: " + e.getMessage());
        }
    }

    /**
     * 从邮件移除标签
     */
    @PostMapping("/remove-label")
    public ResponseEntity<String> removeLabel(@RequestParam String emailId, @RequestParam String label) {
        try {
            mailStateChange.removeLabel(emailId, label);
            return ResponseEntity.ok("从邮件移除标签成功: " + emailId + " -> " + label);
        } catch (Exception e) {
            log.error("从邮件移除标签失败: emailId={}, label={}", emailId, label, e);
            return ResponseEntity.status(500).body("从邮件移除标签失败: " + e.getMessage());
        }
    }
}