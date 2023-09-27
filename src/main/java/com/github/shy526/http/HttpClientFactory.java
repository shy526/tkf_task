package com.github.shy526.http;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HttpClientService 工厂类
 *
 * @author shy526
 */
public class HttpClientFactory {


    public static HttpClientService getHttpClientService(CloseableHttpClient httpClient, RequestConfig requestConfig) {
        return new HttpClientService(httpClient, requestConfig);
    }

    public static HttpClientService getHttpClientService(HttpClientProperties properties) {
        return new HttpClientService(getHttpClient(properties), getRequestConfig(properties));
    }

    public static PoolingHttpClientConnectionManager getHttpClientConnectionManager(HttpClientProperties properties) {
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.
                <ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", getSslConnectionSocketFactory()).build();
        PoolingHttpClientConnectionManager httpClientConnectionManager = new PoolingHttpClientConnectionManager(registry);
        //最大连接数
        httpClientConnectionManager.setMaxTotal(properties.getMaxTotal());
        //并发数
        httpClientConnectionManager.setDefaultMaxPerRoute(properties.getDefaultMaxPerRoute());
        httpClientConnectionManager.setValidateAfterInactivity(properties.getValidateAfterInactivity());
        if (properties.getCloseTask() != null) {
            CloseExpiredConnectionsTask.start(httpClientConnectionManager, properties.getCloseTask());

        }
        return httpClientConnectionManager;
    }

    /**
     * https 支持
     *
     * @return SSLConnectionSocketFactory
     */
    public static SSLConnectionSocketFactory getSslConnectionSocketFactory() {
        try {
            SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(null, new TrustSelfSignedStrategy()).build();
            return new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        } catch (Exception e) {
            throw new HttpException(e);
        }
    }

    public static CloseableHttpClient getHttpClient(HttpClientProperties properties) {
        PoolingHttpClientConnectionManager manager = getHttpClientConnectionManager(properties);
        SSLConnectionSocketFactory sslFactory = getSslConnectionSocketFactory();
        HttpClientBuilder httpBuilder = getHttpClientBuilder(manager, sslFactory, properties);
        Map<String, String> header = properties.getHeader();
        if (header != null) {
            List<BasicHeader> headers = new ArrayList<>();
            header.forEach((k, v) -> {
                headers.add(new BasicHeader(k, v));
            });
            //设置默认请求头
            httpBuilder.setDefaultHeaders(headers);
        }
        return httpBuilder.build();
    }

    /**
     * 实例化连接池，设置连接池管理器。
     *
     * @param poolManager poolManager
     * @param sslFactory  sslFactory
     * @return HttpClientBuilder
     */
    public static HttpClientBuilder getHttpClientBuilder(PoolingHttpClientConnectionManager poolManager, SSLConnectionSocketFactory sslFactory
            , HttpClientProperties properties) {
        //HttpClientBuilder中的构造方法被protected修饰，所以这里不能直接使用new来实例化一个HttpClientBuilder，
        //可以使用HttpClientBuilder提供的静态方法create()来获取HttpClientBuilder对象
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        httpClientBuilder.setConnectionManager(poolManager);
        httpClientBuilder.setSSLSocketFactory(sslFactory);
        if (!properties.getAutomaticRetries()) {
            httpClientBuilder.disableAutomaticRetries();
        }
        return httpClientBuilder;
    }


    /**
     * Builder是RequestConfig的一个内部类
     * 通过RequestConfig的custom方法来获取到一个Builder对象
     * 设置builder的连接信息
     * 这里还可以设置proxy，cookieSpec等属性。有需要的话可以在此设置
     * 使用builder构建一个RequestConfig对象
     *
     * @param httpClientProperties httpClientProperties
     * @return RequestConfig
     */
    public static RequestConfig getRequestConfig(HttpClientProperties httpClientProperties) {
        RequestConfig.Builder builder = RequestConfig.custom();
        return builder.setConnectTimeout(httpClientProperties.getConnectTimeout())
                .setConnectionRequestTimeout(httpClientProperties.getConnectionRequestTimeout())
                .setSocketTimeout(httpClientProperties.getSocketTimeout())
                .build();
    }
}
