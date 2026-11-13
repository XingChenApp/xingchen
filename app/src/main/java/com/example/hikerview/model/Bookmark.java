package com.example.hikerview.model;

import com.alibaba.fastjson.annotation.JSONField;
import com.example.hikerview.utils.StringUtil;

/**
 * 照官 8.83 本体移植（jadx 官包源）：书签/文件夹条目模型。
 * 官类继承 LitePalSupport（其 ORM），星辰无 LitePal——此处落为纯模型：
 * id 改自有字段（官 getId() 取 LitePal 的 baseObjId），字段与访问器、
 * fastjson 序列化口径与官逐字一致；LitePal 的存取静态面不在此模型职责内。
 */
public class Bookmark implements Comparable<Bookmark> {

    private long id;

    @JSONField(deserialize = false, serialize = false)
    private int childSize;

    @JSONField(deserialize = false, serialize = false)
    private int dir;

    private boolean dirTag;
    private String group;
    private String icon;
    private int order;

    @JSONField(deserialize = false, serialize = false)
    private Bookmark parent;
    private long parentId;

    @JSONField(deserialize = false, serialize = false)
    private boolean privacyModeUnlocked;

    @JSONField(deserialize = false, serialize = false)
    private boolean selected;

    @JSONField(deserialize = false, serialize = false)
    private boolean selecting;
    private String title;
    private String url;

    public long getId() {
        return this.id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String str) {
        this.title = str;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String str) {
        this.url = str;
    }

    public int getOrder() {
        return this.order;
    }

    public void setOrder(int i) {
        this.order = i;
    }

    @Override
    public int compareTo(Bookmark bookmark) {
        if (StringUtil.isEmpty(bookmark.getUrl()) && StringUtil.isEmpty(getUrl())) {
            int order = getOrder() - bookmark.getOrder();
            return order != 0 ? order : getTitle().compareTo(bookmark.getTitle());
        }
        if (StringUtil.isEmpty(getUrl())) {
            return -1;
        }
        if (StringUtil.isEmpty(bookmark.getUrl())) {
            return 1;
        }
        int order2 = getOrder() - bookmark.getOrder();
        return order2 == 0 ? (int) (getId() - bookmark.getId()) : order2;
    }

    public String getGroup() {
        if (this.group == null) {
            this.group = "";
        }
        return this.group;
    }

    public void setGroup(String str) {
        this.group = str;
    }

    public boolean isDir() {
        return this.dir > 0;
    }

    public void setDir(boolean z) {
        this.dir = z ? 1 : 0;
    }

    public int getDir() {
        return this.dir;
    }

    public void setDir(int i) {
        this.dir = i;
    }

    public long getParentId() {
        return this.parentId;
    }

    public void setParentId(long j) {
        this.parentId = j;
    }

    public Bookmark getParent() {
        return this.parent;
    }

    public void setParent(Bookmark bookmark) {
        this.parent = bookmark;
    }

    public boolean isDirTag() {
        return this.dirTag;
    }

    public void setDirTag(boolean z) {
        this.dirTag = z;
    }

    public String getIcon() {
        return this.icon;
    }

    public void setIcon(String str) {
        this.icon = str;
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setSelected(boolean z) {
        this.selected = z;
    }

    public boolean isSelecting() {
        return this.selecting;
    }

    public void setSelecting(boolean z) {
        this.selecting = z;
    }

    public boolean isPrivacyModeUnlocked() {
        return this.privacyModeUnlocked;
    }

    public void setPrivacyModeUnlocked(boolean z) {
        this.privacyModeUnlocked = z;
    }

    public int getChildSize() {
        return this.childSize;
    }

    public void setChildSize(int i) {
        this.childSize = i;
    }
}
