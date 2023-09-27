package com.github.shy526.http;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


/**
 * HttpClientService 配置类
 * @author shy526
 */
@Data
public class HttpClientProperties implements Serializable {
    public static final String PREFIX = "http-client-service";
    /**
     * 连接池最大连接数
     */
    private Integer maxTotal = 200;

    /**
     * 默认路由最大连接数
     */
    private Integer defaultMaxPerRoute = 100;

    /**
     * 请求连接超时时间
     */
    private Integer connectTimeout = 30000;

    /**
     * 连接池获取请求超时时间
     */
    private Integer connectionRequestTimeout = 50000;

    /**
     * socket超时时间
     */
    private Integer socketTimeout = 30000;

    /**
     * 空闲永久连接检查间隔
     */
    private Integer validateAfterInactivity = 2000;

    /**
     * 请求头
     */
    private Map<String, String> header = new HashMap<>();

    /**
     * 是否重试
     * true 启用
     */
    private Boolean automaticRetries = Boolean.TRUE;


    private CloseTask closeTask = new CloseTask();



    @Data
    public static class CloseTask {

        public CloseTask() {
        }
        public CloseTask(Integer idleTime, Long delay) {
            this.idleTime = idleTime;
            this.delay = delay;
        }

        /**
         * 清理多少毫秒内部活动的链接
         */
        private Integer idleTime = 3000;
        /**
         * 之后延时的时间
         */
        private Long delay = idleTime.longValue();

    }
}
