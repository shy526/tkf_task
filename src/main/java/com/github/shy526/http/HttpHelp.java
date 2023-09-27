package com.github.shy526.http;

import java.util.HashMap;
import java.util.Map;

public class HttpHelp {
    private static final HttpClientService httpClientService;

    static {
        HttpClientProperties httpClientProperties = new HttpClientProperties();
        httpClientProperties.setAutomaticRetries(false);
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36 Edg/117.0.2045.43");
        httpClientProperties.setHeader(header);
        httpClientService = HttpClientFactory.getHttpClientService(httpClientProperties);
    }

    public static HttpClientService getInstance() {
        return httpClientService;
    }
}
