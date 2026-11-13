package com.fongmi.android.tv.legado;

import java.util.Map;

/**
 * Legado（「阅读」）书源：官方 ruleHelp.md 格式子集（阶段一主线）。
 * 只做数据承载；解析/求值在 LegadoRuleEvaluator，管线在 LegadoPipeline。
 */
public class LegadoSource {

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_AUDIO = 1;
    public static final int TYPE_IMAGE = 2;
    public static final int TYPE_FILE = 3;

    /** 阶段一可跑：文本、图片。 */
    public static boolean runnable(int type) {
        // 阶段二·甲：type 1（听书）跑通；type 3 仍只存不跑
        return type == TYPE_TEXT || type == TYPE_IMAGE || type == TYPE_AUDIO;
    }

    public String bookSourceUrl = "";
    public String bookSourceName = "";
    public int bookSourceType = TYPE_TEXT;
    public String bookSourceGroup = "";
    public String searchUrl = "";
    public String exploreUrl = "";
    public String jsLib = "";
    public boolean enabledCookieJar = false;
    public Map<String, String> header;

    public RuleSearch ruleSearch;
    public RuleBookInfo ruleBookInfo;
    public RuleToc ruleToc;
    public RuleContent ruleContent;

    public static class RuleSearch {
        public String bookList = "";
        public String name = "";
        public String author = "";
        public String kind = "";
        public String wordCount = "";
        public String lastChapter = "";
        public String coverUrl = "";
        public String intro = "";
        public String bookUrl = "";
    }

    public static class RuleBookInfo {
        public String init = "";
        public String name = "";
        public String author = "";
        public String kind = "";
        public String coverUrl = "";
        public String intro = "";
        public String lastChapter = "";
        public String wordCount = "";
        public String tocUrl = "";
    }

    public static class RuleToc {
        public String chapterList = "";
        public String chapterName = "";
        public String chapterUrl = "";
        public String nextTocUrl = "";
        public String isVolume = "";
    }

    public static class RuleContent {
        public String content = "";
        public String title = "";
        public String nextContentUrl = "";
        public String sourceRegex = "";
        /** 正文清洗规则（Legado 生态字段，此前模型层没这个字段、引擎也从没执行——正文里的「第（1/3）页」分页标记就是这么漏进来的）。 */
        public String replaceRegex = "";
    }

    /** 源标识（展示用，脱敏：不记 URL 全串）。 */
    public String tag() {
        String n = bookSourceName == null ? "" : bookSourceName;
        return n.isEmpty() ? "(未命名书源)" : n;
    }

    public String typeLabel() {
        switch (bookSourceType) {
            case TYPE_TEXT: return "小说";
            case TYPE_AUDIO: return "听书";
            case TYPE_IMAGE: return "漫画";
            case TYPE_FILE: return "文件";
            default: return "未知";
        }
    }
}
