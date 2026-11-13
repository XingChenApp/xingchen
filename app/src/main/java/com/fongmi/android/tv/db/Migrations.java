package com.fongmi.android.tv.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class Migrations {

    // 1→30 段（CORE-24 补链）：上游在 v30 之前从未给这段留完整迁移链，
    // 咱家此前靠破坏性回落整库销毁过关。本段按上游
    // 逐版实体 diff 重构（台账 fm_schema_A/B），当年上游写过原文的步骤
    // （5_6 起多条）照原文；破坏性时代无原文的按 Room 实体定义正形写。
    // 全链已本地回放验证：v1 建库起逐条回放到 45，35/36/37 三个检查点
    // 与仓库内 Room 导出 schema 逐表逐列对齐，样本行全程不丢。

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN vodFlag TEXT");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN opening INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE History ADD COLUMN ending INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 无 schema 变更：本版只改 History.key 的拼接分隔符（_ → @@@），
            // 表结构一字未动；空迁移占位，保证 3→4 有正路可走。
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `Config` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL, `url` TEXT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Config_url` ON `Config` (`url`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `Site` (`key` TEXT NOT NULL, `name` TEXT, `searchable` INTEGER, `filterable` INTEGER, `activated` INTEGER NOT NULL, PRIMARY KEY(`key`))");
            database.execSQL("ALTER TABLE History ADD COLUMN cid INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN revSort INTEGER DEFAULT 0 NOT NULL");
            database.execSQL("ALTER TABLE History ADD COLUMN revPlay INTEGER DEFAULT 0 NOT NULL");
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN position INTEGER DEFAULT 0 NOT NULL");
            // 上游原版数据语义：旧版 duration 兼作播放位置，拆列时把
            // 存量位置搬进 position、duration 清零（之后由播放链重写）。
            database.execSQL("UPDATE History SET position = duration");
            database.execSQL("UPDATE History SET duration = 0");
        }
    };

    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Config ADD COLUMN json TEXT DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `Keep` (`key` TEXT NOT NULL, `siteName` TEXT, `vodName` TEXT, `vodPic` TEXT, `createTime` INTEGER NOT NULL, `cid` INTEGER NOT NULL, PRIMARY KEY(`key`))");
        }
    };

    public static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 无 schema 变更；上游原版数据订正：片尾设置全表清零。
            database.execSQL("UPDATE `History` SET ending = 0");
        }
    };

    public static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 无 schema 变更（本版 History 只改合并方法）；空迁移占位。
        }
    };

    public static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Config ADD COLUMN type INTEGER DEFAULT 0 NOT NULL");
            database.execSQL("ALTER TABLE Config ADD COLUMN home TEXT DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Keep ADD COLUMN type INTEGER DEFAULT 0 NOT NULL");
        }
    };

    public static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 唯一索引由 url 单列扩为 (url, type)：此前 url 本就全局
            // 唯一，复合键必然不冲突，存量行一行不丢。
            database.execSQL("DROP INDEX IF EXISTS index_Config_url");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Config_url_type ON Config(url, type)");
        }
    };

    public static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN scale INTEGER DEFAULT -1 NOT NULL");
        }
    };

    public static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN speed REAL DEFAULT 1 NOT NULL");
            database.execSQL("ALTER TABLE History ADD COLUMN player INTEGER DEFAULT -1 NOT NULL");
        }
    };

    public static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `Track` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `group` INTEGER NOT NULL, `track` INTEGER NOT NULL, `player` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `selected` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Track_key_player_type` ON `Track` (`key`, `player`, `type`)");
        }
    };

    public static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Config ADD COLUMN parse TEXT DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Site ADD COLUMN changeable INTEGER DEFAULT 1");
        }
    };

    public static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Config ADD COLUMN name TEXT DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `Device` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `uuid` TEXT, `name` TEXT, `ip` TEXT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Device_uuid_name` ON `Device` (`uuid`, `name`)");
        }
    };

    public static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Device ADD COLUMN type INTEGER DEFAULT 0 NOT NULL");
        }
    };

    public static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 无 schema 变更；上游原版数据订正：播放器编号 0 的存量
            // 历史改记为 2。（上游同版迁移里还有一处播放偏好订正，
            // 属 SharedPreferences 不是库内数据，本链不搬，见台账记名。）
            database.execSQL("UPDATE History SET player = 2 WHERE player = 0");
        }
    };

    public static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Track ADD COLUMN `adaptive` INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Site ADD COLUMN recordable INTEGER DEFAULT 1");
        }
    };

    public static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 上游原版重建：Site 删 filterable 列（activated 同版改判
            // 不入表，一并随重建落掉）；存量行按保留列全量搬运。
            database.execSQL("CREATE TABLE Site_Backup (`key` TEXT NOT NULL, name TEXT, searchable INTEGER, changeable INTEGER, recordable INTEGER, PRIMARY KEY (`key`))");
            database.execSQL("INSERT INTO Site_Backup SELECT `key`, name, searchable, changeable, recordable FROM Site");
            database.execSQL("DROP TABLE Site");
            database.execSQL("ALTER TABLE Site_Backup RENAME TO Site");
        }
    };

    public static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `Live` (`name` TEXT NOT NULL, `boot` INTEGER NOT NULL, `pass` INTEGER NOT NULL, PRIMARY KEY(`name`))");
        }
    };

    public static final Migration MIGRATION_27_28 = new Migration(27, 28) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 无 schema 变更（上游本版只清了一项播放偏好）；空迁移占位。
        }
    };

    public static final Migration MIGRATION_28_29 = new Migration(28, 29) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 上游原版重建：Site 删 recordable 与 name 两列（name 改
            // @Ignore 不入表）；存量行按保留列全量搬运。
            database.execSQL("CREATE TABLE Site_Backup (`key` TEXT NOT NULL, searchable INTEGER, changeable INTEGER, PRIMARY KEY (`key`))");
            database.execSQL("INSERT INTO Site_Backup SELECT `key`, searchable, changeable FROM Site");
            database.execSQL("DROP TABLE Site");
            database.execSQL("ALTER TABLE Site_Backup RENAME TO Site");
        }
    };

    public static final Migration MIGRATION_29_30 = new Migration(29, 30) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Config ADD COLUMN logo TEXT DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_30_31 = new Migration(30, 31) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE Track");
            database.execSQL("CREATE TABLE Track (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `group` INTEGER NOT NULL, `track` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `selected` INTEGER NOT NULL, `adaptive` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Track_key_type` ON `Track` (`key`, `type`)");
        }
    };

    public static final Migration MIGRATION_31_32 = new Migration(31, 32) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE History_Backup (`key` TEXT NOT NULL, `vodPic` TEXT, `vodName` TEXT, `vodFlag` TEXT, `vodRemarks` TEXT, `episodeUrl` TEXT, `revSort` INTEGER NOT NULL, `revPlay` INTEGER NOT NULL, `createTime` INTEGER NOT NULL, `opening` INTEGER NOT NULL, `ending` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `speed` REAL NOT NULL, `scale` INTEGER NOT NULL, `cid` INTEGER NOT NULL, PRIMARY KEY(`key`))");
            database.execSQL("INSERT INTO History_Backup SELECT `key`, `vodPic`, `vodName`, `vodFlag`, `vodRemarks`, `episodeUrl`, `revSort`, `revPlay`, `createTime`, `opening`, `ending`, `position`, `duration`, `speed`, `scale`, `cid` FROM History");
            database.execSQL("DROP TABLE History");
            database.execSQL("ALTER TABLE History_Backup RENAME to History");
        }
    };

    public static final Migration MIGRATION_32_33 = new Migration(32, 33) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Live ADD COLUMN keep TEXT DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_33_34 = new Migration(33, 34) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE Track");
            database.execSQL("CREATE TABLE Track (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `group` INTEGER NOT NULL, `track` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `selected` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Track_key_type` ON `Track` (`key`, `type`)");
        }
    };

    public static final Migration MIGRATION_34_35 = new Migration(34, 35) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE Track");
            database.execSQL("CREATE TABLE Track (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `format` TEXT, `selected` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Track_key_type` ON `Track` (`key`, `type`)");
        }
    };

    public static final Migration MIGRATION_35_36 = new Migration(35, 36) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN wallPic TEXT DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_36_37 = new Migration(36, 37) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS PlaybackDeleteTombstone (`id` TEXT NOT NULL, `configKey` TEXT NOT NULL, `scope` TEXT NOT NULL, `historyKey` TEXT NOT NULL, `siteKey` TEXT NOT NULL, `vodId` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_PlaybackDeleteTombstone_deletedAt` ON `PlaybackDeleteTombstone` (`deletedAt`)");
        }
    };

    public static final Migration MIGRATION_37_38 = new Migration(37, 38) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS MusicFavorite (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackId` TEXT NOT NULL, `title` TEXT, `artist` TEXT, `album` TEXT, `duration` INTEGER NOT NULL, `cover` TEXT, `createTime` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_MusicFavorite_trackId` ON `MusicFavorite` (`trackId`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS MusicPlaylist (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `createTime` INTEGER NOT NULL, `updateTime` INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS MusicPlaylistItem (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `trackId` TEXT, `title` TEXT, `artist` TEXT, `album` TEXT, `duration` INTEGER NOT NULL, `cover` TEXT, `position` INTEGER NOT NULL, `createTime` INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_MusicPlaylistItem_playlistId` ON `MusicPlaylistItem` (`playlistId`)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_MusicPlaylistItem_playlistId_trackId` ON `MusicPlaylistItem` (`playlistId`, `trackId`)");
        }
    };

    public static final Migration MIGRATION_38_39 = new Migration(38, 39) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS MusicDownload (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackId` TEXT NOT NULL, `title` TEXT, `artist` TEXT, `album` TEXT, `duration` INTEGER NOT NULL, `cover` TEXT, `filePath` TEXT, `createTime` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_MusicDownload_trackId` ON `MusicDownload` (`trackId`)");
        }
    };

    public static final Migration MIGRATION_39_40 = new Migration(39, 40) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 歌单/收藏存源信息（platform＋rawJson），点播才不丢源；旧行留空=未知来源
            database.execSQL("ALTER TABLE MusicFavorite ADD COLUMN platform TEXT");
            database.execSQL("ALTER TABLE MusicFavorite ADD COLUMN rawJson TEXT");
            database.execSQL("ALTER TABLE MusicPlaylistItem ADD COLUMN platform TEXT");
            database.execSQL("ALTER TABLE MusicPlaylistItem ADD COLUMN rawJson TEXT");
        }
    };

    public static final Migration MIGRATION_40_41 = new Migration(40, 41) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Keep 主键由单 key 换复合 (key, cid, type)：同名收藏跨配置/跨类型互覆的根治（H14）。
            // SQLite 改主键只能重建表。旧表 key 本就唯一，(key, cid, type) 必然不冲突，
            // INSERT 全量直搬，旧收藏一行不丢、不改值；同名不同配置的旧行在旧表里已被顶掉
            // 的部分无从恢复，保留下来的行原样保留，升级后新写各归各行不再互覆。
            database.execSQL("CREATE TABLE IF NOT EXISTS `Keep_new` (`key` TEXT NOT NULL, `siteName` TEXT, `vodName` TEXT, `vodPic` TEXT, `createTime` INTEGER NOT NULL, `type` INTEGER NOT NULL, `cid` INTEGER NOT NULL, PRIMARY KEY(`key`, `cid`, `type`))");
            database.execSQL("INSERT INTO `Keep_new` (`key`, `siteName`, `vodName`, `vodPic`, `createTime`, `type`, `cid`) SELECT `key`, `siteName`, `vodName`, `vodPic`, `createTime`, `type`, `cid` FROM `Keep`");
            database.execSQL("DROP TABLE IF EXISTS `Keep`");
            database.execSQL("ALTER TABLE `Keep_new` RENAME TO `Keep`");
        }
    };

    public static final Migration MIGRATION_41_42 = new Migration(41, 42) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 音乐身份修复：①收藏/歌单补 localPath——已下载歌（platform=local）的取链只认
            // localPath，旧行没有这列、收藏/进歌单后回读成死条目；②唯一索引从裸 trackId
            // 扩成 (trackId, platform)——trackId 只是源内编号，跨来源同 id 互相顶掉。
            // 旧表 trackId 本就全局唯一，复合键必然不冲突，存量行一行不丢、不改值；
            // 旧行 localPath 留空（多为在线歌，在线取链不看这列）。
            database.execSQL("ALTER TABLE MusicFavorite ADD COLUMN localPath TEXT");
            database.execSQL("ALTER TABLE MusicPlaylistItem ADD COLUMN localPath TEXT");
            database.execSQL("DROP INDEX IF EXISTS `index_MusicFavorite_trackId`");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_MusicFavorite_trackId_platform` ON `MusicFavorite` (`trackId`, `platform`)");
            database.execSQL("DROP INDEX IF EXISTS `index_MusicPlaylistItem_playlistId_trackId`");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_MusicPlaylistItem_playlistId_trackId_platform` ON `MusicPlaylistItem` (`playlistId`, `trackId`, `platform`)");
        }
    };

    public static final Migration MIGRATION_43_44 = new Migration(43, 44) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 歌单条目身份补插件段（与收藏 42→43 同型）：歌单唯一键此前只到
            // (playlistId, trackId, platform)，FreeJS 各插件 platform 同为 freejs、
            // trackId 是插件内编号，同 id 同 platform 异插件的另一首会被判「已在歌单」
            // 拒入或互相顶掉。新列 plugin 存 rawJson 的 _plugin 戳；存量行留 null
            // （SQLite 唯一索引里 null 互不相等、不会误冲突），读路径按 rawJson 戳比对。
            // 旧表三段键本就唯一，四段键必然不冲突，存量行一行不丢。
            database.execSQL("ALTER TABLE MusicPlaylistItem ADD COLUMN plugin TEXT");
            database.execSQL("DROP INDEX IF EXISTS `index_MusicPlaylistItem_playlistId_trackId_platform`");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_MusicPlaylistItem_playlistId_trackId_platform_plugin` ON `MusicPlaylistItem` (`playlistId`, `trackId`, `platform`, `plugin`)");
        }
    };

    public static final Migration MIGRATION_44_45 = new Migration(44, 45) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 已下载音质标回显＋歌词写入失败留痕：MusicDownload 补两列——qualityTag
            // 存下载当下的音质标（重启回读列表回显，存量行留 null 不显、不冒充）；
            // lyric 存「封面与歌词」写入失败且策略为写入字段时取到的歌词原文
            // （回读兜底见 MusicDownloadStore.readSidecarLyric，存量行留 null）。
            // 两列都是纯追加，存量行一行不丢、不改值。
            database.execSQL("ALTER TABLE MusicDownload ADD COLUMN qualityTag TEXT");
            database.execSQL("ALTER TABLE MusicDownload ADD COLUMN lyric TEXT");
        }
    };

    public static final Migration MIGRATION_42_43 = new Migration(42, 43) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 音乐身份修复续（扫描 M3）：收藏唯一键再扩一段插件来源——FreeJS 各插件
            // platform 同为 freejs、trackId 是插件内编号，只到 (trackId, platform) 时
            // 跨插件同 id 收藏仍互相顶掉：后收藏的那首被 IGNORE 静默吞掉还报成功。
            // 新列 plugin 存 rawJson 的 _plugin 戳；存量行留 null（SQLite 唯一索引里
            // null 互不相等、不会误冲突），读路径按 rawJson 戳比对老行并回填本列。
            // 旧表 (trackId, platform) 本就唯一，三段键必然不冲突，存量行一行不丢。
            database.execSQL("ALTER TABLE MusicFavorite ADD COLUMN plugin TEXT");
            database.execSQL("DROP INDEX IF EXISTS `index_MusicFavorite_trackId_platform`");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_MusicFavorite_trackId_platform_plugin` ON `MusicFavorite` (`trackId`, `platform`, `plugin`)");
        }
    };
}
