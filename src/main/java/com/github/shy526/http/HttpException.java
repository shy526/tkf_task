package com.github.shy526.http;


/**
 * http异常
 *
 * @author shy526
 */
public class HttpException  extends AbsException {

    public HttpException(Throwable exception) {
        super(exception);
    }

    public HttpException(String message, Throwable exception) {
        super(message, exception);
    }

    public HttpException(String message, Throwable cause, Throwable exception) {
        super(message, cause, exception);
    }
}
