package com.example.hikerview.utils.contentdisposition;

import java.util.Date;
import java.util.Map;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * ContentDispositionField.smali 等同名 smali 共 69 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public interface ContentDispositionField {
    public static final String DISPOSITION_TYPE_ATTACHMENT = "attachment";
    public static final String DISPOSITION_TYPE_INLINE = "inline";
    public static final String PARAM_CREATION_DATE = "creation-date";
    public static final String PARAM_FILENAME = "filename";
    public static final String PARAM_MODIFICATION_DATE = "modification-date";
    public static final String PARAM_READ_DATE = "read-date";
    public static final String PARAM_SIZE = "size";

    Date getCreationDate();

    String getDispositionType();

    String getFilename();

    Date getModificationDate();

    String getParameter(String str);

    Map<String, String> getParameters();

    ParseException getParseException();

    Date getReadDate();

    long getSize();

    boolean isAttachment();

    boolean isDispositionType(String str);

    boolean isInline();

    boolean isValidField();
}
