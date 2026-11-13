package com.fongmi.android.tv.cloud.drive115;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 音频文件头标签解析（纯字节版，无安卓依赖，供 JVM 直测）：
 * 只吃文件头一段字节，把 ID3v2（APIC／USLT／SYLT／TXXX／TIT2／TPE1／TALB／TLEN）、
 * FLAC（STREAMINFO 时长／Vorbis comment／PICTURE）、Ogg 注释包（Vorbis／Opus，
 * 含 METADATA_BLOCK_PICTURE）、MP4（moov 在头部窗口内时：©nam／©ART／©alb／©lyr／covr／mvhd）
 * 一次读全。判读口径与 EmbeddedLyricsReader 同族（USLT＞SYLT＞TXXX、Vorbis 键集），
 * 那一份是本地 File 版、这里是远程头部字节版，分工不同不互相替代。
 * 解析状态三档：COMPLETE＝已读全（有无标签都算）；NEED_MORE＝头不够、加大窗口可能有；
 * NO_TAG＝魔数不认／格式不在头部口径（如 APE 尾标签、moov 在文件尾的 MP4）。
 * 任何损坏一律降级为「读到多少算多少」，不抛异常。
 */
public final class AudioHeadTags {

    public static final int COMPLETE = 0;
    public static final int NEED_MORE = 1;
    public static final int NO_TAG = 2;

    public static final class Result {
        public String title;
        public String artist;
        public String album;
        public String lyric;
        public long durationMs;
        public byte[] picture;
        public String pictureMime = "";
        public int status = NO_TAG;

        public boolean anyMeta() {
            return (title != null && !title.trim().isEmpty())
                    || (artist != null && !artist.trim().isEmpty())
                    || (album != null && !album.trim().isEmpty())
                    || (lyric != null && !lyric.trim().isEmpty())
                    || durationMs > 0 || picture != null;
        }
    }

    private AudioHeadTags() {
    }

    /** @param moreAvailable 窗口外是否还有字节可读（决定截断时报 NEED_MORE 还是按残缺收口） */
    public static Result parse(byte[] buf, int len, boolean moreAvailable) {
        Result r = new Result();
        if (buf == null || len < 4) {
            r.status = moreAvailable ? NEED_MORE : NO_TAG;
            return r;
        }
        try {
            if (buf[0] == 'I' && buf[1] == 'D' && buf[2] == '3') return parseId3(buf, len, moreAvailable);
            if (len >= 4 && buf[0] == 'f' && buf[1] == 'L' && buf[2] == 'a' && buf[3] == 'C') {
                return parseFlac(buf, len, moreAvailable);
            }
            if (len >= 4 && buf[0] == 'O' && buf[1] == 'g' && buf[2] == 'g' && buf[3] == 'S') {
                return parseOgg(buf, len, moreAvailable);
            }
            if (len >= 8 && buf[4] == 'f' && buf[5] == 't' && buf[6] == 'y' && buf[7] == 'p') {
                return parseMp4(buf, len, moreAvailable);
            }
        } catch (Throwable ignored) {
            r.status = COMPLETE;
            return r;
        }
        r.status = NO_TAG;
        return r;
    }

    // ================= ID3v2 =================

    private static Result parseId3(byte[] buf, int len, boolean moreAvailable) {
        Result r = new Result();
        int major = buf[3] & 0xFF;
        if (major < 2 || major > 4) {
            r.status = NO_TAG;
            return r;
        }
        boolean unsync = (buf[5] & 0x80) != 0;
        int tagSize = synchsafe(buf, 6);
        if (tagSize <= 0) {
            r.status = NO_TAG;
            return r;
        }
        boolean truncated = len < 10 + tagSize;
        int avail = Math.min(len, 10 + tagSize);
        byte[] tag = new byte[avail - 10];
        System.arraycopy(buf, 10, tag, 0, tag.length);
        if (unsync) tag = deunsync(tag);
        int[] params = major == 2 ? null : audioParams(buf, Math.min(len, 10 + tagSize), len);
        scanId3(tag, major == 2, major == 4, params, r);
        if (truncated && moreAvailable && tagSize <= 16 * 1024 * 1024) {
            // 标签还没读全（多半是大封面在后段）：已扫到的字段先留着，状态报 NEED_MORE 由调用方加大窗口
            r.status = NEED_MORE;
        } else {
            r.status = COMPLETE;
        }
        return r;
    }

    private static void scanId3(byte[] tag, boolean v22, boolean v24, int[] syltParams, Result r) {
        String uslt = null, sylt = null, txxx = null;
        int off = 0;
        while (off < tag.length) {
            if (v22) {
                if (!isFrameId(tag, off, 3)) {
                    off++;
                    continue;
                }
                if (off + 6 > tag.length) break;
                int size = ((tag[off + 3] & 0xFF) << 16) | ((tag[off + 4] & 0xFF) << 8) | (tag[off + 5] & 0xFF);
                if (size <= 0 || off + 6 + size > tag.length) break;
                String id = new String(tag, off, 3, StandardCharsets.ISO_8859_1);
                int body = off + 6;
                switch (id) {
                    case "TT2": if (r.title == null) r.title = textFrame(tag, body, size); break;
                    case "TP1": if (r.artist == null) r.artist = textFrame(tag, body, size); break;
                    case "TAL": if (r.album == null) r.album = textFrame(tag, body, size); break;
                    case "ULT": if (uslt == null) uslt = parseUslt(tag, body, size); break;
                    case "SLT": if (sylt == null) sylt = parseSylt(tag, body, size, syltParams); break;
                    case "PIC": if (r.picture == null) parsePicV22(tag, body, size, r); break;
                    default: break;
                }
                off += 6 + size;
            } else {
                if (!isFrameId(tag, off, 4)) {
                    off++;
                    continue;
                }
                if (off + 10 > tag.length) break;
                int size = v24 ? synchsafe(tag, off + 4)
                        : ((tag[off + 4] & 0xFF) << 24) | ((tag[off + 5] & 0xFF) << 16)
                        | ((tag[off + 6] & 0xFF) << 8) | (tag[off + 7] & 0xFF);
                if (size <= 0 || off + 10 + size > tag.length) break;
                String id = new String(tag, off, 4, StandardCharsets.ISO_8859_1);
                int body = off + 10;
                switch (id) {
                    case "TIT2": if (r.title == null) r.title = textFrame(tag, body, size); break;
                    case "TPE1": if (r.artist == null) r.artist = textFrame(tag, body, size); break;
                    case "TALB": if (r.album == null) r.album = textFrame(tag, body, size); break;
                    case "TLEN":
                        if (r.durationMs <= 0) {
                            String s = textFrame(tag, body, size);
                            if (s != null) {
                                try {
                                    r.durationMs = Long.parseLong(s.trim());
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        break;
                    case "USLT": if (uslt == null) uslt = parseUslt(tag, body, size); break;
                    case "SYLT": if (sylt == null) sylt = parseSylt(tag, body, size, syltParams); break;
                    case "TXXX": if (txxx == null) txxx = parseTxxx(tag, body, size); break;
                    case "APIC": if (r.picture == null) parseApic(tag, body, size, r); break;
                    default: break;
                }
                // 目标帧收齐就停：全齐（歌名/歌手/专辑/词/图）或图已到且文本类齐
                off += 10 + size;
            }
        }
        if (uslt != null && !uslt.trim().isEmpty()) r.lyric = uslt;
        else if (sylt != null && !sylt.trim().isEmpty()) r.lyric = sylt;
        else if (txxx != null && !txxx.trim().isEmpty()) r.lyric = txxx;
        if (r.title != null) r.title = r.title.trim();
        if (r.artist != null) r.artist = r.artist.trim();
        if (r.album != null) r.album = r.album.trim();
    }

    /** 文本帧：编码(1)＋正文 */
    private static String textFrame(byte[] tag, int body, int size) {
        if (size < 1) return null;
        int enc = tag[body] & 0xFF;
        if (enc > 3) return null;
        String s = decode(tag, body + 1, size - 1, enc);
        return s == null ? null : s.replace("\0", "").trim();
    }

    private static void parseApic(byte[] tag, int body, int size, Result r) {
        if (size < 4) return;
        int enc = tag[body] & 0xFF;
        if (enc > 3) return;
        int end = body + size;
        int p = body + 1;
        int mimeEnd = findTerminator(tag, p, end, 0);
        if (mimeEnd < 0) return;
        String mime = new String(tag, p, mimeEnd - p, StandardCharsets.ISO_8859_1).trim().toLowerCase();
        p = mimeEnd + 1;
        if (p >= end) return;
        p++; // picture type
        int descEnd = findTerminator(tag, p, end, enc);
        if (descEnd < 0) return;
        p = descEnd + (enc == 0 || enc == 3 ? 1 : 2);
        if (p >= end) return;
        byte[] pic = new byte[end - p];
        System.arraycopy(tag, p, pic, 0, pic.length);
        if (pic.length == 0) return;
        r.picture = pic;
        r.pictureMime = mime;
    }

    private static void parsePicV22(byte[] tag, int body, int size, Result r) {
        if (size < 6) return;
        int enc = tag[body] & 0xFF;
        if (enc > 3) return;
        int end = body + size;
        String fmt = new String(tag, body + 1, 3, StandardCharsets.ISO_8859_1).trim().toLowerCase();
        int p = body + 5; // 编码(1)＋格式(3)＋类型(1)
        int descEnd = findTerminator(tag, p, end, enc);
        if (descEnd < 0) return;
        p = descEnd + (enc == 0 || enc == 3 ? 1 : 2);
        if (p >= end) return;
        byte[] pic = new byte[end - p];
        System.arraycopy(tag, p, pic, 0, pic.length);
        if (pic.length == 0) return;
        r.picture = pic;
        r.pictureMime = fmt.equals("png") ? "image/png" : "image/jpeg";
    }

    private static String parseUslt(byte[] tag, int body, int size) {
        if (size < 5) return null;
        int enc = tag[body] & 0xFF;
        if (enc > 3) return null;
        int p = body + 4;
        int end = body + size;
        int descEnd = findTerminator(tag, p, end, enc);
        if (descEnd < 0) return null;
        int textStart = descEnd + (enc == 0 || enc == 3 ? 1 : 2);
        if (textStart > end) return null;
        return decode(tag, textStart, end - textStart, enc);
    }

    private static String parseSylt(byte[] tag, int body, int size, int[] syltParams) {
        if (size < 7) return null;
        int enc = tag[body] & 0xFF;
        if (enc > 3) return null;
        int tsFormat = tag[body + 4] & 0xFF;
        if (tsFormat != 1 && tsFormat != 2) return null;
        int p = body + 6;
        int end = body + size;
        int descEnd = findTerminator(tag, p, end, enc);
        if (descEnd < 0) return null;
        p = descEnd + (enc == 0 || enc == 3 ? 1 : 2);
        java.util.List<Long> times = new java.util.ArrayList<>();
        java.util.List<String> texts = new java.util.ArrayList<>();
        while (p < end) {
            int tEnd = findTerminator(tag, p, end, enc);
            if (tEnd < 0 || tEnd + (enc == 0 || enc == 3 ? 1 : 2) + 4 > end) break;
            String text = decode(tag, p, tEnd - p, enc);
            int tsPos = tEnd + (enc == 0 || enc == 3 ? 1 : 2);
            long ts = ((tag[tsPos] & 0xFFL) << 24) | ((tag[tsPos + 1] & 0xFFL) << 16)
                    | ((tag[tsPos + 2] & 0xFFL) << 8) | (tag[tsPos + 3] & 0xFFL);
            if (tsFormat == 1) {
                long spf = syltParams != null ? syltParams[1] : 1152;
                long rate = syltParams != null ? syltParams[0] : 44100;
                ts = ts * spf * 1000 / rate;
            }
            if (text != null && !text.trim().isEmpty()) {
                times.add(ts);
                texts.add(text.trim());
            }
            p = tsPos + 4;
        }
        if (texts.isEmpty()) return null;
        Integer[] order = new Integer[texts.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Long.compare(times.get(a), times.get(b)));
        StringBuilder sb = new StringBuilder();
        for (int idx : order) {
            long ms = Math.max(0, times.get(idx));
            sb.append(String.format("[%02d:%02d.%02d]", ms / 60000, (ms / 1000) % 60, (ms / 10) % 100));
            sb.append(texts.get(idx)).append('\n');
        }
        return sb.toString();
    }

    private static String parseTxxx(byte[] tag, int body, int size) {
        if (size < 2) return null;
        int enc = tag[body] & 0xFF;
        if (enc > 3) return null;
        int end = body + size;
        int descEnd = findTerminator(tag, body + 1, end, enc);
        if (descEnd < 0) return null;
        String desc = decode(tag, body + 1, descEnd - (body + 1), enc);
        if (desc == null || !desc.toUpperCase().contains("LYRIC")) return null;
        int valStart = descEnd + (enc == 0 || enc == 3 ? 1 : 2);
        if (valStart > end) return null;
        return decode(tag, valStart, end - valStart, enc);
    }

    /** MP3 帧头现读 [采样率, 每帧采样数]（供 SYLT 格式 1），在 buf[from..len) 256KB 内找首个合法帧同步 */
    private static int[] audioParams(byte[] buf, int from, int len) {
        try {
            int[][] rates = {{44100, 48000, 32000}, {22050, 24000, 16000}, {11025, 12000, 8000}};
            int end = Math.min(len - 4, from + 256 * 1024);
            for (int i = Math.max(0, from); i <= end; i++) {
                int h = ((buf[i] & 0xFF) << 24) | ((buf[i + 1] & 0xFF) << 16)
                        | ((buf[i + 2] & 0xFF) << 8) | (buf[i + 3] & 0xFF);
                if ((h & 0xFFE00000) != 0xFFE00000) continue;
                int version = (h >> 19) & 3;
                int layer = (h >> 17) & 3;
                int bitrateIdx = (h >> 12) & 15;
                int rateIdx = (h >> 10) & 3;
                if (version == 1 || layer == 0 || bitrateIdx == 15 || bitrateIdx == 0 || rateIdx == 3) continue;
                int vrow = version == 3 ? 0 : version == 2 ? 1 : 2;
                int rate = rates[vrow][rateIdx];
                int spf = layer == 3 ? 384 : layer == 2 ? 1152 : version == 3 ? 1152 : 576;
                return new int[]{rate, spf};
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ================= FLAC =================

    private static Result parseFlac(byte[] buf, int len, boolean moreAvailable) {
        Result r = new Result();
        int pos = 4;
        boolean sawAny = false;
        while (pos + 4 <= len) {
            int b0 = buf[pos] & 0xFF;
            int type = b0 & 0x7F;
            boolean last = (b0 & 0x80) != 0;
            int blockLen = ((buf[pos + 1] & 0xFF) << 16) | ((buf[pos + 2] & 0xFF) << 8) | (buf[pos + 3] & 0xFF);
            if (blockLen < 0) break;
            if (pos + 4 + blockLen > len) {
                // 块被窗口截断：图/评论块读不全就如实留空，状态按窗口余量定
                r.status = moreAvailable ? NEED_MORE : COMPLETE;
                return r;
            }
            sawAny = true;
            int body = pos + 4;
            if (type == 0 && blockLen >= 18) {
                // STREAMINFO：第 10 字节起 20 位采样率、其后 36 位总采样数
                int rate = ((buf[body + 10] & 0xFF) << 12) | ((buf[body + 11] & 0xFF) << 4)
                        | ((buf[body + 12] & 0xFF) >> 4);
                long total = ((long) (buf[body + 13] & 0x0F) << 32)
                        | ((long) (buf[body + 14] & 0xFF) << 24)
                        | ((long) (buf[body + 15] & 0xFF) << 16)
                        | ((long) (buf[body + 16] & 0xFF) << 8)
                        | (buf[body + 17] & 0xFF);
                if (rate > 0 && total > 0) r.durationMs = total * 1000 / rate;
            } else if (type == 4) {
                parseVorbisComment(buf, body, blockLen, r);
            } else if (type == 6) {
                parseFlacPicture(buf, body, blockLen, r);
            }
            pos += 4 + blockLen;
            if (last) break;
        }
        if (!sawAny) {
            r.status = moreAvailable && len < 8 ? NEED_MORE : NO_TAG;
            return r;
        }
        r.status = COMPLETE;
        return r;
    }

    private static void parseVorbisComment(byte[] buf, int body, int blockLen, Result r) {
        try {
            int end = body + blockLen;
            int p = body;
            if (p + 4 > end) return;
            int vendorLen = le32(buf, p);
            p += 4;
            if (vendorLen < 0 || p + vendorLen + 4 > end) return;
            p += vendorLen;
            int count = le32(buf, p);
            p += 4;
            if (count < 0 || count > 100000) return;
            for (int i = 0; i < count; i++) {
                if (p + 4 > end) return;
                int elen = le32(buf, p);
                p += 4;
                if (elen < 0 || p + elen > end) return;
                String entry = new String(buf, p, elen, StandardCharsets.UTF_8);
                p += elen;
                int eq = entry.indexOf('=');
                if (eq <= 0) continue;
                String key = entry.substring(0, eq).trim().toUpperCase();
                String v = entry.substring(eq + 1);
                if (v.trim().isEmpty()) continue;
                switch (key) {
                    case "TITLE": if (r.title == null) r.title = v.trim(); break;
                    case "ARTIST": if (r.artist == null) r.artist = v.trim(); break;
                    case "ALBUM": if (r.album == null) r.album = v.trim(); break;
                    case "LYRIC": case "LYRICS": case "UNSYNCEDLYRICS":
                        if (r.lyric == null) r.lyric = v;
                        break;
                    case "METADATA_BLOCK_PICTURE":
                        if (r.picture == null) {
                            try {
                                byte[] block = androidBase64Decode(v.trim());
                                if (block != null && block.length > 0) {
                                    parseFlacPicture(block, 0, block.length, r);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                        break;
                    default: break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** FLAC PICTURE 块：类型(4)＋mime 串＋描述＋尺寸 16 字节＋数据 */
    private static void parseFlacPicture(byte[] buf, int body, int blockLen, Result r) {
        try {
            int end = body + blockLen;
            int p = body;
            if (p + 4 > end) return;
            p += 4; // 图片类型（3=封面）
            if (p + 4 > end) return;
            int mimeLen = be32(buf, p);
            p += 4;
            if (mimeLen < 0 || p + mimeLen + 4 > end) return;
            String mime = new String(buf, p, mimeLen, StandardCharsets.ISO_8859_1).trim().toLowerCase();
            p += mimeLen;
            int descLen = be32(buf, p);
            p += 4;
            if (descLen < 0 || p + descLen + 16 + 4 > end) return;
            p += descLen + 16;
            int dataLen = be32(buf, p);
            p += 4;
            if (dataLen <= 0 || p + dataLen > end) return;
            byte[] pic = new byte[dataLen];
            System.arraycopy(buf, p, pic, 0, dataLen);
            r.picture = pic;
            r.pictureMime = mime;
        } catch (Throwable ignored) {
        }
    }

    // ================= Ogg =================

    private static Result parseOgg(byte[] buf, int len, boolean moreAvailable) {
        Result r = new Result();
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        int pos = 0;
        boolean seenHeader = false;
        boolean truncated = false;
        int guard = 0;
        while (pos + 27 <= len && guard++ < 512) {
            if (!(buf[pos] == 'O' && buf[pos + 1] == 'g' && buf[pos + 2] == 'g' && buf[pos + 3] == 'S')) break;
            int nSeg = buf[pos + 26] & 0xFF;
            if (pos + 27 + nSeg > len) {
                truncated = true;
                break;
            }
            long payloadLen = 0;
            for (int i = 0; i < nSeg; i++) payloadLen += buf[pos + 27 + i] & 0xFF;
            if (pos + 27 + nSeg + payloadLen > len) {
                truncated = true;
                break;
            }
            int p = pos + 27 + nSeg;
            for (int i = 0; i < nSeg; i++) {
                int segLen = buf[pos + 27 + i] & 0xFF;
                if (segLen > 0) {
                    packet.write(buf, p, segLen);
                    p += segLen;
                    if (packet.size() > 1024 * 1024) {
                        r.status = COMPLETE;
                        return r;
                    }
                }
                if (segLen == 255) continue;
                byte[] pkt = packet.toByteArray();
                packet.reset();
                if (parseOggCommentPacket(pkt, r)) {
                    r.status = COMPLETE;
                    return r;
                }
                if (isOggHeaderPacket(pkt)) {
                    seenHeader = true;
                    continue;
                }
                if (seenHeader) {
                    r.status = COMPLETE;
                    return r;
                }
            }
            pos += 27 + nSeg + (int) payloadLen;
        }
        r.status = truncated && moreAvailable ? NEED_MORE : (r.anyMeta() || seenHeader ? COMPLETE : NO_TAG);
        return r;
    }

    private static boolean isOggHeaderPacket(byte[] p) {
        return p.length >= 7 && ((p[0] == 1 && p[1] == 'v' && p[2] == 'o' && p[3] == 'r' && p[4] == 'b' && p[5] == 'i' && p[6] == 's')
                || (p.length >= 8 && p[0] == 'O' && p[1] == 'p' && p[2] == 'u' && p[3] == 's'
                && p[4] == 'H' && p[5] == 'e' && p[6] == 'a' && p[7] == 'd'));
    }

    private static boolean parseOggCommentPacket(byte[] p, Result r) {
        int off = -1;
        if (p.length >= 7 && p[0] == 3 && p[1] == 'v' && p[2] == 'o' && p[3] == 'r' && p[4] == 'b' && p[5] == 'i' && p[6] == 's') off = 7;
        else if (p.length >= 8 && p[0] == 'O' && p[1] == 'p' && p[2] == 'u' && p[3] == 's'
                && p[4] == 'T' && p[5] == 'a' && p[6] == 'g' && p[7] == 's') off = 8;
        if (off < 0) return false;
        parseVorbisComment(p, off, p.length - off, r);
        return true;
    }

    // ================= MP4 =================

    private static Result parseMp4(byte[] buf, int len, boolean moreAvailable) {
        Result r = new Result();
        // 顶层找 moov（头部窗口内；moov 在尾的文件头读不到，如实 NO_TAG）
        long[] moov = findBox(buf, 0, len, "moov");
        if (moov == null) {
            r.status = moreAvailable ? NEED_MORE : NO_TAG;
            return r;
        }
        r.status = COMPLETE;
        long[] mvhd = findBox(buf, (int) moov[0], (int) moov[1], "mvhd");
        if (mvhd != null && mvhd[1] - mvhd[0] >= 20) {
            int s = (int) mvhd[0];
            int version = buf[s] & 0xFF;
            try {
                long timescale, duration;
                if (version == 1 && mvhd[1] - mvhd[0] >= 32) {
                    timescale = be32u(buf, s + 20);
                    duration = be64(buf, s + 24);
                } else {
                    timescale = be32u(buf, s + 12);
                    duration = be32u(buf, s + 16);
                }
                if (timescale > 0 && duration > 0) r.durationMs = duration * 1000 / timescale;
            } catch (Throwable ignored) {
            }
        }
        long[] udta = findBox(buf, (int) moov[0], (int) moov[1], "udta");
        if (udta == null) return r;
        long[] meta = findBox(buf, (int) udta[0], (int) udta[1], "meta");
        if (meta == null) return r;
        long[] ilst = findBox(buf, (int) meta[0] + 4, (int) meta[1], "ilst");
        if (ilst == null) return r;
        String[][] items = {
                {"\u00a9nam", "title"}, {"\u00a9ART", "artist"}, {"aART", "artist"},
                {"\u00a9alb", "album"}, {"\u00a9lyr", "lyric"}, {"covr", "cover"}};
        for (String[] it : items) {
            long[] box = findBox(buf, (int) ilst[0], (int) ilst[1], it[0]);
            if (box == null) continue;
            long[] data = findBox(buf, (int) box[0], (int) box[1], "data");
            if (data == null) continue;
            int s = (int) data[0] + 8;
            int e = (int) data[1];
            if (e <= s) continue;
            if (it[1].equals("cover")) {
                if (r.picture == null) {
                    byte[] pic = new byte[e - s];
                    System.arraycopy(buf, s, pic, 0, pic.length);
                    r.picture = pic;
                    r.pictureMime = pic.length >= 3 && (pic[0] & 0xFF) == 0x89 ? "image/png" : "image/jpeg";
                }
            } else {
                String v = new String(buf, s, e - s, StandardCharsets.UTF_8).trim();
                if (v.isEmpty()) continue;
                switch (it[1]) {
                    case "title": if (r.title == null) r.title = v; break;
                    case "artist": if (r.artist == null) r.artist = v; break;
                    case "album": if (r.album == null) r.album = v; break;
                    case "lyric": if (r.lyric == null) r.lyric = v; break;
                    default: break;
                }
            }
        }
        return r;
    }

    /** 在 buf[start,end) 内找 type 盒子，返回内容区 [contentStart, contentEnd)（相对 buf 下标），没有返回 null */
    private static long[] findBox(byte[] buf, int start, int end, String type) {
        byte[] want = type.getBytes(StandardCharsets.ISO_8859_1);
        long pos = start;
        int guard = 0;
        while (pos + 8 <= end && guard++ < 10000) {
            long size = be32u(buf, (int) pos);
            long header = 8;
            if (size == 1) {
                if (pos + 16 > end) return null;
                size = be64(buf, (int) pos + 8);
                header = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < header || pos + size > end) return null;
            if (buf[(int) pos + 4] == want[0] && buf[(int) pos + 5] == want[1]
                    && buf[(int) pos + 6] == want[2] && buf[(int) pos + 7] == want[3]) {
                return new long[]{pos + header, pos + size};
            }
            pos += size;
        }
        return null;
    }

    // ================= 通用小工具 =================

    private static int synchsafe(byte[] b, int off) {
        return ((b[off] & 0x7F) << 21) | ((b[off + 1] & 0x7F) << 14)
                | ((b[off + 2] & 0x7F) << 7) | (b[off + 3] & 0x7F);
    }

    private static byte[] deunsync(byte[] tag) {
        byte[] out = new byte[tag.length];
        int w = 0;
        for (int i = 0; i < tag.length; i++) {
            out[w++] = tag[i];
            if (tag[i] == (byte) 0xFF && i + 1 < tag.length && tag[i + 1] == 0x00) i++;
        }
        byte[] r = new byte[w];
        System.arraycopy(out, 0, r, 0, w);
        return r;
    }

    private static boolean isFrameId(byte[] tag, int off, int idLen) {
        if (off + idLen > tag.length) return false;
        for (int i = 0; i < idLen; i++) {
            byte c = tag[off + i];
            if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) return false;
        }
        return true;
    }

    private static int findTerminator(byte[] tag, int from, int end, int enc) {
        if (enc == 0 || enc == 3) {
            for (int i = from; i < end; i++) if (tag[i] == 0) return i;
            return -1;
        }
        for (int i = from; i + 1 < end; i += 2) {
            if (tag[i] == 0 && tag[i + 1] == 0) return i;
        }
        return -1;
    }

    private static String decode(byte[] tag, int off, int len, int enc) {
        try {
            if (len <= 0) return "";
            switch (enc) {
                case 0:
                    return new String(tag, off, len, StandardCharsets.ISO_8859_1);
                case 2:
                    return new String(tag, off, len, StandardCharsets.UTF_16BE);
                case 3:
                    return new String(tag, off, len, StandardCharsets.UTF_8);
                default: {
                    if (len >= 2 && tag[off] == (byte) 0xFF && tag[off + 1] == (byte) 0xFE) {
                        return new String(tag, off + 2, len - 2, StandardCharsets.UTF_16LE);
                    }
                    if (len >= 2 && tag[off] == (byte) 0xFE && tag[off + 1] == (byte) 0xFF) {
                        return new String(tag, off + 2, len - 2, StandardCharsets.UTF_16BE);
                    }
                    return new String(tag, off, len, StandardCharsets.UTF_16LE);
                }
            }
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static int be32(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static long be32u(byte[] b, int off) {
        return be32(b, off) & 0xFFFFFFFFL;
    }

    private static long be64(byte[] b, int off) {
        return (be32u(b, off) << 32) | be32u(b, off + 4);
    }

    /** Base64 解码（纯 Java 实现，避免给本类挂安卓依赖；Ogg 的 METADATA_BLOCK_PICTURE 用） */
    private static byte[] androidBase64Decode(String s) {
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '+' || c == '/' || c == '=') clean.append(c);
        }
        String in = clean.toString();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int acc = 0, accBits = 0;
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            if (c == '=') break;
            int v;
            if (c >= 'A' && c <= 'Z') v = c - 'A';
            else if (c >= 'a' && c <= 'z') v = c - 'a' + 26;
            else if (c >= '0' && c <= '9') v = c - '0' + 52;
            else if (c == '+') v = 62;
            else v = 63;
            acc = (acc << 6) | v;
            accBits += 6;
            if (accBits >= 8) {
                accBits -= 8;
                out.write((acc >> accBits) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
