package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.fongmi.android.tv.bean.DownloadRecord;

import java.util.List;

@Dao
public abstract class DownloadRecordDao extends BaseDao<DownloadRecord> {

    @Query("SELECT * FROM DownloadRecord ORDER BY createTime DESC")
    public abstract List<DownloadRecord> findAll();

    @Query("SELECT * FROM DownloadRecord WHERE url = :url LIMIT 1")
    public abstract DownloadRecord findByUrl(String url);

    @Query("SELECT * FROM DownloadRecord WHERE filePath = :filePath LIMIT 1")
    public abstract DownloadRecord findByFilePath(String filePath);

    @Query("DELETE FROM DownloadRecord WHERE id = :id")
    public abstract void deleteById(String id);
}
