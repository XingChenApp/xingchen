package com.example.hikerview.utils.contentdisposition;

import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 照官转写（官包 smali_classes2/com/example/hikerview/utils/contentdisposition/
 * ContentDispositionHolder.smali 等同名 smali 共 352 行）：官 vendored 内容处置解析库一员，
 * 逐字照官落码；官本体原有的判空/try 形照官保留，不另加守卫/兜底。
 */
public class ContentDispositionHolder {
    private String body;
    private ParseException parseException;
    private boolean parsed = false;
    private String dispositionType = "";
    private final Map<String, String> parameters = new HashMap();

    public ContentDispositionHolder(String str) {
        this.body = str;
    }

    public ParseException getParseException() {
        if (!this.parsed) {
            parse();
        }
        return this.parseException;
    }

    public String getDispositionType() {
        if (!this.parsed) {
            parse();
        }
        return this.dispositionType;
    }

    public String getParameter(String str) {
        if (!this.parsed) {
            parse();
        }
        return this.parameters.get(str.toLowerCase());
    }

    public Map<String, String> getParameters() {
        if (!this.parsed) {
            parse();
        }
        return Collections.unmodifiableMap(this.parameters);
    }

    public boolean isDispositionType(String str) {
        if (!this.parsed) {
            parse();
        }
        return this.dispositionType.equalsIgnoreCase(str);
    }

    public boolean isInline() {
        if (!this.parsed) {
            parse();
        }
        return this.dispositionType.equals(ContentDispositionField.DISPOSITION_TYPE_INLINE);
    }

    public boolean isAttachment() {
        if (!this.parsed) {
            parse();
        }
        return this.dispositionType.equals(ContentDispositionField.DISPOSITION_TYPE_ATTACHMENT);
    }

    public String getFilename() {
        return getParameter(ContentDispositionField.PARAM_FILENAME);
    }

    public long getSize() {
        String parameter = getParameter(ContentDispositionField.PARAM_SIZE);
        if (parameter == null) {
            return -1L;
        }
        try {
            long parseLong = Long.parseLong(parameter);
            if (parseLong < 0) {
                return -1L;
            }
            return parseLong;
        } catch (NumberFormatException unused) {
            return -1L;
        }
    }

    private void parse() {
        ContentDispositionParser contentDispositionParser = new ContentDispositionParser(new StringReader(this.body));
        try {
            contentDispositionParser.parseAll();
        } catch (ParseException e) {
            this.parseException = e;
        } catch (TokenMgrError e2) {
            this.parseException = new ParseException(e2);
        }
        String dispositionType = contentDispositionParser.getDispositionType();
        if (dispositionType != null) {
            this.dispositionType = dispositionType.toLowerCase(Locale.US);
            this.parameters.putAll(contentDispositionParser.getParameters());
        }
        this.parsed = true;
    }
}
