package com.github.shy526.config;

import com.alibaba.fastjson.JSONObject;
import com.github.shy526.http.HttpClientFactory;
import com.github.shy526.http.HttpClientProperties;
import com.github.shy526.http.HttpClientService;
import com.github.shy526.service.GithubRestServiceImpl;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Context {
    private final static JSONObject context = new JSONObject();

    static {
        HttpClientProperties httpClientProperties = new HttpClientProperties();
        httpClientProperties.setAutomaticRetries(false);
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36 Edg/117.0.2045.43");
        httpClientProperties.setHeader(header);
        setContext(new GithubRestServiceImpl());
        setContext(HttpClientFactory.getHttpClientService(httpClientProperties));
        Config config = new Config();
        log.info("config load :{}",JSONObject.toJSONString(config));
        setContext(config);
    }

    private static void setContext(Object temp) {
        context.put(temp.getClass().getSimpleName(), temp);
    }


    public static <T> T getInstance(Class<T> tclass) {
        T result = context.getObject(tclass.getSimpleName(), tclass);
        if (result == null) {
            log.error("getInstance err : {}", tclass.getSimpleName());
            throw new RuntimeException();
        }
        return result;
    }


}
