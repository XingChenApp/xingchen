package com.fongmi.android.tv.patch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * .xcpatch 容器头（生成端 scripts/make_release_patch.sh 与本类同格式，钉死不许单方改）：
 * 偏移 0 起 8 字节魔数 ASCII「XCPATCH1」；其后 4 字节大端无符号整数＝头长 N；
 * 其后 N 字节 UTF-8 JSON 头；其后为原始 ZipDiff 补丁字节。
 * JSON 字段：format、fromVersionCode、fromVersionName、toVersionCode、toVersionName、
 * baseApkSha256（发布形态旧包整包）、targetApkSha256（发布形态新包整包）、
 * patchDataSha256（补丁数据段）、patchDataSize、targetSize。
 */
public final class PatchMeta {

    public static final String MAGIC = "XCPATCH1";
    private static final int MAX_HEADER_BYTES = 1024 * 1024;

    public final long fromVersionCode;
    public final String fromVersionName;
    public final long toVersionCode;
    public final String toVersionName;
    public final String baseApkSha256;
    public final String targetApkSha256;
    public final String patchDataSha256;
    public final long patchDataSize;
    public final long targetSize;
    /** 原始补丁数据段在本文件中的起始偏移。 */
    public final long dataOffset;

    private PatchMeta(JsonObject json, long dataOffset) {
        this.fromVersionCode = reqLong(json, "fromVersionCode");
        this.fromVersionName = reqString(json, "fromVersionName");
        this.toVersionCode = reqLong(json, "toVersionCode");
        this.toVersionName = reqString(json, "toVersionName");
        this.baseApkSha256 = reqHash(json, "baseApkSha256");
        this.targetApkSha256 = reqHash(json, "targetApkSha256");
        this.patchDataSha256 = reqHash(json, "patchDataSha256");
        this.patchDataSize = reqLong(json, "patchDataSize");
        this.targetSize = reqLong(json, "targetSize");
        this.dataOffset = dataOffset;
        if (patchDataSize <= 0 || targetSize <= 0) throw new IllegalArgumentException("补丁头里的大小不对");
    }

    /** 解析容器头；任何格式问题抛 IllegalArgumentException（信息可直接给用户看）。 */
    public static PatchMeta parse(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IllegalArgumentException("补丁文件不存在");
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 12) throw new IllegalArgumentException("这不是补丁文件（太小）");
            byte[] magic = new byte[8];
            raf.readFully(magic);
            if (!MAGIC.equals(new String(magic, StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException("这不是补丁文件（文件头对不上）");
            int headerLen = raf.readInt();
            if (headerLen <= 0 || headerLen > MAX_HEADER_BYTES || 12L + headerLen > raf.length())
                throw new IllegalArgumentException("补丁文件头损坏");
            byte[] header = new byte[headerLen];
            raf.readFully(header);
            JsonObject json;
            try {
                json = JsonParser.parseString(new String(header, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                throw new IllegalArgumentException("补丁文件头损坏（读不出内容）");
            }
            if (!json.has("format") || json.get("format").getAsInt() != 1)
                throw new IllegalArgumentException("补丁格式版本不支持");
            PatchMeta meta = new PatchMeta(json, 12L + headerLen);
            long actualData = raf.length() - meta.dataOffset;
            if (actualData != meta.patchDataSize)
                throw new IllegalArgumentException("补丁文件不完整：应有 " + meta.patchDataSize + " 字节补丁数据，实际 " + actualData + " 字节");
            return meta;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("补丁文件头损坏（" + e.getMessage() + "）");
        }
    }

    private static long reqLong(JsonObject json, String key) {
        if (!json.has(key)) throw new IllegalArgumentException("补丁头缺字段：" + key);
        try {
            return json.get(key).getAsLong();
        } catch (Exception e) {
            throw new IllegalArgumentException("补丁头字段不对：" + key);
        }
    }

    private static String reqString(JsonObject json, String key) {
        if (!json.has(key)) throw new IllegalArgumentException("补丁头缺字段：" + key);
        String value = json.get(key).getAsString();
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("补丁头字段为空：" + key);
        return value;
    }

    private static String reqHash(JsonObject json, String key) {
        String value = reqString(json, key).trim().toLowerCase();
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("补丁头里的指纹不对：" + key);
        return value;
    }
}
