package com.github.mail.service.History;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.mail.model.config.MailConfig;
import com.github.mail.repo.Mail.domain.MailAttachment;
import com.github.mail.repo.Mail.domain.MailMessage;
import com.github.mail.repo.Mail.mapper.MailAttachmentMapper;
import com.github.mail.repo.Mail.mapper.MailMessageMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MailHistoryContextService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int BODY_SNIPPET_CHARS = 500;
    private static final int CANDIDATE_LIMIT = 200;

    private final MailMessageMapper mailMessageMapper;
    private final MailAttachmentMapper mailAttachmentMapper;
    private final MailConfig mailConfig;

    public String buildContext(Long accountId, String accountEmail, String correspondentEmail, Long currentMessageId) {
        if (accountId == null || isBlank(accountEmail) || isBlank(correspondentEmail)) {
            return "（无可用历史往来上下文）";
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(mailConfig.getHistorySync().getLookbackDays());
        List<MailMessage> candidates = mailMessageMapper.selectList(new QueryWrapper<MailMessage>()
                .eq("mail_account_id", accountId)
                .eq("is_deleted", 0)
                .and(wrapper -> wrapper.ge("sent_at", cutoff).or().ge("received_at", cutoff))
                .orderByDesc("COALESCE(sent_at, received_at)")
                .last("limit " + CANDIDATE_LIMIT));

        List<MailMessage> histories = candidates.stream()
                .filter(message -> !Objects.equals(message.getId(), currentMessageId))
                .filter(message -> isBetween(message, accountEmail, correspondentEmail))
                .sorted(Comparator.comparing(this::messageTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(mailConfig.getHistorySync().getMaxContextMessages())
                .toList();

        if (histories.isEmpty()) {
            return "（一年内未找到与该联系人的历史往来）";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < histories.size(); i++) {
            appendMessage(builder, i + 1, histories.get(i));
            if (builder.length() >= mailConfig.getHistorySync().getMaxContextChars()) {
                return builder.substring(0, mailConfig.getHistorySync().getMaxContextChars());
            }
        }
        return builder.toString().trim();
    }

    private boolean isBetween(MailMessage message, String accountEmail, String correspondentEmail) {
        String from = normalize(message.getFromEmail());
        String account = normalize(accountEmail);
        String correspondent = normalize(correspondentEmail);
        if (from.equals(correspondent)) {
            return containsEmail(message.getToEmails(), account)
                    || containsEmail(message.getCcEmails(), account)
                    || containsEmail(message.getBccEmails(), account)
                    || "INBOUND".equalsIgnoreCase(message.getDirection());
        }
        if (!from.equals(account)) {
            return false;
        }
        return containsEmail(message.getToEmails(), correspondent)
                || containsEmail(message.getCcEmails(), correspondent)
                || containsEmail(message.getBccEmails(), correspondent);
    }

    private void appendMessage(StringBuilder builder, int index, MailMessage message) {
        builder.append("[历史邮件 ").append(index).append("]\n");
        builder.append("时间: ").append(formatTime(messageTime(message))).append('\n');
        builder.append("方向: ").append(resolveDirectionLabel(message)).append('\n');
        builder.append("主题: ").append(Objects.toString(message.getSubject(), "")).append('\n');
        builder.append("正文摘要: ").append(snippet(resolveBody(message))).append('\n');
        builder.append("附件摘要: ").append(buildAttachmentSummary(message.getId())).append("\n\n");
    }

    private String buildAttachmentSummary(Long mailMessageId) {
        List<MailAttachment> attachments = mailAttachmentMapper.selectList(
                Wrappers.lambdaQuery(MailAttachment.class)
                        .eq(MailAttachment::getMailMessageId, mailMessageId)
                        .orderByAsc(MailAttachment::getId)
        );
        if (attachments == null || attachments.isEmpty()) {
            return "无";
        }
        return attachments.stream()
                .map(this::formatAttachment)
                .toList()
                .toString();
    }

    private String formatAttachment(MailAttachment attachment) {
        if ("REMOTE_LINK".equalsIgnoreCase(attachment.getAttachmentKind())) {
            return attachment.getFilename() + "（QQ超大附件链接，" + Objects.toString(attachment.getRemark(), "") + "）";
        }
        return attachment.getFilename() + "（" + Objects.toString(attachment.getContentType(), "unknown") + "）";
    }

    private String resolveBody(MailMessage message) {
        if (!isBlank(message.getBodyText())) {
            return message.getBodyText();
        }
        if (!isBlank(message.getBodyHtml())) {
            return Jsoup.parse(message.getBodyHtml()).text();
        }
        return "";
    }

    private String snippet(String body) {
        String normalized = Objects.toString(body, "").replaceAll("\\s+", " ").trim();
        if (normalized.length() <= BODY_SNIPPET_CHARS) {
            return normalized;
        }
        return normalized.substring(0, BODY_SNIPPET_CHARS);
    }

    private String resolveDirectionLabel(MailMessage message) {
        if ("OUTBOUND".equalsIgnoreCase(message.getDirection())) {
            return "本邮箱发出";
        }
        return "联系人发来";
    }

    private LocalDateTime messageTime(MailMessage message) {
        return message.getSentAt() != null ? message.getSentAt() : message.getReceivedAt();
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : FORMATTER.format(time);
    }

    private boolean containsEmail(List<String> emails, String expected) {
        if (emails == null || emails.isEmpty()) {
            return false;
        }
        return emails.stream().map(this::normalize).anyMatch(expected::equals);
    }

    private String normalize(String email) {
        return Objects.toString(email, "").trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
