package com.github.mail.service.Persistence;

import com.github.mail.service.Search.ElasticsearchIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 系统启动时检查Es的index是否存在
 * @author Aster
 * @date 2026/1/7
 */
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private final ElasticsearchIndexService indexService;

    @Override
    public void run(ApplicationArguments args) {
        indexService.createKbChunksIndex();
    }
}