package com.demo.githubcopilotwithcursor.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.demo.githubcopilotwithcursor.cursor.CursorAuth;
import com.demo.githubcopilotwithcursor.cursor.dto.CursorIdentity;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.github.GitHubAuth;
import com.demo.githubcopilotwithcursor.github.GitHubAuthenticatedUser;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import com.demo.githubcopilotwithcursor.service.WorkspaceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "app.github.token=test-token",
    "app.cursor.api-key=test-cursor-key",
    "app.workspace.root=build/tmp/workbench-view-validation",
    "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
class WorkbenchViewValidationIntegrationTest {

    private static final Path WORKSPACE_ROOT =
        Path.of("build", "tmp", "workbench-view-validation").toAbsolutePath().normalize();
    private static final String SAMPLE_OWNER = "sample-owner";
    private static final String SAMPLE_REPO = "sample-repo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RepositoryWorkspaceRepository repository;

    @Autowired
    private WorkspaceService workspaceService;

    private String duplicateRepoOwner;
    private String duplicateRepoName;

    @AfterEach
    void cleanupDuplicateWorkspace() {
        if (duplicateRepoOwner == null || duplicateRepoName == null) {
            return;
        }
        Optional<RepositoryWorkspace> workspace =
            repository.findByRepoOwnerAndRepoName(duplicateRepoOwner, duplicateRepoName);
        workspace.ifPresent(ignored -> {
            try {
                workspaceService.deleteWorkspace(duplicateRepoOwner, duplicateRepoName);
            } catch (RuntimeException ignoredException) {
                // Windows JGit pack files can remain locked briefly after the test.
            }
        });
        duplicateRepoOwner = null;
        duplicateRepoName = null;
    }

    @Test
    void cloneRejectsBlankRepoUrl() throws Exception {
        mockMvc.perform(post("/web/clone")
                .param("repoUrl", "   ")
                .param("agentPrompt", "test")
                .param("mode", "REVIEW"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web"))
            .andExpect(flash().attribute("errorMessage", "GitHub 저장소 URL을 입력하세요."));
    }

    @Test
    void cloneRejectsNonHttpsRepoUrl() throws Exception {
        mockMvc.perform(post("/web/clone")
                .param("repoUrl", "http://github.com/octocat/Hello-World")
                .param("agentPrompt", "test")
                .param("mode", "REVIEW"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web"))
            .andExpect(flash().attribute("errorMessage", "GitHub 저장소 URL 형식이 올바르지 않습니다."))
            .andExpect(flash().attribute("showRepoUrlHintPopup", true));
    }

    @Test
    void commitPushRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/web/workspaces/" + SAMPLE_OWNER + "/" + SAMPLE_REPO + "/commit-push")
                .param("message", "   ")
                .param("authorName", "Tester")
                .param("authorEmail", "tester@example.com"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + SAMPLE_OWNER + "/" + SAMPLE_REPO + "/commit"))
            .andExpect(flash().attribute("errorMessage", "message: 커밋 메시지를 입력하세요."));
    }

    @Test
    void commitPushRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/web/workspaces/" + SAMPLE_OWNER + "/" + SAMPLE_REPO + "/commit-push")
                .param("message", "Fix bug")
                .param("authorName", "Tester")
                .param("authorEmail", "not-an-email"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + SAMPLE_OWNER + "/" + SAMPLE_REPO + "/commit"))
            .andExpect(flash().attribute("errorMessage", "authorEmail: 올바른 이메일 주소를 입력하세요."));
    }

    @Test
    void contributeCloneRejectsBlankRepoUrl() throws Exception {
        mockMvc.perform(post("/web/clone")
                .param("repoUrl", "   ")
                .param("agentPrompt", "test")
                .param("mode", "CONTRIBUTE"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web"))
            .andExpect(flash().attribute("errorMessage", "GitHub 저장소 URL을 입력하세요."));
    }

    @Test
    void cloneDuplicateWorkspaceShowsDeleteGuidance() throws Exception {
        duplicateRepoOwner = "octocat";
        duplicateRepoName = "dup-ws-" + UUID.randomUUID().toString().substring(0, 8);
        String repoUrl = "https://github.com/" + duplicateRepoOwner + "/" + duplicateRepoName;
        Path workspacePath = WORKSPACE_ROOT.resolve(duplicateRepoOwner).resolve(duplicateRepoName);
        Files.createDirectories(workspacePath);

        String headSha;
        try (Git git = Git.init().setDirectory(workspacePath.toFile()).call()) {
            Files.writeString(workspacePath.resolve("README.md"), "# test\n");
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("init").setAuthor("Test", "test@example.com").call();
            ObjectId head = git.getRepository().resolve("HEAD");
            headSha = head.name();
        }

        repository.save(new RepositoryWorkspace(
            duplicateRepoOwner,
            duplicateRepoName,
            repoUrl,
            workspacePath.toString(),
            headSha
        ));

        mockMvc.perform(post("/web/clone")
                .param("repoUrl", repoUrl)
                .param("agentPrompt", "test")
                .param("mode", "REVIEW"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web"))
            .andExpect(flash().attribute(
                "errorMessage",
                "워크스페이스 '" + duplicateRepoOwner + "/" + duplicateRepoName + "'가 이미 있습니다. 아래 목록에서 삭제한 뒤 다시 시도하세요."
            ))
            .andExpect(flash().attribute("conflictRepoOwner", duplicateRepoOwner))
            .andExpect(flash().attribute("conflictRepoName", duplicateRepoName));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthTestOverrides {

        @Bean
        @Primary
        GitHubAuth gitHubAuth() {
            GitHubAuth auth = mock(GitHubAuth.class);
            when(auth.isContributeEnabled()).thenReturn(true);
            when(auth.requireAuthenticatedUser())
                .thenReturn(new GitHubAuthenticatedUser("octocat", "Octocat", "octocat@example.com"));
            return auth;
        }

        @Bean
        @Primary
        CursorAuth cursorAuth() {
            CursorAuth auth = mock(CursorAuth.class);
            when(auth.isCursorEnabled()).thenReturn(true);
            when(auth.requireApiKey()).thenReturn(new CursorIdentity("42", "dev@example.com"));
            return auth;
        }
    }

    @Test
    void createPrRejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/web/workspaces/" + SAMPLE_OWNER + "/" + SAMPLE_REPO + "/create-pr")
                .param("title", "   ")
                .param("body", "Summary")
                .param("draft", "true"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + SAMPLE_OWNER + "/" + SAMPLE_REPO + "/pr"))
            .andExpect(flash().attribute("errorMessage", "PR 제목을 입력하세요."));
    }
}
