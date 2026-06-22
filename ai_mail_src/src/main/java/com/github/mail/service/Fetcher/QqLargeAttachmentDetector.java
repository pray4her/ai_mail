package com.github.mail.service.Fetcher;

import com.github.mail.repo.Mail.dto.MailRawAttachment;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QqLargeAttachmentDetector {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILENAME_PATTERN = Pattern.compile("(?i)(?:filename|file|name|文件名)[:=：]\\s*([^&\\s\"'<>]+)");
    private static final String REMARK = "QQ 超大附件通常 30 天有效，历史邮件中可能只保留可访问链接。";

    public List<MailRawAttachment> detect(String textBody, String htmlBody) {
        Set<String> urls = new LinkedHashSet<>();
        collectUrls(textBody, urls);
        collectUrls(htmlBody, urls);
        if (htmlBody != null && !htmlBody.isBlank()) {
            collectUrls(Jsoup.parse(htmlBody).text(), urls);
        }
        List<MailRawAttachment> attachments = new ArrayList<>();
        for (String url : urls) {
            if (!isQqLargeAttachmentUrl(url)) {
                continue;
            }
            MailRawAttachment attachment = new MailRawAttachment();
            attachment.setFilename(resolveFilename(url));
            attachment.setContentType("text/uri-list");
            attachment.setSize(0L);
            attachment.setAttachmentKind("REMOTE_LINK");
            attachment.setExternalUrl(url);
            attachment.setExpiresAt(LocalDateTime.now().plusDays(30));
            attachment.setRemark(REMARK);
            attachments.add(attachment);
        }
        return attachments;
    }

    private void collectUrls(String content, Set<String> urls) {
        if (content == null || content.isBlank()) {
            return;
        }
        Matcher matcher = URL_PATTERN.matcher(content);
        while (matcher.find()) {
            urls.add(trimUrl(matcher.group()));
        }
    }

    private String trimUrl(String url) {
        return url.replaceAll("[)，。；;,.]+$", "");
    }

    private boolean isQqLargeAttachmentUrl(String url) {
        String normalized = url.toLowerCase();
        return normalized.contains("mail.qq.com")
                && (normalized.contains("ftn")
                || normalized.contains("download")
                || normalized.contains("large")
                || normalized.contains("file"));
    }

    private String resolveFilename(String url) {
        Matcher matcher = FILENAME_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "QQ超大附件";
    }
}
