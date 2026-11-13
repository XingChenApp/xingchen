package com.fongmi.android.tv.legado;

/** 书源执行器异常：只打断当前源，不许拖垮宿主。 */
public class LegadoException extends RuntimeException {

    public LegadoException(String message) {
        super(message);
    }

    public LegadoException(String message, Throwable cause) {
        super(message, cause);
    }
}
