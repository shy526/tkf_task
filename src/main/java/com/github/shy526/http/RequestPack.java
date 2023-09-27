package com.github.shy526.http;

import org.apache.commons.codec.CharEncoding;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicNameValuePair;

import java.lang.reflect.Constructor;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.commons.codec.CharEncoding.UTF_8;

/**
 * request 包装
 *
 * @author Administrator
 */
public class RequestPack {


    private HttpHost proxy;

    private final HttpRequestBase requestBase;

    private HttpClientContext context;

    public HttpHost getProxy() {
        return proxy;
    }

    public HttpClientContext getContext() {
        return context;
    }

    public HttpRequestBase getRequestBase() {
        return requestBase;
    }

    private static boolean mapIsEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    public RequestPack(HttpRequestBase requestBase) {
        this.requestBase = requestBase;
    }

    public RequestPack setHeader(Map<String, String> header) {
        if (mapIsEmpty(header)) {
            return this;
        }
        header.forEach(requestBase::setHeader);
        return this;
    }


    /**
     * 生成表单
     *
     * @param format 参数
     * @param encode 字符编码
     * @return RequestPack
     */
    public RequestPack setFormat(Map<String, String> format, String encode) {
        if (mapIsEmpty(format)) {
            return this;
        }
        if (!(requestBase instanceof HttpEntityEnclosingRequestBase)) {
            return this;
        }
        if (encode == null || "".equals(encode.trim())) {
            encode = CharEncoding.UTF_8;
        }
        List<NameValuePair> parameters = new ArrayList<>(format.size());
        format.forEach((k, v) -> parameters.add(new BasicNameValuePair(k, v)));

        try {
            ((HttpEntityEnclosingRequestBase) requestBase).setEntity(new UrlEncodedFormEntity(parameters, encode));
        } catch (Exception e) {
            throw new HttpException(e);
        }

        return this;
    }

    public RequestPack setBodyStr(String str) {
        if (str == null || "".equals(str.trim())) {
            return this;
        }
        if (!(requestBase instanceof HttpEntityEnclosingRequestBase)) {
            return this;
        }
        try {
            ((HttpEntityEnclosingRequestBase) requestBase).setEntity(new StringEntity(str));
        } catch (Exception e) {
            throw new HttpException(e);
        }
        return this;
    }

    public RequestPack setRequestConfig(RequestConfig requestConfig) {
        requestBase.setConfig(RequestConfig.copy(requestConfig).build());
        return this;
    }

    /**
     * url参数贬值
     *
     * @param url    url
     * @param params params
     * @return String
     */
    public static String buildUrlParams(String url, Map<String, String> params) {
        if (mapIsEmpty(params)) {
            return url;
        }
        URIBuilder builder;
        try {
            builder = new URIBuilder(url);
            params.forEach(builder::setParameter);
            url = builder.build().toString();
        } catch (URISyntaxException e) {
            throw new HttpException(e);
        }
        return url;
    }


    /**
     * 生成使用代理的设置代理
     *
     * @param hostPort 代理主机名
     * @param scheme   代理端口
     * @param userPas  代理端口
     * @return Message
     */
    public RequestPack setProxy(String hostPort, String scheme, String userPas) {
        String[] temp = hostPort.split(":");
        String hostName = temp[0];
        Integer port = Integer.parseInt(temp[1]);
        scheme = scheme == null ? "http" : scheme;
        this.proxy = new HttpHost(hostName, port, scheme);
        if (userPas != null) {
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            temp = userPas.split(":");
            String userName = temp[0];
            String password = temp[1];
            credsProvider.setCredentials(new AuthScope(proxy), new UsernamePasswordCredentials(userName, password));
            context = HttpClientContext.create();
            context.setAttribute("http.auth.credentials-provider", credsProvider);
        }

        return this;
    }

    /**
     * 生产RequestPack
     *
     * @param url    url
     * @param params params
     * @param tclass tclass
     * @return RequestPack
     */
    public static RequestPack produce(String url, Map<String, String> params, Class<? extends HttpRequestBase> tclass) {
        HttpRequestBase httpRequestBase = null;
        try {
            Constructor<? extends HttpRequestBase> constructor = tclass.getConstructor(String.class);
            httpRequestBase = constructor.newInstance(buildUrlParams(url, params));
        } catch (Exception e) {
            throw new HttpException(e);
        }
        return new RequestPack(httpRequestBase);
    }
}
