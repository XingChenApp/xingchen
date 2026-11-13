package com.example.hikerview.service.exception;

/**
 * 照官 8.83 本体逐字转写（官源 smali/com/example/hikerview/service/exception/ParseException.smali，
 * 全类仅一构造）。①团按需转写：PageParser.parsePageRule/getNextPage 的 throws 面。
 */
public class ParseException extends Exception {

    public ParseException(String message) {
        super(message);
    }
}
