package com.fongmi.android.tv.hongguo.crypto;

import com.fongmi.android.tv.hongguo.proxy.HgRandomSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 红果正片 CENC 解密器：逐条移植 cenc_decrypt.py（＝ lib_hg_pxy.js 的
 * ccParse/ccSamples/ccDecryptRange/ccPatch）语义，已离线逐字节验通的那一套。
 *
 * <p>语义要点（与底本逐项对齐）：</p>
 * <ul>
 *   <li>盒树 walk：顶层只读盒头（8/16 字节）逐盒跳过，moov 整盒读入解析；
 *       moov 在头在尾都一样走——靠 {@link HgRandomSource} 随机取数，不顺序扫文件。</li>
 *   <li>trak 解析：mdia→hdlr/minf→stbl，记 stsd 入口、stsz/stco/co64/stsc/senc
 *       的绝对偏移；视频入口子盒偏移 +78、音频 +28（与底本同值）。</li>
 *   <li>样本表：stsc 只推进指针一次展开（底本 ccSamples 口径），样本绝对偏移、
 *       大小、每样本 senc IV 前 8 字节在解析期一次性抽出存表。</li>
 *   <li>逐样本 AES-CTR：计数器初值＝该样本 IV 前 8 字节‖块号（8 字节大端，
 *       块内从 0 起）。已离线实证 JDK 的 AES/CTR/NoPadding 是整 128 位大端递增，
 *       与 Python Counter.new(128) 逐字节一致，故直接用 JCE，不手写 CTR。</li>
 *   <li>区间解密：底本 ccDecryptPart 按「块首归属窗口」切分（顺序分窗语义）；
 *       本类是随机 Range 单窗，改为相交块全解＋起始块密钥流推进，见
 *       {@link #decryptWindow} 的口径差注明。</li>
 *   <li>同长改写（补丁表）：encv→hev1（avcC 时 avc1）、enca→sinf 里 frma 记的原始
 *       格式（短剧 AAC 轨即 mp4a、与旧口径同值；听书 Opus 轨为 Opus；frma 读不到
 *       或不可信才回落 mp4a）、sinf→free、
 *       顶层 pssh→free，全部 4 字节等长，出流时按偏移透明套用。</li>
 * </ul>
 *
 * <p><b>与底本共同的简化（如实注明）</b>：底本（Python 与 JS 两版相同）不解析
 * CENC 的 subsample 分段加密——senc 只按「每样本一个 8 字节 IV、无 subsample
 * 条目」读，整样本视为全加密。若某集真用了 subsample（清晰段与加密段交替），
 * 本实现与底本会同样解错；验通语料（ep_enc.mp4，12,903 样本）逐字节全等，
 * 证明该内容未用 subsample。三阶段联调若遇别的档位解花，先查 senc 的
 * subsample_count 是否非零。另：底本要求 trak 必须有 stco，co64-only 的轨会被
 * 底本整轨跳过；本实现接受 co64-only（超集），不影响既有语料。</p>
 */
public final class HgCencDecryptor {

    private HgCencDecryptor() {
    }

    /** 一条同长改写：文件绝对偏移处替换为 bytes（恒 4 字节）。 */
    public static final class Patch {
        public final long offset;
        public final byte[] bytes;

        Patch(long offset, String fourCc) {
            this.offset = offset;
            this.bytes = fourCc.getBytes(StandardCharsets.US_ASCII);
        }
    }

    /** 一条轨的样本表：偏移／大小／IV 均为样本序平行数组，IV 抽平存放（每样本 8 字节）。 */
    public static final class TrackTable {
        public final String handler;
        public final long[] offsets;
        public final int[] sizes;
        public final byte[] ivs;

        TrackTable(String handler, long[] offsets, int[] sizes, byte[] ivs) {
            this.handler = handler;
            this.offsets = offsets;
            this.sizes = sizes;
            this.ivs = ivs;
        }

        public int count() {
            return sizes.length;
        }
    }

    /** 解析产物：样本表＋补丁表，解析后不可变，可被多条 Range 请求并发读。 */
    public static final class Meta {
        public final List<TrackTable> tracks;
        public final List<Patch> patches;
        public final long moovStart;
        public final long moovEnd;

        Meta(List<TrackTable> tracks, List<Patch> patches, long moovStart, long moovEnd) {
            this.tracks = tracks;
            this.patches = patches;
            this.moovStart = moovStart;
            this.moovEnd = moovEnd;
        }

        public int totalSamples() {
            int n = 0;
            for (TrackTable t : tracks) n += t.count();
            return n;
        }
    }

    // ---------------------------------------------------------------- 解析

    private static long u32(byte[] b, int p) {
        return ((b[p] & 0xffL) << 24) | ((b[p + 1] & 0xffL) << 16) | ((b[p + 2] & 0xffL) << 8) | (b[p + 3] & 0xffL);
    }

    private static long u64(byte[] b, int p) {
        return (u32(b, p) << 32) | u32(b, p + 4);
    }

    private static String cc(byte[] b, int p) {
        return new String(b, p, 4, StandardCharsets.ISO_8859_1);
    }

    private interface BoxVisitor {
        /** 返回 true 提前停。 */
        boolean visit(String type, long pos, long size, int hdr);
    }

    /** 在 buf 的 [start,end) 区间（对应文件绝对偏移 absBase+start）内 walk 盒。 */
    /** frma 原始格式须是可打印的 4 字节 fourcc 才采信，否则回落旧口径 mp4a。 */
    private static boolean isFourCc(String s) {
        if (s == null || s.length() != 4) return false;
        for (int i = 0; i < 4; i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c > 0x7e) return false;
        }
        return true;
    }

    private static void walk(byte[] buf, int start, int end, long absBase, BoxVisitor v) {
        int pos = start;
        while (pos + 8 <= end) {
            long sz = u32(buf, pos);
            int hdr = 8;
            if (sz == 1) {
                if (pos + 16 > end) break;
                sz = u64(buf, pos + 8);
                hdr = 16;
            } else if (sz == 0) {
                sz = end - pos;
            }
            if (sz < 8 || pos + sz > end) break;
            if (v.visit(cc(buf, pos + 4), absBase + pos, sz, hdr)) return;
            pos += (int) sz;
        }
    }

    /** 从源里把 [pos,pos+len) 读满；越界抛异常。 */
    private static void readFully(HgRandomSource src, long pos, byte[] buf, int off, int len) throws IOException {
        int got = 0;
        while (got < len) {
            int n = src.readAt(pos + got, buf, off + got, len - got);
            if (n < 0) throw new IOException("上游提前结束 @" + (pos + got));
            got += n;
        }
    }

    /**
     * 解析盒结构：顶层逐盒只读盒头跳过（mdat 不读内容），moov 整盒读入后解析
     * 各 trak。首字节延迟只取决于盒头几次小读＋moov 一读，与文件总长无关。
     */
    public static Meta parse(HgRandomSource src) throws IOException {
        long total = src.length();
        if (total < 8) throw new IOException("文件过小: " + total);
        List<Patch> patches = new ArrayList<>();
        byte[] moov = null;
        long moovStart = -1, moovEnd = -1;
        byte[] head = new byte[16];
        long pos = 0;
        while (pos + 8 <= total) {
            int hn = (int) Math.min(16, total - pos);
            readFully(src, pos, head, 0, hn);
            long sz = u32(head, 0);
            int hdr = 8;
            if (sz == 1) {
                if (hn < 16) break;
                sz = u64(head, 8);
                hdr = 16;
            } else if (sz == 0) {
                sz = total - pos;
            }
            if (sz < 8 || pos + sz > total) break;
            String type = cc(head, 4);
            if ("moov".equals(type)) {
                if (sz > Integer.MAX_VALUE - 8) throw new IOException("moov 过大: " + sz);
                moov = new byte[(int) sz];
                readFully(src, pos, moov, 0, (int) sz);
                moovStart = pos;
                moovEnd = pos + sz;
            } else if ("pssh".equals(type)) {
                patches.add(new Patch(pos + 4, "free"));
            }
            pos += sz;
            if (moov != null && pos > moovEnd && patchesDone(patches)) {
                // moov 已拿到且顶层已走完才停；pssh 可能在 moov 之后，走完顶层再收工。
            }
        }
        if (moov == null) throw new IOException("no moov（分片 mp4 暂不支持）");
        List<TrackTable> tracks = new ArrayList<>();
        final byte[] moovBytes = moov;
        final long moovContentBase = moovStart; // moov 字节数组下标 0 对应的文件绝对偏移
        int moovHdr = (int) (u32(moovBytes, 0) == 1 ? 16 : 8);
        walk(moovBytes, moovHdr, moovBytes.length, moovContentBase, (type, p, s, hdr) -> {
            if ("trak".equals(type)) {
                TrackTable t = parseTrak(moovBytes, (int) (p - moovContentBase) + hdr, (int) (p - moovContentBase + s), moovContentBase, patches);
                if (t != null) tracks.add(t);
            }
            return false;
        });
        if (tracks.isEmpty()) throw new IOException("moov 内无带 senc 的轨");
        return new Meta(tracks, patches, moovStart, moovEnd);
    }

    private static boolean patchesDone(List<Patch> patches) {
        return false; // 顶层 walk 必须走完（pssh 位置不定），此哨兵仅防误读，无实际短路
    }

    /** trak 原始记录（偏移均为 moov 字节数组下标，-1 表示无）。 */
    private static final class TrakRaw {
        String handler = "";
        int stblStart = -1, stblEnd = -1;
        int entryOff = -1;
        String entryCC = "";
        int sinfOff = -1;
        String origFmt = "";
        String codecTag = "";
        int stsz = -1, stco = -1, co64 = -1, stsc = -1, senc = -1;
    }

    private static TrackTable parseTrak(byte[] b, int start, int end, long absBase, List<Patch> patches) {
        TrakRaw t = new TrakRaw();
        walk(b, start, end, absBase, (type, p, s, hdr) -> {
            if ("mdia".equals(type)) {
                int mp = (int) (p - absBase);
                walk(b, mp + hdr, mp + (int) s, absBase, (ty2, p2, s2, hdr2) -> {
                    int mp2 = (int) (p2 - absBase);
                    if ("hdlr".equals(ty2)) {
                        t.handler = cc(b, mp2 + 16);
                    } else if ("minf".equals(ty2)) {
                        walk(b, mp2 + hdr2, mp2 + (int) s2, absBase, (ty3, p3, s3, hdr3) -> {
                            if ("stbl".equals(ty3)) {
                                int mp3 = (int) (p3 - absBase);
                                t.stblStart = mp3 + hdr3;
                                t.stblEnd = mp3 + (int) s3;
                            }
                            return false;
                        });
                    }
                    return false;
                });
            }
            return false;
        });
        if (t.stblStart < 0) return null;
        walk(b, t.stblStart, t.stblEnd, absBase, (type, p, s, hdr) -> {
            int mp = (int) (p - absBase);
            switch (type) {
                case "stsd" -> {
                    t.entryOff = mp + 16;
                    t.entryCC = cc(b, mp + 20);
                }
                case "stsz" -> t.stsz = mp;
                case "stco" -> t.stco = mp;
                case "co64" -> t.co64 = mp;
                case "stsc" -> t.stsc = mp;
                case "senc" -> t.senc = mp;
                default -> {
                }
            }
            return false;
        });
        if (t.entryOff >= 0) {
            int childOff = t.entryOff + 8 + ("vide".equals(t.handler) ? 78 : 28);
            int entrySize = (int) u32(b, t.entryOff);
            if (childOff < t.entryOff + entrySize) {
                walk(b, childOff, t.entryOff + entrySize, absBase, (type, p, s, hdr) -> {
                    int mp = (int) (p - absBase);
                    if ("sinf".equals(type)) {
                        t.sinfOff = mp;
                        // frma 记着加密前的原始格式（音频书 Opus 轨靠它把 enca 改回去，
                        // 短剧 AAC 轨 frma 就是 mp4a、改名结果与旧口径逐字节相同）。
                        int sinfEnd = mp + (int) s;
                        walk(b, mp + hdr, sinfEnd, absBase, (ty4, p4, s4, hdr4) -> {
                            if ("frma".equals(ty4)) {
                                t.origFmt = cc(b, (int) (p4 - absBase) + 8);
                            }
                            return false;
                        });
                    }
                    else if ("hvcC".equals(type) || "avcC".equals(type) || "bv2C".equals(type)
                            || "vpcC".equals(type) || "av1C".equals(type)) t.codecTag = type;
                    return false;
                });
            }
        }
        if (t.senc < 0 || t.stsz < 0 || (t.stco < 0 && t.co64 < 0) || t.stsc < 0) return null;

        // 补丁（绝对偏移）
        long entryAbs = absBase + t.entryOff;
        if ("encv".equals(t.entryCC)) patches.add(new Patch(entryAbs + 4, "avcC".equals(t.codecTag) ? "avc1" : "hev1"));
        else if ("enca".equals(t.entryCC)) patches.add(new Patch(entryAbs + 4, isFourCc(t.origFmt) ? t.origFmt : "mp4a"));
        if (t.sinfOff >= 0) patches.add(new Patch(absBase + t.sinfOff + 4, "free"));

        return buildSamples(b, t);
    }

    /** 展开样本表（stsc 指针单趟推进，底本 ccSamples 口径）＋抽出每样本 IV。 */
    private static TrackTable buildSamples(byte[] b, TrakRaw t) {
        int stsz = t.stsz, stsc = t.stsc;
        long defSize = u32(b, stsz + 12);
        int cnt = (int) u32(b, stsz + 16);
        int szOff = stsz + 20;
        boolean big = t.co64 >= 0;
        int coff = big ? t.co64 : t.stco;
        int nch = (int) u32(b, coff + 12);
        int coOff = coff + 16;
        int nsc = (int) u32(b, stsc + 12);
        int scOff = stsc + 16;

        long[] offs = new long[cnt];
        int[] szs = new int[cnt];
        int si = 0, ei = 0;
        long spc = nsc > 0 ? u32(b, scOff + 4) : 1;
        for (int ci = 0; ci < nch && si < cnt; ci++) {
            while (ei + 1 < nsc && ci + 1 >= u32(b, scOff + (ei + 1) * 12)) {
                ei++;
                spc = u32(b, scOff + ei * 12 + 4);
            }
            long off = big ? u64(b, coOff + ci * 8) : u32(b, coOff + ci * 4);
            for (long j = 0; j < spc && si < cnt; j++) {
                int s = defSize != 0 ? (int) defSize : (int) u32(b, szOff + si * 4);
                offs[si] = off;
                szs[si] = s;
                off += s;
                si++;
            }
        }
        int n = si;
        long[] fo = new long[n];
        int[] fs = new int[n];
        System.arraycopy(offs, 0, fo, 0, n);
        System.arraycopy(szs, 0, fs, 0, n);
        byte[] ivs = new byte[n * 8];
        int ivBase = t.senc + 16;
        for (int i = 0; i < n; i++) {
            int from = ivBase + i * 8;
            if (from + 8 <= b.length) System.arraycopy(b, from, ivs, i * 8, 8);
        }
        return new TrackTable(t.handler, fo, fs, ivs);
    }

    // ---------------------------------------------------------------- 解密

    /** 把补丁表里落在 buf 覆盖的文件区间 [bufStart, bufStart+len) 内的改写套上。 */
    public static void applyPatches(Meta meta, byte[] buf, long bufStart, int off, int len) {
        long ws = bufStart, we = bufStart + len;
        for (Patch p : meta.patches) {
            for (int i = 0; i < 4; i++) {
                long at = p.offset + i;
                if (at >= ws && at < we) buf[off + (int) (at - ws)] = p.bytes[i];
            }
        }
    }

    /**
     * 解密 buf 里文件区间 [bufStart, bufStart+len) 的内容（buf 下标 off 起）。
     * 返回实际触及的块数（计数证据用）。
     *
     * <p>与底本的口径差（如实注明）：底本 ccDecryptPart 按「块首归属窗口」切分，
     * 服务的是整文件顺序分窗（相邻窗拼起来每块恰解一次）；本类服务的是随机
     * Range 单窗，窗口头若落在一个块中间，那半块没人解就会留密文。故本实现按
     * 「与窗口相交的块全解」：起始块的计数器定位到块号后，先用零字节推进
     * Cipher 吃掉块内已过的字节（密钥流推进），再从窗口头原地异或；尾块同理
     * 只解到窗口尾。整窗从样本头开始时（整文件解、全 0 起点）与底本逐字节等价，
     * 已由离线全文件＋随机区间双验。</p>
     *
     * <p>缓冲纪律：除每窗一把 Cipher 与一块 15 字节零垫外不分配新缓冲，
     * 样本粒度直接在调用方 buf 上原地异或。</p>
     */
    public static int decryptWindow(Meta meta, byte[] dek, byte[] buf, long bufStart, int off, int len)
            throws IOException {
        long ws = bufStart, we = bufStart + len;
        int blocks = 0;
        try {
            SecretKeySpec key = new SecretKeySpec(dek, "AES");
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            byte[] iv16 = new byte[16];
            byte[] skipPad = new byte[15];
            byte[] skipOut = new byte[15];
            for (TrackTable t : meta.tracks) {
                for (int i = 0; i < t.count(); i++) {
                    long o = t.offsets[i];
                    int z = t.sizes[i];
                    if (z <= 0 || o + z <= ws || o >= we) continue;
                    long segStart = Math.max(ws, o);
                    long segEnd = Math.min(we, o + z);
                    if (segEnd <= segStart) continue;
                    long jS = (segStart - o) / 16;           // 起始块号（块内可未对齐）
                    int inBlock = (int) (segStart - (o + jS * 16));
                    System.arraycopy(t.ivs, i * 8, iv16, 0, 8);
                    // 块号写进后 8 字节大端（底本 JS 只写低 32 位；此处写满 64 位等价且更稳）
                    long blk = jS;
                    for (int k = 15; k >= 8; k--) {
                        iv16[k] = (byte) (blk & 0xff);
                        blk >>>= 8;
                    }
                    cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv16));
                    if (inBlock > 0) {
                        // 推进密钥流到块内偏移：喂零字节并丢弃输出
                        cipher.update(skipPad, 0, inBlock, skipOut, 0);
                    }
                    int bOff = off + (int) (segStart - ws);
                    cipher.doFinal(buf, bOff, (int) (segEnd - segStart), buf, bOff);
                    blocks += (int) ((segEnd - o + 15) / 16 - jS);
                }
            }
        } catch (Exception e) {
            // 必须以 IOException 出（A-10）：调用链 readClear/fill 只声明
            // IOException，运行时异常会穿透 NanoHTTPD 的出流线程、把错误
            // 文本混进媒体流且无状态可分；IOException 则由出流层按
            // 502 明码收口（HongguoStream）。
            throw new IOException("CENC 解密失败", e);
        }
        return blocks;
    }

    /** 整份解密（离线验与小文件兜底用）：就地改写 enc 并返回同一数组。 */
    public static byte[] decryptAll(byte[] enc, byte[] dek) throws IOException {
        HgRandomSource mem = new HgRandomSource() {
            @Override
            public long length() {
                return enc.length;
            }

            @Override
            public int readAt(long pos, byte[] buf, int off, int len) {
                if (pos >= enc.length) return -1;
                int n = (int) Math.min(len, enc.length - pos);
                System.arraycopy(enc, (int) pos, buf, off, n);
                return n;
            }

            @Override
            public long fetchedBytes() {
                return 0;
            }

            @Override
            public int fetchCalls() {
                return 0;
            }

            @Override
            public void close() {
            }
        };
        Meta meta = parse(mem);
        decryptWindow(meta, dek, enc, 0, 0, enc.length);
        applyPatches(meta, enc, 0, 0, enc.length);
        return enc;
    }
}
