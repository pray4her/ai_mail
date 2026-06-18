package com.github.mail.service.KnowledgeBase;

import com.github.mail.model.config.MailConfig;
import com.github.mail.model.config.Properties.RagProperties;
import com.github.mail.repo.KnowledgeBase.domain.RagChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceProviderRoutingTest {

    @Mock
    private KnowledgeRetrievalProvider localProvider;

    @Mock
    private KnowledgeRetrievalProvider bailianProvider;

    @Mock
    private RagProperties ragProperties;

    private MailConfig mailConfig;

    @InjectMocks
    private RagService ragService;

    @BeforeEach
    void setUp() {
        mailConfig = new MailConfig();
        ragService = new RagService(
                java.util.Map.of(
                        "localEsKnowledgeRetrievalProvider", localProvider,
                        "bailianKnowledgeRetrievalProvider", bailianProvider
                ),
                mailConfig,
                ragProperties
        );
    }


    @Test
    void retrieveRagChunks_routesToLocalProvider() {
        mailConfig.getRag().setProvider("local");
        List<RagChunk> expected = List.of(new RagChunk("local-chunk", 0.9, "1"));
        when(localProvider.retrieve("query", 5, 0.3)).thenReturn(expected);

        List<RagChunk> result = ragService.retrieveRagChunks("query", 5, 0.3);

        assertEquals(expected, result);
        verify(localProvider).retrieve("query", 5, 0.3);
        verify(bailianProvider, never()).retrieve(anyString(), anyInt(), anyDouble());
    }

    @Test
    void retrieveRagChunks_routesToBailianProvider() {
        mailConfig.getRag().setProvider("bailian");
        List<RagChunk> expected = List.of(new RagChunk("bailian-chunk", 0.8, "b-1"));
        when(bailianProvider.retrieve("query", 3, 0.2)).thenReturn(expected);

        List<RagChunk> result = ragService.retrieveRagChunks("query", 3, 0.2);

        assertEquals(expected, result);
        verify(bailianProvider).retrieve("query", 3, 0.2);
        verify(localProvider, never()).retrieve(anyString(), anyInt(), anyDouble());
    }

    @Test
    void batchRetrieveRagChunks_routesToBailianProvider() {
        mailConfig.getRag().setProvider("bailian");
        List<String> queries = List.of("q1", "q2");
        List<List<RagChunk>> expected = List.of(
                List.of(new RagChunk("a", 0.7, "1")),
                List.of(new RagChunk("b", 0.6, "2"))
        );
        when(bailianProvider.batchRetrieve(queries, 5, 0.25)).thenReturn(expected);

        List<List<RagChunk>> result = ragService.batchRetrieveRagChunks(queries, 5, 0.25);

        assertEquals(expected, result);
        verify(bailianProvider).batchRetrieve(eq(queries), eq(5), eq(0.25));
        verify(localProvider, never()).batchRetrieve(anyList(), anyInt(), anyDouble());
    }

    @Test
    void retrieveRagChunks_returnsEmptyForBlankQuery() {
        List<RagChunk> result = ragService.retrieveRagChunks("  ", 5, 0.3);

        assertTrue(result.isEmpty());
        verify(localProvider, never()).retrieve(anyString(), anyInt(), anyDouble());
        verify(bailianProvider, never()).retrieve(anyString(), anyInt(), anyDouble());
    }
}
