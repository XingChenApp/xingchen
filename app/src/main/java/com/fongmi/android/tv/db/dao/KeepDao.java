package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.Keep;

import java.util.List;

@Dao
public abstract class KeepDao extends BaseDao<Keep> {

    @Query("SELECT * FROM Keep")
    public abstract List<Keep> findAll();

    @Query("SELECT * FROM Keep WHERE type = 0 ORDER BY createTime DESC")
    public abstract List<Keep> getVod();

    @Query("SELECT * FROM Keep WHERE type = 1 ORDER BY createTime DESC")
    public abstract List<Keep> getLive();

    @Query("SELECT * FROM Keep WHERE type = 0 AND cid = :cid AND `key` = :key")
    public abstract Keep find(int cid, String key);

    // 直播收藏（type=1）按 cid＋频道名双键：同名频道跨配置互不顶替。
    // cid=0 是旧版 addKeep 未写 cid 的存量行，读/删时按未归属处理，避免升级后旧收藏整体消失；
    // 新写入由调用方带真实 cid（见 Keep.save(int)），存量会随重新收藏逐步归位。
    @Query("SELECT * FROM Keep WHERE type = 1 AND (cid = :cid OR cid = 0) AND `key` = :key")
    public abstract Keep findLive(int cid, String key);

    @Query("DELETE FROM Keep WHERE type = 1 AND (cid = :cid OR cid = 0) AND `key` = :key")
    public abstract void deleteLive(int cid, String key);

    @Query("DELETE FROM Keep WHERE type = 0 AND cid = :cid AND `key` = :key")
    public abstract void delete(int cid, String key);

    @Query("DELETE FROM Keep WHERE type = 0 AND cid = :cid")
    public abstract void delete(int cid);

    @Query("DELETE FROM Keep WHERE type = 0")
    public abstract void delete();

    @Query("DELETE FROM Keep")
    public abstract void deleteAll();
}
