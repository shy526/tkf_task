package com.github.shy526.service;

import com.alibaba.fastjson.JSONObject;
import com.github.shy526.vo.GithubVo;

public interface GithubRestService {

    /**
     * 修改或创建文件
     * @param vo vo
     */
    JSONObject createOrUpdateFile(GithubVo vo);
    JSONObject getContent(GithubVo vo);
}
