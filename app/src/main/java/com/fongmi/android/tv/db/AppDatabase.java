package com.fongmi.android.tv.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.PlaybackDeleteTombstone;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.db.dao.ConfigDao;
import com.fongmi.android.tv.db.dao.DeviceDao;
import com.fongmi.android.tv.db.dao.HistoryDao;
import com.fongmi.android.tv.db.dao.KeepDao;
import com.fongmi.android.tv.db.dao.LiveDao;
import com.fongmi.android.tv.db.dao.PlaybackDeleteTombstoneDao;
import com.fongmi.android.tv.db.dao.SiteDao;
import com.fongmi.android.tv.db.dao.TrackDao;
import com.fongmi.android.tv.utils.AppBackup;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Database(entities = {Keep.class, Site.class, Live.class, Track.class, Config.class, Device.class, History.class, PlaybackDeleteTombstone.class}, version = AppDatabase.VERSION)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 45;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase get() {
        if (instance == null) {
            // Application 未构造就取库是调用时序错误：给可诊断的异常，不裸 NPE 在 Room 内部
            if (App.get() == null) throw new IllegalStateException("AppDatabase requested before Application is created");
            instance = create(App.get());
        }
        return instance;
    }

    public static void backup() {
        backup(new com.fongmi.android.tv.impl.Callback());
    }

    public static void backup(com.fongmi.android.tv.impl.Callback callback) {
        backup(callback, null);
    }

    public static void backup(com.fongmi.android.tv.impl.Callback callback, AppBackup.Progress progress) {
        Task.execute(() -> {
            File file = new File(Path.tv(), AppBackup.fileName());
            try {
                AppBackup.CreateResult result = AppBackup.create(file, progress);
                // 任一回备份成功即兑现销毁待办（宿主批刀3）：手动
                // 备份与待办补跑同走本口，清旗只在此成功路，失败
                // 留旗待下次回前台再补。
                AppBackup.clearPendingBackup();
                App.post(() -> {
                    callback.success();
                    if (result.hasWarning()) Notify.show(result.warning);
                });
                cleanOld();
            } catch (Exception e) {
                SpiderDebug.log("backup", "local create failed error=%s", e.getMessage());
                App.post(callback::error);
            }
        });
    }

    public static void restore(File file, com.fongmi.android.tv.impl.Callback callback) {
        restore(file, callback, null);
    }

    public static void restore(File file, com.fongmi.android.tv.impl.Callback callback, AppBackup.Progress progress) {
        Task.execute(() -> {
            try {
                AppBackup.RestoreResult result = AppBackup.restore(file, progress);
                App.post(() -> {
                    callback.success();
                    if (result.hasWarning()) Notify.show(result.warning);
                });
            } catch (Exception e) {
                SpiderDebug.log("backup", "local restore failed file=%s error=%s", file == null ? "" : file.getAbsolutePath(), e.getMessage());
                App.post(callback::error);
            }
        });
    }

    private static void cleanOld() {
        List<File> items = new ArrayList<>();
        File[] files = Path.tv().listFiles();
        if (files == null) files = new File[0];
        for (File file : files) if (AppBackup.isBackup(file)) items.add(file);
        if (!items.isEmpty()) items.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        if (items.size() > 7) for (int i = 7; i < items.size(); i++) Path.clear(items.get(i));
    }

    private static AppDatabase create(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(Migrations.MIGRATION_1_2)
                .addMigrations(Migrations.MIGRATION_2_3)
                .addMigrations(Migrations.MIGRATION_3_4)
                .addMigrations(Migrations.MIGRATION_4_5)
                .addMigrations(Migrations.MIGRATION_5_6)
                .addMigrations(Migrations.MIGRATION_6_7)
                .addMigrations(Migrations.MIGRATION_7_8)
                .addMigrations(Migrations.MIGRATION_8_9)
                .addMigrations(Migrations.MIGRATION_9_10)
                .addMigrations(Migrations.MIGRATION_10_11)
                .addMigrations(Migrations.MIGRATION_11_12)
                .addMigrations(Migrations.MIGRATION_12_13)
                .addMigrations(Migrations.MIGRATION_13_14)
                .addMigrations(Migrations.MIGRATION_14_15)
                .addMigrations(Migrations.MIGRATION_15_16)
                .addMigrations(Migrations.MIGRATION_16_17)
                .addMigrations(Migrations.MIGRATION_17_18)
                .addMigrations(Migrations.MIGRATION_18_19)
                .addMigrations(Migrations.MIGRATION_19_20)
                .addMigrations(Migrations.MIGRATION_20_21)
                .addMigrations(Migrations.MIGRATION_21_22)
                .addMigrations(Migrations.MIGRATION_22_23)
                .addMigrations(Migrations.MIGRATION_23_24)
                .addMigrations(Migrations.MIGRATION_24_25)
                .addMigrations(Migrations.MIGRATION_25_26)
                .addMigrations(Migrations.MIGRATION_26_27)
                .addMigrations(Migrations.MIGRATION_27_28)
                .addMigrations(Migrations.MIGRATION_28_29)
                .addMigrations(Migrations.MIGRATION_29_30)
                .addMigrations(Migrations.MIGRATION_30_31)
                .addMigrations(Migrations.MIGRATION_31_32)
                .addMigrations(Migrations.MIGRATION_32_33)
                .addMigrations(Migrations.MIGRATION_33_34)
                .addMigrations(Migrations.MIGRATION_34_35)
                .addMigrations(Migrations.MIGRATION_35_36)
                .addMigrations(Migrations.MIGRATION_36_37)
                .addMigrations(Migrations.MIGRATION_37_38)
                .addMigrations(Migrations.MIGRATION_38_39)
                .addMigrations(Migrations.MIGRATION_39_40)
                .addMigrations(Migrations.MIGRATION_40_41)
                .addMigrations(Migrations.MIGRATION_41_42)
                .addMigrations(Migrations.MIGRATION_42_43)
                .addMigrations(Migrations.MIGRATION_43_44)
                .addMigrations(Migrations.MIGRATION_44_45)
                .allowMainThreadQueries().build();
    }

    public abstract KeepDao getKeepDao();

    public abstract SiteDao getSiteDao();

    public abstract LiveDao getLiveDao();

    public abstract TrackDao getTrackDao();

    public abstract ConfigDao getConfigDao();

    public abstract DeviceDao getDeviceDao();

    public abstract HistoryDao getHistoryDao();

    public abstract PlaybackDeleteTombstoneDao getPlaybackDeleteTombstoneDao();




}
