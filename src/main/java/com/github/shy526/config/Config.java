package com.github.shy526.config;

import lombok.Data;

@Data
public class Config {
    public Config() {
        this.owner = System.getenv("OWNER");
        this.repo = System.getenv("REPO");
        this.githubApiToken = System.getenv("MY_GITHUB_API_TOKEN");
    }

    private String owner;
    private String repo;
    private String githubApiToken;
}
