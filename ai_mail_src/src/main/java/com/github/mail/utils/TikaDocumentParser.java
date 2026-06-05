package com.github.mail.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Tika文档解析器
 * @author Aster
 * @date 2025/12/26
 */
@Component
@Slf4j
public class TikaDocumentParser {

    private final Tika tika = new Tika();


    /**
     * 提取文档文本内容
     * @param inputStream 输入流
     * @param fileName 文件名
     * @return 文本内容
     */
    public String extractText(InputStream inputStream, String fileName) {
        try {
            String text = tika.parseToString(inputStream);
            if (text == null) {
                return "";
            }
            return normalize(text);
        } catch (Exception e) {
            log.error("Failed to parse document: {}", fileName, e);
            return "";
        }

    }

    /**
     * 文本标准化
     * @param text 文本内容
     * @return 标准化后的文本内容
     */
    private String normalize(String text) {
        return text
                // 去掉非法空字符
                .replace("\u0000", "")
                // 统一换行
                .replace("\r\n", "\n")
                // 合并多空格（不动换行）
                .replaceAll("[ \t]+", " ")
                // 最多保留两个换行
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    /**
     * 获取有效文本内容
     * @param textBody 文本内容
     * @param htmlBody HTML内容
     * @return 文本内容
     */
    public String getEffectiveText(String textBody, String htmlBody) {
        if (textBody != null && !textBody.isBlank()) {
            return textBody;
        }
        if (htmlBody != null && !htmlBody.isBlank()) {
            return htmlToText(htmlBody);
        }
        return "";
    }

    /**
     * HTML转文本
     * @param html HTML内容
     * @return 文本内容
     */
    private String htmlToText(String html) {
        return Jsoup.parse(html).text();
    }


}
