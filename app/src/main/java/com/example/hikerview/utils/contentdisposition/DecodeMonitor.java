package com.example.hikerview.utils.contentdisposition;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * DecodeMonitor.smali 等同名 smali 共 101 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 * 含匿名类 DecodeMonitor$1（STRICT，官 smali 独立文件 DecodeMonitor$1.smali）随字段初始化落码。
 */
public class DecodeMonitor {
    public static final DecodeMonitor STRICT = new DecodeMonitor() { // from class: com.example.hikerview.utils.contentdisposition.DecodeMonitor.1
        @Override // com.example.hikerview.utils.contentdisposition.DecodeMonitor
        public boolean isListening() {
            return true;
        }

        @Override // com.example.hikerview.utils.contentdisposition.DecodeMonitor
        public boolean warn(String str, String str2) {
            return true;
        }
    };
    public static final DecodeMonitor SILENT = new DecodeMonitor();

    public boolean isListening() {
        return false;
    }

    public boolean warn(String str, String str2) {
        return false;
    }
}
