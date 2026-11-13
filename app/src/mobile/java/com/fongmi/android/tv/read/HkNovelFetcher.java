package com.fongmi.android.tv.read;

import android.text.TextUtils;

import com.fongmi.android.tv.api.hk.HkHttp;
import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.api.hk.HkRule;
import com.fongmi.android.tv.api.hk.HkRuleManager;
import com.fongmi.android.tv.ui.hk.HkHomeParser;
import com.github.catvod.crawler.SpiderDebug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 海阔小说正文抓取（阶段二）：复用与文章页同一套解析器
 * {@link HkHomeParser#findList}，无头驱动后抽正文行拼纯文本。
 * <p>
 * 接缝说明：数据从规则的 findRule 进（与文章页同规则）、章节 URL 的 HTML 来，
 * 经 HkHomeParser（文章页同一静态解析器）出 HkItem 列表，取 long_text /
 * rich_text / text_* 行的 title 拼纯文本，喂文字阅读器。
 * <p>
 * 断了会怎样：规则未安装 / 取页失败 / 解析无正文行 → 返回空串，阅读器显示
 * "正文抓取失败"，不崩。js: 规则依赖引擎绑定（海阔页打开过即绑定），未绑定
 * 时同样走失败分支。
 */
public class HkNovelFetcher {

    private HkNovelFetcher() {
    }

    /** 同步抓取章节正文（调用方保证在后台线程）。 */
    public static String fetchText(String ruleTitle, String chapterUrl) {
        if (TextUtils.isEmpty(ruleTitle) || TextUtils.isEmpty(chapterUrl)) return "";
        try {
            HkRule rule = HkRuleManager.get().getRule(ruleTitle);
            if (rule == null) {
                SpiderDebug.log("reading", "novel fetch no rule: %s", ruleTitle);
                return "";
            }
            // 节点打点：归属当前规则（kind=hk、id=规则名），与「按源定向」海阔栏一致。
            com.github.catvod.net.SourceGate.Mark prevMark =
                    com.github.catvod.net.SourceGate.push(
                            com.fongmi.android.tv.clash.SourceProxyPolicy.KIND_HK, ruleTitle);
            String html;
            try {
                html = HkHttp.get(chapterUrl, rule.resolvedUa());
            } finally {
                com.github.catvod.net.SourceGate.pop(prevMark);
            }
            if (TextUtils.isEmpty(html)) {
                SpiderDebug.log("reading", "novel fetch empty html");
                return "";
            }
            List<HkItem> items = parseItems(chapterUrl, rule, html);
            String text = extractText(items);
            SpiderDebug.log("reading", "novel fetch rule=%s items=%d chars=%d",
                    ruleTitle, items.size(), text.length());
            return text;
        } catch (Throwable t) {
            SpiderDebug.log("reading", "novel fetch failed: %s", t.getMessage());
            return "";
        }
    }

    private static List<HkItem> parseItems(String chapterUrl, HkRule rule, String html) {
        List<HkItem> out = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            HkHomeParser.findList("novel", chapterUrl, rule, rule.getFindRule(), html, 1,
                    new HashMap<>(), new HkHomeParser.Callback() {
                        @Override
                        public void bindArrayToView(String key, List<HkItem> list) {
                            if (list != null) out.addAll(list);
                            latch.countDown();
                        }

                        @Override
                        public void error(String title, String msg, String code, Exception e) {
                            latch.countDown();
                        }
                    });
            latch.await(30, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 抽正文行：long_text / rich_text / text_* 取 title（文章页同字段渲染）。 */
    private static String extractText(List<HkItem> items) {
        StringBuilder sb = new StringBuilder();
        for (HkItem item : items) {
            if (item == null) continue;
            String type = item.getType();
            if (type == null) continue;
            if (!("long_text".equals(type) || "rich_text".equals(type) || type.startsWith("text_"))) {
                continue;
            }
            String t = item.getTitle();
            if (TextUtils.isEmpty(t)) t = item.getDesc();
            if (TextUtils.isEmpty(t)) continue;
            // 去残留标签（纯文本阅读器不做富文本）
            t = t.replaceAll("<[^>]+>", "").trim();
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(t);
        }
        return sb.toString().trim();
    }
}
