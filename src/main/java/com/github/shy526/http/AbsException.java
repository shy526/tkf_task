package com.github.shy526.http;

/**
 * 自定义异常抽象类
 *
 * @author shy526
 */
public abstract class AbsException extends RuntimeException {

    public AbsException(Throwable exception) {
    }

    public AbsException(String message, Throwable exception) {
        super(message);
    }

    public AbsException(String message, Throwable cause, Throwable exception) {
        super(message, cause);
    }
}
