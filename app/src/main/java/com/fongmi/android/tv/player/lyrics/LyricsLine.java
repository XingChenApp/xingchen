package com.fongmi.android.tv.player.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LyricsLine {

    private final long timeMs;
    private final String text;
    private final List<LyricsWord> words;
    /** 翻译逐词（M-02：相对行首偏移，见 LyricsWord；空表＝无逐词，走整行字符串回落）。 */
    private List<LyricsWord> translationWords = Collections.emptyList();
    /** 音译逐词（M-02：相对行首偏移；空表＝无逐词，走整行字符串回落）。 */
    private List<LyricsWord> romanizationWords = Collections.emptyList();
    /** 对唱行（M-03：ttm:agent 与上一行不同时为真）。 */
    private boolean isDuet;
    /** 伴唱行（M-03：TTML x-bg span 另起的行）。 */
    private boolean isBG;
    /** 段落（M-05：TTML songPart，空＝无段落标签）。 */
    private String songPart;
    /** 演唱者（M-05：TTML ttm:agent，空＝未知）。 */
    private String agentName;

    public LyricsLine(long timeMs, String text) {
        this(timeMs, text, Collections.emptyList());
    }

    public LyricsLine(long timeMs, String text, List<LyricsWord> words) {
        this.timeMs = Math.max(0, timeMs);
        this.text = text == null ? "" : text.trim();
        this.words = words == null || words.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(words));
    }

    public long getTimeMs() {
        return timeMs;
    }

    public String getText() {
        return text;
    }

    public List<LyricsWord> getWords() {
        return words;
    }

    public boolean hasWords() {
        return !words.isEmpty();
    }

    public List<LyricsWord> getTranslationWords() {
        return translationWords;
    }

    public void setTranslationWords(List<LyricsWord> words) {
        this.translationWords = words == null || words.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(words));
    }

    public List<LyricsWord> getRomanizationWords() {
        return romanizationWords;
    }

    public void setRomanizationWords(List<LyricsWord> words) {
        this.romanizationWords = words == null || words.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(words));
    }

    public boolean isDuet() {
        return isDuet;
    }

    public void setDuet(boolean duet) {
        this.isDuet = duet;
    }

    public boolean isBG() {
        return isBG;
    }

    public void setBG(boolean bg) {
        this.isBG = bg;
    }

    public String getSongPart() {
        return songPart == null ? "" : songPart;
    }

    public void setSongPart(String songPart) {
        this.songPart = songPart == null ? "" : songPart.trim();
    }

    public String getAgentName() {
        return agentName == null ? "" : agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName == null ? "" : agentName.trim();
    }
}
