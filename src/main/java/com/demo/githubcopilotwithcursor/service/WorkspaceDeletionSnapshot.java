package com.demo.githubcopilotwithcursor.service;

import java.nio.file.Path;

public record WorkspaceDeletionSnapshot(String repoOwner, String repoName, Path savedPath) {

    public String label() {
        return repoOwner + "/" + repoName;
    }
}
