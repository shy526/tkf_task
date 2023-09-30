package com.github.shy526.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.shy526.config.Config;
import com.github.shy526.config.Context;
import com.github.shy526.http.HttpClientService;
import com.github.shy526.http.HttpResult;
import com.github.shy526.http.RequestPack;
import com.github.shy526.vo.Committer;
import com.github.shy526.vo.GithubVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpPut;

import java.util.HashMap;
import java.util.Map;


@Slf4j
public class GithubRestServiceImpl implements GithubRestService {
    private static final HttpClientService httpClientService = Context.getInstance(HttpClientService.class);
    private static final String CREATE_UPDATE_PATH = "/repos/%s/%s/contents/%s";
    private static final String GITHUB_HOST = "https://api.github.com%s";
    private static final Map<String, String> HEADER = new HashMap<>();

    static {
        HEADER.put("Accept", "application/vnd.github+json");
        HEADER.put("Host", "api.github.com");
        String token = Context.getInstance(Config.class).getGithubApiToken();
        if (StringUtils.isEmpty(token)) {
            log.error("github_api_token is null");
        } else {
            HEADER.put("Authorization", "token " + token);
        }

    }

    @Override
    public JSONObject createOrUpdateFile(GithubVo vo) {
        if (!HEADER.containsKey("Authorization")) {
            log.error("github_api_token is null");
            return new JSONObject();
        }
        String path = String.format(CREATE_UPDATE_PATH, vo.getOwner(), vo.getRepo(), vo.getPath());
        String url = String.format(GITHUB_HOST, path);
        JSONObject old = getContent(vo);
        if (old!=null) {
            vo.setSha(old.getString("sha"));
        }
        RequestPack produce = RequestPack.produce(url, null, HttpPut.class);
        String content = vo.getContent();
        vo.setContent(Base64.encodeBase64String(content.getBytes()));
        RequestPack requestPack = produce.setBodyStr(JSON.toJSONString(vo));
        requestPack.setHeader(HEADER);
        try (HttpResult execute = httpClientService.execute(requestPack)) {
            return JSONObject.parseObject(execute.getEntityStr());
        } catch (Exception ignored) {
        }
        return new JSONObject();
    }

    @Override
    public JSONObject getContent(GithubVo vo) {
        String path = String.format(CREATE_UPDATE_PATH, vo.getOwner(), vo.getRepo(), vo.getPath());
        String url = String.format(GITHUB_HOST, path);
        try (HttpResult httpResult = httpClientService.get(url)) {
            return JSONObject.parseObject(httpResult.getEntityStr());
        } catch (Exception ignored) {

        }
        return new JSONObject();
    }
}
