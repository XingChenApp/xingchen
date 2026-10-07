package com.fongmi.android.tv.bean;

public class Plugin {
    private String name;
    private String path;
    private boolean enabled;
    private boolean selected;
    private String type; // "py" or "js"

    public Plugin(String name, String path, String type) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.enabled = true;
        this.selected = false;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
