package com.demo.githubcopilotwithcursor.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequest(
    @JsonProperty("html_url") String htmlUrl,
    int number,
    String state,
    boolean draft,
    Boolean merged
) {
}
