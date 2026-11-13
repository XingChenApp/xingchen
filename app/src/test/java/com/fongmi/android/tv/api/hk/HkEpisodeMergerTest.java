package com.fongmi.android.tv.api.hk;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * HkEpisodeMerger 防误并回归（任务书点名五类＋危险变体）。
 * 构造原则：样本先行——每一类先按形态构造，再断言判定；
 * 「应并」的只有同址节点组，「不许并」的形态宁可少并也不误并。
 */
public class HkEpisodeMergerTest {

    private static HkEpisodeMerger.Plan plan(int sel, List<String> urls, List<String> titles) {
        return HkEpisodeMerger.plan(urls, titles, sel);
    }

    private static String xoUrl(int zk, String fnBody) {
        return "hiker://empty#https://xozwdq9916450.xyz/video/play/987654.html?zk=" + zk
                + "@lazyRule=" + fnBody;
    }

    /** ① 本案原型：同址仅差 ?zk= 节点参数（XO 中文大全形态）应并，折成一集四线。 */
    @Test
    public void xoNodesMerge() {
        List<String> urls = Arrays.asList(
                xoUrl(0, "fnA?x=1&y=2"), xoUrl(1, "fnB"), xoUrl(2, "fnC"), xoUrl(3, "fnD"));
        List<String> titles = Arrays.asList(
                "▶ 播放地址1 · 高清加速节点", "▶ 播放地址2 · 刷新加速节点",
                "▶ 播放地址3 · 电信加速节点", "▶ 播放地址4 · 极速加速节点");
        HkEpisodeMerger.Plan p = plan(0, urls, titles);
        assertTrue(p.merge);
        assertEquals(1, p.groups.length);
        assertArrayEquals(new String[]{"高清加速节点", "刷新加速节点", "电信加速节点", "极速加速节点"}, p.lineNames);
        assertArrayEquals(new int[]{0, 1, 2, 3}, p.groups[0].members);
        assertArrayEquals(new int[]{0, 0, 0, 0}, p.episodeIndexOf); // 四条收缩成一集（显片名前提）
    }

    /** ① 变体：被点的是第 3 条（电信节点）→ 全局线路序旋转，被点地址居首设默认线路。 */
    @Test
    public void xoNodesMergeSelRotatesToFront() {
        List<String> urls = Arrays.asList(xoUrl(0, "a"), xoUrl(1, "b"), xoUrl(2, "c"), xoUrl(3, "d"));
        List<String> titles = Arrays.asList(
                "▶ 播放地址1 · 高清加速节点", "▶ 播放地址2 · 刷新加速节点",
                "▶ 播放地址3 · 电信加速节点", "▶ 播放地址4 · 极速加速节点");
        HkEpisodeMerger.Plan p = plan(2, urls, titles);
        assertTrue(p.merge);
        assertArrayEquals(new String[]{"电信加速节点", "极速加速节点", "高清加速节点", "刷新加速节点"}, p.lineNames);
        assertArrayEquals(new int[]{2, 3, 0, 1}, p.groups[0].members);
    }

    /** ② 真多集：各集编号在路径里不同，不许并（序号口径原样下游处理）。 */
    @Test
    public void realEpisodesInPathNotMerged() {
        List<String> urls = Arrays.asList(
                "https://s.example/play/12345-1-1.html",
                "https://s.example/play/12345-1-2.html",
                "https://s.example/play/12345-1-3.html");
        List<String> titles = Arrays.asList("第1集", "第2集", "第3集");
        assertFalse(plan(0, urls, titles).merge);
    }

    /** ② 附带（91 短剧型）：各集是不同页面地址（hiker://empty# 各集 id 不同），不许并。 */
    @Test
    public void shortDramaDistinctPagesNotMerged() {
        java.util.ArrayList<String> urls = new java.util.ArrayList<>();
        java.util.ArrayList<String> titles = new java.util.ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            urls.add("hiker://empty#ignoreVideo=true#https://s.example/play/" + (8800 + i) + ".html@lazyRule=f" + i);
            titles.add("第" + i + "集");
        }
        assertFalse(plan(0, urls, titles).merge);
    }

    /** ③ 同页 ?ep=N 分集型（报告点名：键集合形态与节点型相同）——键词表否决，不许并。 */
    @Test
    public void epParamNotMergedByKey() {
        List<String> urls = Arrays.asList(
                "https://s.example/video/play/987654.html?ep=1",
                "https://s.example/video/play/987654.html?ep=2",
                "https://s.example/video/play/987654.html?ep=3");
        List<String> titles = Arrays.asList(
                "播放地址1 · 高清节点", "播放地址2 · 刷新节点", "播放地址3 · 电信节点");
        assertFalse(plan(0, urls, titles).merge);
    }

    /** ③ 变体（最危险）：键名换成中性 zk，但标题是「第N集」分集形态——标题保险否决。 */
    @Test
    public void epTitlesNotMergedByTitle() {
        List<String> urls = Arrays.asList(
                xoUrl(0, "a"),
                "hiker://empty#https://xozwdq9916450.xyz/video/play/987654.html?zk=1@lazyRule=b",
                "hiker://empty#https://xozwdq9916450.xyz/video/play/987654.html?zk=2@lazyRule=c");
        List<String> titles = Arrays.asList("第1集", "第2集", "第3集");
        assertFalse(plan(0, urls, titles).merge);
    }

    /** ③ 变体二：路径带同一内容编号、仅差内容 id 参数（?id= 不同＝不同内容），靠 id 键表否决。 */
    @Test
    public void idParamNotMerged() {
        List<String> urls = Arrays.asList(
                "https://s.example/video/play/987654.html?id=111",
                "https://s.example/video/play/987654.html?id=222");
        List<String> titles = Arrays.asList("地址1 · 节点甲", "地址2 · 节点乙");
        assertFalse(plan(0, urls, titles).merge);
    }

    /** ③ 变体三：仅差会话 token（值随会话变的同址），不许并。 */
    @Test
    public void tokenParamNotMerged() {
        List<String> urls = Arrays.asList(
                "https://s.example/video/play/987654.html?token=aaa",
                "https://s.example/video/play/987654.html?token=bbb");
        List<String> titles = Arrays.asList("地址1 · 节点甲", "地址2 · 节点乙");
        assertFalse(plan(0, urls, titles).merge);
    }

    /** ④ 单集口径衔接：源没给标题的同址组（标题全空）按保守口径不并——
     *  归并只接管能提取节点名的组，无名集的显名/编号由下游既有口径处理。 */
    @Test
    public void unnamedSameBodyNotMerged() {
        List<String> urls = Arrays.asList(xoUrl(0, "a"), xoUrl(1, "b"));
        List<String> titles = Arrays.asList("", "");
        assertFalse(plan(0, urls, titles).merge);
    }

    /** ⑤ 多集无名 01/02 口径：无名真多集（路径分集、无标题）不被归并触碰。 */
    @Test
    public void unnamedRealEpisodesNotMerged() {
        List<String> urls = Arrays.asList(
                "https://s.example/play/12345-2-1.html",
                "https://s.example/play/12345-2-2.html");
        List<String> titles = Arrays.asList("", "");
        HkEpisodeMerger.Plan p = plan(0, urls, titles);
        assertFalse(p.merge);
        assertArrayEquals(new int[]{0, 1}, p.episodeIndexOf); // 恒等映射，下游照旧编 01/02
    }

    /** 形态一：本体全等、仅 lazyRule 函数体不同，应并。 */
    @Test
    public void bodyIdenticalDifferentLazyRuleMerged() {
        String base = "hiker://empty#https://s.example/video/play/555111.html";
        List<String> urls = Arrays.asList(base + "@lazyRule=fnA", base + "@lazyRule=fnB");
        List<String> titles = Arrays.asList("地址一 · 线路甲", "地址二 · 线路乙");
        HkEpisodeMerger.Plan p = plan(0, urls, titles);
        assertTrue(p.merge);
        assertArrayEquals(new String[]{"线路甲", "线路乙"}, p.lineNames);
    }

    /** 混杂：真多集两集＋一组同址节点，组收缩、真集保位。 */
    @Test
    public void mixedPartialGroup() {
        List<String> urls = Arrays.asList(
                "https://s.example/play/12345-1-1.html",
                xoUrl(0, "a"), xoUrl(1, "b"),
                "https://s.example/play/12345-1-2.html");
        List<String> titles = Arrays.asList(
                "第1集", "▶ 播放地址1 · 高清加速节点", "▶ 播放地址2 · 刷新加速节点", "第2集");
        HkEpisodeMerger.Plan p = plan(0, urls, titles);
        assertTrue(p.merge);
        assertEquals(1, p.groups.length);
        // 集序：第1集=0、节点组收缩=1、第2集=2
        assertArrayEquals(new int[]{0, 1, 1, 2}, p.episodeIndexOf);
        assertArrayEquals(new String[]{"高清加速节点", "刷新加速节点"}, p.lineNames);
    }

    /** 多组同构：两部片各两节点、节点名同构 → 全并。 */
    @Test
    public void twoGroupsIsomorphicMerged() {
        List<String> urls = Arrays.asList(
                "hiker://empty#https://s.example/video/play/111.html?zk=0@lazyRule=a",
                "hiker://empty#https://s.example/video/play/111.html?zk=1@lazyRule=b",
                "hiker://empty#https://s.example/video/play/222.html?zk=0@lazyRule=c",
                "hiker://empty#https://s.example/video/play/222.html?zk=1@lazyRule=d");
        List<String> titles = Arrays.asList(
                "地址1 · 节点甲", "地址2 · 节点乙", "地址1 · 节点甲", "地址2 · 节点乙");
        HkEpisodeMerger.Plan p = plan(-1, urls, titles);
        assertTrue(p.merge);
        assertEquals(2, p.groups.length);
        assertArrayEquals(new int[]{0, 0, 1, 1}, p.episodeIndexOf);
    }

    /** 多组不同构：第二组节点名不同 → 整盘不并（下游线路模型要求同构）。 */
    @Test
    public void twoGroupsNonIsomorphicNotMerged() {
        List<String> urls = Arrays.asList(
                "hiker://empty#https://s.example/video/play/111.html?zk=0@lazyRule=a",
                "hiker://empty#https://s.example/video/play/111.html?zk=1@lazyRule=b",
                "hiker://empty#https://s.example/video/play/222.html?zk=0@lazyRule=c",
                "hiker://empty#https://s.example/video/play/222.html?zk=1@lazyRule=d");
        List<String> titles = Arrays.asList(
                "地址1 · 节点甲", "地址2 · 节点乙", "地址1 · 线路丙", "地址2 · 线路丁");
        assertFalse(plan(-1, urls, titles).merge);
    }

    /** 磁力条目不参与：混在列表里既不并、也不挡其它组归并；纯磁力不成组。 */
    @Test
    public void magnetUntouched() {
        List<String> urls = Arrays.asList(
                "magnet:?xt=urn:btih:ABCDEF123456",
                xoUrl(0, "a"), xoUrl(1, "b"));
        List<String> titles = Arrays.asList(
                "片名 1080p", "▶ 播放地址1 · 高清加速节点", "▶ 播放地址2 · 刷新加速节点");
        HkEpisodeMerger.Plan p = plan(1, urls, titles);
        assertTrue(p.merge);
        assertArrayEquals(new int[]{0, 1, 1}, p.episodeIndexOf);
        List<String> mags = Arrays.asList(
                "magnet:?xt=urn:btih:ABCDEF123456", "magnet:?xt=urn:btih:ABCDEF123456");
        assertFalse(plan(0, mags, Arrays.asList("a", "b")).merge);
    }

    /**
     * 调用点判定输入归一化（与 HkListActivity.mergeJudgeUrlForChapter 同口径）：
     * 每章 originalUrl 优先、为空回退现行地址。混形态样本用它组装判定输入。
     */
    private static java.util.List<String> judgeUrls(java.util.List<String> originals,
                                                    java.util.List<String> currents) {
        java.util.List<String> out = new java.util.ArrayList<>(currents.size());
        for (int i = 0; i < currents.size(); i++) {
            String o = originals.get(i);
            out.add(o != null && !o.isEmpty() ? o : currents.get(i));
        }
        return out;
    }

    private static java.util.List<String> xoTitles() {
        return Arrays.asList(
                "▶ 播放地址1 · 高清加速节点", "▶ 播放地址2 · 刷新加速节点",
                "▶ 播放地址3 · 电信加速节点", "▶ 播放地址4 · 极速加速节点");
    }

    private static java.util.List<String> xoOriginals() {
        return Arrays.asList(xoUrl(0, "fnA"), xoUrl(1, "fnB"), xoUrl(2, "fnC"), xoUrl(3, "fnD"));
    }

    /**
     * 混形态（1.4.18 真机案，A 型）：被点条（地址1/高清）已被懒解析换成异 host 的
     * 干净 m3u8 纯地址，其余三条仍是原始包装地址。混形态直喂判定件＝倒置签名
     * （2 集：被点条单例＋其余三条折成一集挂 3 线）；经 originalUrl 归一化后
     * 恢复原始形态，应并成 1 集 × 4 线、默认线＝被点的高清节点。
     * 收缩为 1 集后，下游按既有单集口径显片名全名（与 xoNodesMerge 同前提）。
     */
    @Test
    public void mixedResolvedTypeA_mergesAfterNormalization() {
        java.util.List<String> originals = xoOriginals();
        java.util.List<String> currents = Arrays.asList(
                "https://cdn9.example.net/v/987654/index.m3u8",
                originals.get(1), originals.get(2), originals.get(3));
        // 污染形态的倒置签名（法医第三节纸面执行结果）：2 集、高清缺位
        HkEpisodeMerger.Plan polluted = plan(0, currents, xoTitles());
        assertTrue(polluted.merge);
        assertArrayEquals(new int[]{0, 1, 1, 1}, polluted.episodeIndexOf);
        assertArrayEquals(new String[]{"刷新加速节点", "电信加速节点", "极速加速节点"}, polluted.lineNames);
        // 归一化后：1 集 × 4 线，被点节点居默认线
        HkEpisodeMerger.Plan p = plan(0, judgeUrls(originals, currents), xoTitles());
        assertTrue(p.merge);
        assertEquals(1, p.groups.length);
        assertArrayEquals(new int[]{0, 1, 2, 3}, p.groups[0].members);
        assertArrayEquals(new String[]{"高清加速节点", "刷新加速节点", "电信加速节点", "极速加速节点"}, p.lineNames);
        assertArrayEquals(new int[]{0, 0, 0, 0}, p.episodeIndexOf);
    }

    /**
     * 混形态（兜底型）：被点条的懒解析回的是同 host 同路径的干净地址（?zk= 已剥、
     * 无 @lazyRule=），键集合为空与兄弟条 {zk} 不同桶，同样劈成 2 集；
     * 归一化后并成 1 集 × 4 线。样本同时让第 4 章 originalUrl 为空，验回退
     * 现行地址（其现行值本就是包装形态）不崩、不影响归并。
     */
    @Test
    public void mixedResolvedFallback_mergesAfterNormalization() {
        java.util.List<String> originals = xoOriginals();
        java.util.List<String> currents = Arrays.asList(
                "https://xozwdq9916450.xyz/video/play/987654.html",
                originals.get(1), originals.get(2), originals.get(3));
        HkEpisodeMerger.Plan polluted = plan(0, currents, xoTitles());
        assertTrue(polluted.merge);
        assertArrayEquals(new int[]{0, 1, 1, 1}, polluted.episodeIndexOf);
        java.util.List<String> stored = Arrays.asList(
                originals.get(0), originals.get(1), originals.get(2), "");
        java.util.List<String> judge = judgeUrls(stored, currents);
        assertEquals(currents.get(3), judge.get(3)); // originalUrl 为空 → 回退现行值
        HkEpisodeMerger.Plan p = plan(0, judge, xoTitles());
        assertTrue(p.merge);
        assertEquals(1, p.groups.length);
        assertArrayEquals(new int[]{0, 1, 2, 3}, p.groups[0].members);
        assertArrayEquals(new String[]{"高清加速节点", "刷新加速节点", "电信加速节点", "极速加速节点"}, p.lineNames);
        assertArrayEquals(new int[]{0, 0, 0, 0}, p.episodeIndexOf);
    }

    /** 无查询的直链同址重复（两条一模一样的 http 地址、无 lazyRule）：名可提取时并。 */
    @Test
    public void plainDuplicateUrlsMerged() {
        List<String> urls = Arrays.asList(
                "https://s.example/video/play/987654.html",
                "https://s.example/video/play/987654.html");
        List<String> titles = Arrays.asList("播放 · 主线", "播放 · 备线");
        HkEpisodeMerger.Plan p = plan(0, urls, titles);
        assertTrue(p.merge);
        assertArrayEquals(new String[]{"主线", "备线"}, p.lineNames);
    }
}
