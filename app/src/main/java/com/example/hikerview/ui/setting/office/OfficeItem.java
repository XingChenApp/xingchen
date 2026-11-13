package com.example.hikerview.ui.setting.office;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 照官 8.83 本体移植（jadx 官包源）：设置菜单条目（标题/选中态/描述），Parcelable 原样。
 */
public class OfficeItem implements Parcelable {

    public static final Parcelable.Creator<OfficeItem> CREATOR = new Parcelable.Creator<OfficeItem>() {
        @Override
        public OfficeItem createFromParcel(Parcel parcel) {
            return new OfficeItem(parcel);
        }

        @Override
        public OfficeItem[] newArray(int i) {
            return new OfficeItem[i];
        }
    };

    private String desc;
    private int selected;
    private String title;

    @Override
    public int describeContents() {
        return 0;
    }

    public OfficeItem(String str) {
        this.title = str;
        this.selected = 0;
    }

    public OfficeItem(String str, int i) {
        this.title = str;
        this.selected = i;
    }

    public OfficeItem(String str, String str2) {
        this.title = str;
        this.desc = str2;
    }

    public OfficeItem(String str, int i, String str2) {
        this.title = str;
        this.selected = i;
        this.desc = str2;
    }

    protected OfficeItem(Parcel parcel) {
        this.title = parcel.readString();
        this.selected = parcel.readInt();
        this.desc = parcel.readString();
    }

    public int getSelected() {
        return this.selected;
    }

    public void setSelected(int i) {
        this.selected = i;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String str) {
        this.title = str;
    }

    public String getDesc() {
        return this.desc;
    }

    public void setDesc(String str) {
        this.desc = str;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.title);
        parcel.writeInt(this.selected);
        parcel.writeString(this.desc);
    }
}
