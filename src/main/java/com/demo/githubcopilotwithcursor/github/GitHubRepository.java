package com.demo.githubcopilotwithcursor.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepository(
    @JsonProperty("full_name") String fullName,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("clone_url") String cloneUrl,
    @JsonProperty("default_branch") String defaultBranch
) {
}
