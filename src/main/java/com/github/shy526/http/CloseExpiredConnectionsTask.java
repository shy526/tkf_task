package com.github.shy526.http;


import com.github.shy526.thread.ThreadPoolUtils;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.util.concurrent.TimeUnit;

/**
 * 关闭不活动连接的定时任务
 *
 * @author shy526
 */
public class CloseExpiredConnectionsTask implements Runnable {
    private final HttpClientConnectionManager manager;
    private final HttpClientProperties.CloseTask closeTask;
    private static final Long INITIAL_DELAY = 3000L;

    public CloseExpiredConnectionsTask(HttpClientConnectionManager manager, HttpClientProperties.CloseTask closeTask) {
        this.manager = manager;
        this.closeTask = closeTask;
    }

    public static void start(PoolingHttpClientConnectionManager httpClientConnectionManager, HttpClientProperties.CloseTask closeTask) {
        ThreadPoolUtils.getScheduledExecutor("scheduled-closeTask",1, true)
                .scheduleWithFixedDelay(new CloseExpiredConnectionsTask(httpClientConnectionManager, closeTask),
                        INITIAL_DELAY, closeTask.getDelay()
                        , TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        //关闭失效的连接
        manager.closeExpiredConnections();
        //不活动的连接
        manager.closeIdleConnections(closeTask.getIdleTime(), TimeUnit.MILLISECONDS);
    }
}

