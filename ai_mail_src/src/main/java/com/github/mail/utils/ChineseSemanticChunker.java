package com.github.mail.utils;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 优化版：贪婪合并式语义分片器 分片主算法
 * @author Asteries
 */
public class ChineseSemanticChunker {
    private static final Logger logger = LoggerFactory.getLogger(ChineseSemanticChunker.class);

    private final int maxChunkChars;
    private final int overlapChars;
    private final int hanlpThresholdChars;

    private static final Pattern PARAGRAPH_SPLIT_PATTERN = Pattern.compile("\\n\\n+");
    private static final Pattern SENTENCE_END_PATTERN = Pattern.compile("(?<=[。！？；]|[.!?;])");

    public ChineseSemanticChunker(int maxChunkChars, int overlapChars, int hanlpThresholdChars) {
        this.maxChunkChars = Math.max(100, maxChunkChars);
        this.overlapChars = Math.min(maxChunkChars / 4, overlapChars);
        this.hanlpThresholdChars = hanlpThresholdChars;
    }

    public List<ChunkResult> chunk(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return Collections.emptyList();
        }

        String text = fullText.replace("\r\n", "\n");
        List<ChunkResult> results = new ArrayList<>();

        // Step 1: 先获取所有段落的 Region
        List<Region> paragraphRegions = splitIntoParagraphRegions(text, 0, text.length());

        // Step 2: 贪婪合并段落
        List<Region> combinedRegions = new ArrayList<>();
        Region currentAccumulator = null;

        for (Region para : paragraphRegions) {
            // 如果单个段落就超过了限制，需要进入段落内部拆分
            if (para.length() > maxChunkChars) {
                // 先把之前积累的存了
                if (currentAccumulator != null) {
                    combinedRegions.add(currentAccumulator);
                }

                // 拆分长段落并直接加入结果
                combinedRegions.addAll(splitLongParagraph(text, para));
                currentAccumulator = null;
            }
            // 尝试合并
            else if (currentAccumulator == null) {
                currentAccumulator = para;
            } else if (currentAccumulator.length() + (para.length() + 1) <= maxChunkChars) {
                currentAccumulator = new Region(currentAccumulator.start, para.end);
            } else {
                // 存满一个，开始下一个
                combinedRegions.add(currentAccumulator);
                currentAccumulator = para;
            }
        }
        if (currentAccumulator != null) {
            combinedRegions.add(currentAccumulator);
        }

        // Step 3: 将合并后的 Region 转换为带 Overlap 的结果集
        for (int i = 0; i < combinedRegions.size(); i++) {
            Region r = combinedRegions.get(i);
            int start = r.start;

            // 增加 Overlap 逻辑：除了第一个块，其余块向前探测
            if (i > 0) {
                start = Math.max(0, r.start - overlapChars);
            }

            results.add(new ChunkResult(text.substring(start, r.end), start, r.end, i));
        }

        return results;
    }

    /**
     * 拆分长段落：同样采用贪婪合并句子的逻辑
     */
    private List<Region> splitLongParagraph(String text, Region para) {
        List<Region> sentences = splitParagraphIntoSentenceRegions(text, para.start, para.end);
        List<Region> mergedSentences = new ArrayList<>();
        Region acc = null;

        for (Region s : sentences) {
            if (s.length() > maxChunkChars) {
                if (acc != null) {
                    mergedSentences.add(acc);
                }
                // 句子还是太长，调用 HanLP/字符 兜底
                mergedSentences.addAll(splitLongSentenceRegions(text, s.start, s.end));
                acc = null;
            } else if (acc == null) {
                acc = s;
            } else if (acc.length() + s.length() <= maxChunkChars) {
                acc = new Region(acc.start, s.end);
            } else {
                mergedSentences.add(acc);
                acc = s;
            }
        }
        if (acc != null) {
            mergedSentences.add(acc);
        }
        return mergedSentences;
    }


    /**
     * 当句子过长时（如法律条文、不打标点的段落）：
     * 1. 尝试使用 HanLP 按语义（词）进行合并切分
     * 2. 若 HanLP 禁用或异常，则强制按字符数切分
     */
    private List<Region> splitLongSentenceRegions(String text, int s, int e) {
        String sentence = text.substring(s, e);
        List<Region> parts = new ArrayList<>();

        // 1. 如果配置了 HanLP 阈值，尝试语义切分
        if (hanlpThresholdChars > 0) {
            try {
                List<?> termList = standardTokenizerSegment(sentence);
                // 句子内部相对指针
                int cursorInSentence = 0;
                int bufStart = s;
                int currentLen = 0;

                for (Object termObj : termList) {
                    String word = extractWordFromHanLpTerm(termObj);
                    if (word == null || word.isEmpty()) {
                        continue;
                    }

                    // 精准定位词在原文中的位置，防止重复词干扰
                    int relPos = sentence.indexOf(word, cursorInSentence);
                    if (relPos < 0) {
                        // 理论上不应发生
                        continue;
                    }

                    int absWordStart = s + relPos;
                    int wordLen = word.length();

                    // 检查合并后是否超过限制
                    if (currentLen + (relPos - cursorInSentence) + wordLen > maxChunkChars) {
                        // 封包当前积累的 Region
                        if (currentLen > 0) {
                            parts.add(new Region(bufStart, s + cursorInSentence));
                        }
                        // 开启新包
                        bufStart = absWordStart;
                        currentLen = wordLen;
                    } else {
                        currentLen += (relPos - cursorInSentence) + wordLen;
                    }
                    // 指针移动到词后
                    cursorInSentence = relPos + wordLen;
                }

                // 剩余部分封包
                if (bufStart < s + cursorInSentence) {
                    parts.add(new Region(bufStart, s + cursorInSentence));
                }

                if (!parts.isEmpty()) {
                    return parts;
                }

            } catch (Throwable th) {
                logger.warn("HanLP semantic split failed for long sentence, falling back to character split: {}", th.getMessage());
            }
        }

        // 2. 兜底方案：纯字符级物理切分
        return splitByCharactersRegions(s, e, maxChunkChars);
    }


    /* ===================== 基础原子方法 (保持 Region 纯净) ===================== */

    private List<Region> splitIntoParagraphRegions(String text, int start, int end) {
        List<Region> paras = new ArrayList<>();
        Matcher m = PARAGRAPH_SPLIT_PATTERN.matcher(text.substring(start, end));
        int cursor = start;
        while (m.find()) {
            int absStart = start + m.start();
            if (cursor < absStart) {
                paras.add(new Region(cursor, absStart));
            }
            cursor = start + m.end();
        }
        if (cursor < end) {
            paras.add(new Region(cursor, end));
        }
        return paras;
    }

    private List<Region> splitParagraphIntoSentenceRegions(String text, int start, int end) {
        List<Region> sentences = new ArrayList<>();
        Matcher m = SENTENCE_END_PATTERN.matcher(text.subSequence(start, end));
        int cursor = start;
        while (m.find()) {
            int absEnd = start + m.end();
            sentences.add(new Region(cursor, absEnd));
            cursor = absEnd;
        }
        if (cursor < end) {
            sentences.add(new Region(cursor, end));
        }
        return sentences;
    }


    /* ===================== HanLP 反射兼容逻辑 ===================== */

    private List<?> standardTokenizerSegment(String sentence) throws Exception {
        try {
            Class<?> tokenizerClass = Class.forName("com.hankcs.hanlp.tokenizer.StandardTokenizer");
            java.lang.reflect.Method segMethod = tokenizerClass.getMethod("segment", String.class);
            return (List<?>) segMethod.invoke(null, sentence);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("HanLP library not found in classpath.");
        }
    }

    private String extractWordFromHanLpTerm(Object termObj) throws Exception {
        // 兼容不同版本的 HanLP (有的版本是 term.word 字段，有的是 getWord() 方法)
        try {
            java.lang.reflect.Field field = termObj.getClass().getDeclaredField("word");
            field.setAccessible(true);
            return (String) field.get(termObj);
        } catch (NoSuchFieldException e) {
            java.lang.reflect.Method method = termObj.getClass().getMethod("toString");
            // 通常 term.toString() 返回的是 "词/词性"，需要截取
            String ts = (String) method.invoke(termObj);
            return ts.contains("/") ? ts.substring(0, ts.lastIndexOf("/")) : ts;
        }
    }
    // 为篇幅省略，逻辑同你原算法中的 HanLP 逻辑，核心是当句子 > maxChunkChars 时调用

    private List<Region> splitByCharactersRegions(int s, int e, int limit) {
        List<Region> parts = new ArrayList<>();
        int cursor = s;
        while (cursor < e) {
            int end = Math.min(cursor + limit, e);
            parts.add(new Region(cursor, end));
            cursor = end;
        }
        return parts;
    }

    /* ===================== 内部类 ===================== */

    private static class Region {
        final int start;
        final int end;

        Region(int s, int e) {
            this.start = s;
            this.end = e;
        }

        int length() {
            return end - start;
        }
    }

    @Getter
    public static class ChunkResult {
        private final String content;
        private final int startOffset;
        private final int endOffset;
        private final int index;

        public ChunkResult(String content, int start, int end, int index) {
            this.content = content;
            this.startOffset = start;
            this.endOffset = end;
            this.index = index;
        }
    }
}