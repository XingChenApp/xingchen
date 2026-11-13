package com.fongmi.android.tv.api.hk;

import android.os.Environment;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Prefers;

import java.io.File;

/**
 * 海阔图片毒缓存一次性清理（hk-cover-still-missing.md 定案）：解密断链的旧版上，
 * 规则先把 .enc 密文下载落进 hiker://files/cache/ 再解密、解密抛错后密文文件留下；
 * 此后规则见缓存非空就直读 file://，升级修好解密也轮不到执行，封面永久空白。
 * 同型风险对所有海阔规则成立：任何在坏版本期间落盘的缓存图都可能是坏的。
 * 跨版本首次启动时把该 cache 目录清空一次，让规则重新下载、重新解密。
 * 只清 hiker files 根直属的 cache/ 一个目录，规则本体、存档、登录态、下载文件一律不碰。
 *
 * <p>同批追加（漫画黑屏复核报告 §7-2）：阅读器漫画章节图片缓存是另一个目录
 * （filesDir/reading_cache/&lt;书&gt;/c_&lt;章&gt;，ReadingDownload 落盘），同型毒化——
 * 坏版本按旧口径抓图落盘（@js= 图落密文原字节），阅读器命中缓存即绕过已修组装链。
 * 同一次清理只删 c_ 章节目录（图片缓存），小说正文 n_*.txt、书目录结构、阅读
 * 进度/存档/书签（不在此目录）一律不碰。</p>
 */
public final class HkCachePurge {

    // 全 App 的「上次运行版本号」记录点：此前树内无此记录，立此一处，Prefers 统一存储。
    private static final String KEY_LAST_RUN_CODE = "app_last_run_code";
    // 本迁移已在哪个版本号上触发过：同版本重启不重复清。
    private static final String KEY_PURGED_FOR_CODE = "hk_img_cache_purged_for_code";

    private HkCachePurge() {
    }

    /**
     * App.onCreate 主进程段调用。判断只读两个 int；清理动作丢后台线程；
     * 全程吞异常、只留一行日志，任何失败都不许影响启动。
     */
    public static void onStartup() {
        try {
            int current = BuildConfig.VERSION_CODE;
            int last = Prefers.getInt(KEY_LAST_RUN_CODE, 0);
            if (last == current) return; // 同版本重启：不动
            // 先更新版本记录：从这一次起，「上次版本」就是本版
            Prefers.put(KEY_LAST_RUN_CODE, current);
            if (last <= 0 || last > current) return; // 首装无旧缓存可清；降级不在本迁移口径内
            if (Prefers.getInt(KEY_PURGED_FOR_CODE, 0) == current) return; // 本版已清过
            // 触发即落标记：保证同版本内只清这一次；清理若整体抛错，purge 内回滚标记待下版再试
            Prefers.put(KEY_PURGED_FOR_CODE, current);
            Task.execute(HkCachePurge::purge);
        } catch (Throwable ignored) {
        }
    }

    private static void purge() {
        int deleted = 0;
        boolean done = false;
        try {
            File root = hikerFilesRoot();
            if (root == null) return;
            File cache = new File(root, "cache");
            // 防呆：目标必须恰好是 files 根直属、名为 cache 的目录，路径对不上绝不动手
            if (!"cache".equals(cache.getName())) return;
            File parent = cache.getParentFile();
            if (parent == null || !parent.getCanonicalPath().equals(root.getCanonicalPath())) return;
            if (cache.isDirectory()) {
                deleted = deleteChildren(cache);
            }
            // 目录不存在 = 无毒可清，不中断：继续阶段二后统一置完成。
            // 阶段二：阅读器漫画章节图片缓存（与阶段一不是同一目录，见类注释）。
            deleted += purgeComicChapterCaches();
            done = true;
        } catch (Throwable ignored) {
        } finally {
            if (!done) {
                // 整体失败（连目录都没摸到）：回滚标记，下一个版本再试，不在本版本内反复折腾
                try {
                    Prefers.put(KEY_PURGED_FOR_CODE, 0);
                } catch (Throwable ignored) {
                }
            }
            try {
                SpiderDebug.log("hkpurge", "hk image cache purge deleted=%s done=%s", deleted, done);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 阶段二：清 ReadingDownload 的漫画章节图片缓存。
     * 目录口径与 ReadingDownload.root/comicDir 逐字对齐：filesDir/reading_cache
     * 直属各书目录、书目录直属 c_<章下标> 目录是章节图片缓存（含 manifest）。
     * 防呆同阶段一：根目录名与 canonical 父路径双核对，对不上绝不动手；
     * 只删 c_ 开头的目录，小说正文文件与其余一切不碰。整体吞异常、返回删除数。
     */
    private static int purgeComicChapterCaches() {
        int count = 0;
        try {
            File filesDir = App.get().getFilesDir();
            if (filesDir == null) return 0;
            File root = new File(filesDir, "reading_cache");
            if (!"reading_cache".equals(root.getName())) return 0;
            File parent = root.getParentFile();
            if (parent == null || !parent.getCanonicalPath().equals(filesDir.getCanonicalPath())) return 0;
            if (!root.isDirectory()) return 0; // 无缓存 = 无毒可清
            File[] books = root.listFiles();
            if (books == null) return 0;
            for (File book : books) {
                try {
                    if (!book.isDirectory()) continue;
                    File[] chapters = book.listFiles();
                    if (chapters == null) continue;
                    for (File ch : chapters) {
                        try {
                            if (ch.isDirectory() && ch.getName().startsWith("c_")) {
                                count += deleteChildren(ch);
                                if (ch.delete()) count++;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return count;
    }

    // 与 ImgUtil / HkJsRuntime.getFilePath 同一口径：hiker://files 根 = 外部 Documents 目录，
    // 取不到时回退内部 filesDir。
    private static File hikerFilesRoot() {
        try {
            File docs = App.get().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            return docs != null ? docs : App.get().getFilesDir();
        } catch (Throwable ignored) {
            return null;
        }
    }

    // 只删目录内容（含子目录），目录本身保留；单个文件失败不中断其余。
    private static int deleteChildren(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            try {
                if (f.isDirectory()) {
                    count += deleteChildren(f);
                    if (f.delete()) count++;
                } else if (f.delete()) {
                    count++;
                }
            } catch (Throwable ignored) {
            }
        }
        return count;
    }
}
