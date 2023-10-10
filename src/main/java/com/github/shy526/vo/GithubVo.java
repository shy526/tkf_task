package com.github.shy526.vo;


import lombok.Data;

import java.io.Serializable;

@Data
public class GithubVo implements Serializable {
    private String owner;
    private String  repo;

    private String path;
    private String message;
    private String content;
    private String sha;
    private Committer committer;
}
