package com.demo.githubcopilotwithcursor.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubAuthenticatedUser(
    String login,
    String name,
    String email
) {
}
