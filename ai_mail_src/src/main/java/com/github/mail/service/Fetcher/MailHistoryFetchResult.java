package com.github.mail.service.Fetcher;

import com.github.mail.repo.Mail.dto.MailRaw;

import java.util.List;

public record MailHistoryFetchResult(
        String folderName,
        long uidValidity,
        long highestFetchedUid,
        List<MailRaw> messages
) {

    public MailHistoryFetchResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
