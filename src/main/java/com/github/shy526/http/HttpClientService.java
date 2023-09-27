package com.github.shy526.http;

import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Map;

/**
 * HttpClientService 实际实现类
 *
 * @author shy526
 */
public class HttpClientService {
    private final CloseableHttpClient httpClient;

    private final RequestConfig requestConfig;


    public HttpClientService(CloseableHttpClient httpClient, RequestConfig requestConfig) {
        this.httpClient = httpClient;
        this.requestConfig = requestConfig;
    }

    /**
     * get请求
     *
     * @param url    url
     * @param params 参数
     * @param header 请求头
     * @return HttpResult
     */
    public HttpResult get(String url, Map<String, String> params, Map<String, String> header) {
        RequestPack requestPack = RequestPack.produce(url, params, HttpGet.class).setHeader(header);
        return this.execute(requestPack);
    }

    /**
     * get请求
     *
     * @param url    url
     * @param params 参数
     * @return HttpResult
     */
    public HttpResult get(String url, Map<String, String> params) {
        return this.get(url, params, null);
    }

    /**
     * get请求
     *
     * @param url url
     * @return HttpResult
     */
    public HttpResult get(String url) {
        return this.get(url, null, null);
    }

    /**
     * 执行POST请求
     *
     * @param url    url
     * @param params 表单参数
     * @param header 请求头
     * @param format 表单
     * @param encode 表单编码
     * @return HttpResult
     */
    public HttpResult post(String url, Map<String, String> params, Map<String, String> header,
                           Map<String, String> format, String encode, String bodyStr) {
        RequestPack requestPack = RequestPack.produce(url, params, HttpPost.class).setFormat(format, encode)
                .setHeader(header).setBodyStr(bodyStr);
        return execute(requestPack);
    }

    /**
     * 执行POST请求
     *
     * @param url url
     * @return HttpResult
     */
    public HttpResult post(String url) {
        return post(url, null, null, null, null, null);
    }


    /**
     * 执行POST请求
     *
     * @param url url
     * @return HttpResult
     */
    public HttpResult post(String url, Map<String, String> params) {
        return post(url, params, null, null, null, null);
    }

    /**
     * 执行POST请求
     *
     * @param url    url
     * @param params 表单参数
     * @param header 请求头
     * @return HttpResult
     */
    public HttpResult post(String url, Map<String, String> params, Map<String, String> header) {
        return post(url, params, header, null, null, null);
    }

    /**
     * 执行POST请求
     *
     * @param url    url
     * @param header 请求头
     * @param format 表单
     * @param encode 表单编码
     * @return HttpResult
     */
    public HttpResult postFormat(String url, Map<String, String> header, Map<String, String> format, String encode) {
        return post(url, null, header, format, encode, null);
    }


    /**
     * 执行POST请求
     *
     * @param url    url
     * @param format 表单
     * @param encode 表单编码
     * @return HttpResult
     */
    public HttpResult postFormat(String url, Map<String, String> format, String encode) {
        return post(url, null, null, format, encode, null);
    }

    /**
     * 执行POST请求
     *
     * @param url     url
     * @param bodyStr body
     * @return HttpResult
     */
    public HttpResult postStr(String url, String bodyStr) {
        return post(url, null, null, null, null, bodyStr);
    }


    /**
     * 执行POST请求
     *
     * @param url     url
     * @param bodyStr body
     * @return HttpResult
     */
    public HttpResult postStr(String url, Map<String, String> header, String bodyStr) {
        return post(url, null, header, null, null, bodyStr);
    }

    /**
     * 执行提交
     *
     * @param requestPack requestPack
     * @return HttpResult
     */
    public HttpResult execute(RequestPack requestPack) {
        HttpHost proxy = requestPack.getProxy();
        if (proxy==null){
            requestPack.setRequestConfig(this.requestConfig);
        }else {
            RequestConfig.Builder reqConfigBuilder = RequestConfig.copy(requestConfig);
            reqConfigBuilder.setProxy(proxy);
            requestPack.setRequestConfig(reqConfigBuilder.build());
        }
        HttpClientContext context = requestPack.getContext();
        HttpRequestBase requestBase = requestPack.getRequestBase();
        if (context==null){
            return execute(requestBase);
        }else {
            return  execute(requestBase,context);
        }

    }


    /**
     * 执行提交
     *
     * @param requestBase requestBase
     * @return HttpResult
     */
    public HttpResult execute(HttpRequestBase requestBase) {
        HttpResult result = null;
        try {
            result = new HttpResult(httpClient.execute(requestBase), requestBase);
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpException(e.getMessage(),e);
        }
        return result;
    }

    /**
     * 执行提交
     *
     * @param requestBase requestBase
     * @return HttpResult
     */
    public HttpResult execute(HttpRequestBase requestBase, HttpContext context) {
        HttpResult result = null;
        try {
            result = new HttpResult(httpClient.execute(requestBase,context), requestBase);
        } catch (Exception e) {
            e.printStackTrace();
            throw new HttpException(e.getMessage(),e);
        }
        return result;
    }


    /**
     * 上传文件
     *
     * @param fileUpLoadName         长传文件名称
     * @param file                   file
     * @param multipartEntityBuilder multipartEntityBuilder null时自动创建
     * @return MultipartEntityBuilder
     */
    public MultipartEntityBuilder buildFile(String fileUpLoadName, File file, MultipartEntityBuilder multipartEntityBuilder) {
        if (multipartEntityBuilder == null) {
            multipartEntityBuilder = MultipartEntityBuilder.create();
        }
        try {
            multipartEntityBuilder.addBinaryBody(fileUpLoadName, new FileInputStream(file), ContentType.MULTIPART_FORM_DATA, file.getName());
        } catch (FileNotFoundException e) {
            throw new HttpException(e);
        }
        return multipartEntityBuilder;
    }

    public RequestConfig getRequestConfig() {
        return requestConfig;
    }
}
