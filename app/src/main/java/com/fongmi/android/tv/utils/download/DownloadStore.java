package com.fongmi.android.tv.utils.download;

import com.fongmi.android.tv.bean.DownloadRecord;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.utils.Task;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 下载任务落盘的唯一封装（下载外壳件 1）：DownloadManager 只调本类，
 * 下载器不碰 Room。写全走 Task.execute 异步，读只供 restore 对账用
 * （调用方保证在后台线程）。
 */
public class DownloadStore {

    private static final Gson GSON = new Gson();
    private static final Type HEADERS_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    /** 任务 → 记录（全字段镜像） */
    public static DownloadRecord toRecord(DownloadTask task) {
        DownloadRecord r = new DownloadRecord();
        r.setId(task.getId());
        r.setUrl(task.getUrl());
        r.setName(task.getName());
        r.setFilePath(task.getFilePath());
        r.setStatus(task.getStatus());
        r.setTotalBytes(task.getTotalBytes());
        r.setDownloadedBytes(task.getDownloadedBytes());
        r.setM3u8(task.isM3u8());
        r.setCreateTime(task.getCreateTime());
        r.setFinishTime(task.getFinishTime());
        r.setLastPlayTime(task.getLastPlayTime());
        r.setHeadersJson(task.getHeaders() == null ? null : GSON.toJson(task.getHeaders()));
        r.setPic(task.getPic());
        r.setQualityName(task.getQualityName());
        r.setError(task.getError());
        r.setVariantBandwidth(task.getVariantBandwidth());
        return r;
    }

    /** 记录 → 任务（状态翻转口径在 DownloadManager.restore 里做，这里只搬字段） */
    public static DownloadTask toTask(DownloadRecord r) {
        DownloadTask task = new DownloadTask(r.getId(), r.getUrl(), r.getName(), r.getCreateTime());
        task.setFilePath(r.getFilePath());
        task.setStatus(r.getStatus());
        task.setTotalBytes(r.getTotalBytes());
        task.setDownloadedBytes(r.getDownloadedBytes());
        task.setM3u8(r.isM3u8());
        task.setFinishTime(r.getFinishTime());
        task.setLastPlayTime(r.getLastPlayTime());
        task.setPic(r.getPic());
        task.setQualityName(r.getQualityName());
        task.setError(r.getError());
        task.setVariantBandwidth(r.getVariantBandwidth());
        if (r.getHeadersJson() != null && !r.getHeadersJson().isEmpty()) {
            try {
                Map<String, String> headers = GSON.fromJson(r.getHeadersJson(), HEADERS_TYPE);
                if (headers != null) task.setHeaders(headers);
            } catch (Exception ignored) {
                // 请求头解析坏了不拦恢复：任务照常回来，续传没头可能 403，
                // 错误会经下载器自己的报错口可见，不静默拼错文件。
            }
        }
        return task;
    }

    /** 异步落盘一条（insertOrUpdate） */
    public static void save(DownloadTask task) {
        DownloadRecord record = toRecord(task);
        Task.execute(() -> {
            try {
                AppDatabase.get().getDownloadRecordDao().insertOrUpdate(record);
            } catch (Exception ignored) {
                // 落盘失败不许拖垮下载本身：内存表仍是当下真源，
                // 下次状态迁移会再写一次。
            }
        });
    }

    /** 异步直存一条现成记录（孤儿收编补建用） */
    public static void saveRecord(DownloadRecord record) {
        Task.execute(() -> {
            try {
                AppDatabase.get().getDownloadRecordDao().insertOrUpdate(record);
            } catch (Exception ignored) {
            }
        });
    }

    /** 异步删一条记录 */
    public static void delete(String taskId) {
        Task.execute(() -> {
            try {
                AppDatabase.get().getDownloadRecordDao().deleteById(taskId);
            } catch (Exception ignored) {
            }
        });
    }

    /** 同步读全量（只许后台线程调，restore 对账用） */
    public static List<DownloadRecord> loadAll() {
        try {
            return AppDatabase.get().getDownloadRecordDao().findAll();
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /** 同步按 url 查一条（只许后台线程调） */
    public static DownloadRecord findByUrl(String url) {
        try {
            return AppDatabase.get().getDownloadRecordDao().findByUrl(url);
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<String, String> copyHeaders(Map<String, String> headers) {
        return headers == null ? null : new HashMap<>(headers);
    }
}
