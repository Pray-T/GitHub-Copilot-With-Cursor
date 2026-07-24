package com.demo.githubcopilotwithcursor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.githubcopilotwithcursor.cursor.CursorAuth;
import com.demo.githubcopilotwithcursor.cursor.dto.CursorIdentity;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.github.GitHubAuth;
import com.demo.githubcopilotwithcursor.github.GitHubAuthenticatedUser;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import com.demo.githubcopilotwithcursor.service.WorkspaceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.MockServerRestClientCustomizer;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.FlashMap;

@SpringBootTest(properties = {
    "app.github.token=test-token",
    "app.cursor.api-key=test-cursor-key",
    "app.cursor.composer.enabled=true",
    "app.workspace.root=build/tmp/pr-metadata-fingerprint-e2e",
    "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
@AutoConfigureMockRestServiceServer
class PrMetadataFingerprintIntegrationTest {

    private static final String UPSTREAM_OWNER = "spring-projects";
    private static final String LOGIN = "octocat";
    private static final String AGENT_ID = "bc-test-001";
    private static final Path WORKSPACE_ROOT =
        Path.of("build", "tmp", "pr-metadata-fingerprint-e2e").toAbsolutePath().normalize();
    private static final Path REMOTES_ROOT =
        Path.of("build", "tmp", "pr-metadata-fingerprint-e2e-remotes").toAbsolutePath().normalize();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockServerRestClientCustomizer restClientCustomizer;

    @Autowired
    private RepositoryWorkspaceRepository repository;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private GitHubAuth gitHubAuth;

    @Autowired
    private CursorAuth cursorAuth;

    private Path remote;
    private MockRestServiceServer server;
    private final String repoName = "fingerprint-e2e-" + UUID.randomUUID().toString().substring(0, 8);
    private final String upstreamUrl = "https://github.com/" + UPSTREAM_OWNER + "/" + repoName;

    @BeforeEach
    void setUp() throws Exception {
        repository.deleteAll();
        cleanupWorkspace();
        Files.createDirectories(WORKSPACE_ROOT);
        Files.createDirectories(REMOTES_ROOT);
        reset(gitHubAuth, cursorAuth);
        when(gitHubAuth.isContributeEnabled()).thenReturn(true);
        when(gitHubAuth.requireAuthenticatedUser())
            .thenReturn(new GitHubAuthenticatedUser(LOGIN, "The Octocat", "octocat@example.com"));
        when(gitHubAuth.githubLogin()).thenReturn(LOGIN);
        when(cursorAuth.isCursorEnabled()).thenReturn(true);
        when(cursorAuth.requireApiKey()).thenReturn(new CursorIdentity("42", "dev@example.com"));
        server = restClientCustomizer.getServer();
        server.reset();
        remote = createBareRemoteWithInitialCommit();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.reset();
        cleanupWorkspace();
    }

    @Test
    void prPrepareSkipsFollowUpWhenFingerprintUnchanged() throws Exception {
        bootstrapContributeWorkspace();
        Path workspacePath = WORKSPACE_ROOT.resolve(UPSTREAM_OWNER).resolve(repoName);
        Files.writeString(workspacePath.resolve("README.md"), "# Demo\n\nAgent baseline change\n");

        expectPrMetadataFollowUp(
            "run-meta-first",
            "feat: first metadata",
            "First PR title",
            "변경 요약"
        );

        mockMvc.perform(post("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr/prepare"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/commit"));
        server.verify();

        RepositoryWorkspace afterFirst = repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName).orElseThrow();
        String firstFingerprint = afterFirst.getLlmDiffFingerprint();
        assertThat(firstFingerprint).isNotBlank();
        assertThat(afterFirst.getLlmCommitMessage()).isEqualTo("feat: first metadata");

        server.reset();

        mockMvc.perform(post("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr/prepare"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/commit"));
        server.verify();

        RepositoryWorkspace afterSecond = repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName).orElseThrow();
        assertThat(afterSecond.getLlmDiffFingerprint()).isEqualTo(firstFingerprint);
        assertThat(afterSecond.getLlmCommitMessage()).isEqualTo("feat: first metadata");
    }

    @Test
    void ideEditShowsStaleBannerAndRegeneratesMetadataOnSecondPrepare() throws Exception {
        bootstrapContributeWorkspace();
        Path workspacePath = WORKSPACE_ROOT.resolve(UPSTREAM_OWNER).resolve(repoName);
        Files.writeString(workspacePath.resolve("README.md"), "# Demo\n\nAgent baseline change\n");

        expectPrMetadataFollowUp(
            "run-meta-first",
            "feat: first metadata",
            "First PR title",
            "변경 요약"
        );

        mockMvc.perform(post("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr/prepare"))
            .andExpect(status().is3xxRedirection());
        server.verify();
        server.reset();

        mockMvc.perform(get("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/diff"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("이전에 생성된 PR 메타데이터"))));

        Files.writeString(workspacePath.resolve("README.md"), "# Demo\n\nAgent baseline change\n\nIDE extra line\n");

        mockMvc.perform(get("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/diff"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("이전에 생성된 PR 메타데이터")))
            .andExpect(content().string(containsString("PR 진행")));

        expectPrMetadataFollowUp(
            "run-meta-second",
            "feat: regenerated after IDE edit",
            "Regenerated PR title",
            "IDE edit"
        );

        MvcResult regenerateResult = mockMvc.perform(
                post("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr/prepare"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/commit"))
            .andReturn();
        server.verify();

        FlashMap flash = regenerateResult.getFlashMap();
        assertThat(flash.get("infoMessage"))
            .asString()
            .contains("로컬 IDE 추가 수정이 반영되어 PR 메타데이터를 새로 생성했습니다");

        RepositoryWorkspace regenerated = repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName).orElseThrow();
        assertThat(regenerated.getLlmCommitMessage()).isEqualTo("feat: regenerated after IDE edit");
        assertThat(regenerated.getLlmDiffFingerprint()).isNotBlank();

        mockMvc.perform(get("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/diff"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("이전에 생성된 PR 메타데이터"))));
    }

    @Test
    void apiPrepareExposesRegenerationFlagAfterIdeEdit() throws Exception {
        bootstrapContributeWorkspace();
        Path workspacePath = WORKSPACE_ROOT.resolve(UPSTREAM_OWNER).resolve(repoName);
        Files.writeString(workspacePath.resolve("README.md"), "# Demo\n\nAgent baseline change\n");

        expectPrMetadataFollowUp(
            "run-meta-api-1",
            "feat: api first",
            "API first title",
            "api baseline"
        );

        mockMvc.perform(post("/api/contribute/" + UPSTREAM_OWNER + "/" + repoName + "/pr/prepare"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadataRegeneratedDueToDiffChange").value(false))
            .andExpect(jsonPath("$.commitMessage").value("feat: api first"));
        server.verify();
        server.reset();

        Files.writeString(workspacePath.resolve("README.md"), "# Demo\n\nAgent baseline change\n\nAPI IDE edit\n");

        expectPrMetadataFollowUp(
            "run-meta-api-2",
            "feat: api regenerated",
            "API regenerated title",
            "api ide edit"
        );

        mockMvc.perform(post("/api/contribute/" + UPSTREAM_OWNER + "/" + repoName + "/pr/prepare"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadataRegeneratedDueToDiffChange").value(true))
            .andExpect(jsonPath("$.commitMessage").value("feat: api regenerated"));

        mockMvc.perform(get("/api/contribute/" + UPSTREAM_OWNER + "/" + repoName + "/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.llmCache.hasFingerprint").value(true))
            .andExpect(jsonPath("$.llmCache.hasCommitMessage").value(true));

        server.verify();
    }

    private void bootstrapContributeWorkspace() throws Exception {
        expectContributeStartForkFlow();

        mockMvc.perform(post("/web/clone")
                .param("repoUrl", upstreamUrl)
                .param("agentPrompt", "Refactor README for fingerprint test")
                .param("mode", "CONTRIBUTE"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/wait"));

        RepositoryWorkspace workspace = repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName).orElseThrow();
        workspace.markAgentCompleted(java.time.OffsetDateTime.now());
        workspaceService.saveWorkspace(workspace);
        server.verify();
        server.reset();
    }

    private void expectContributeStartForkFlow() {
        server.expect(requestTo("https://api.github.com/repos/" + LOGIN + "/" + repoName))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Not Found\"}"));
        server.expect(requestTo("https://api.github.com/repos/" + UPSTREAM_OWNER + "/" + repoName + "/forks"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(repositoryJson(remote)));
        server.expect(requestTo("https://api.github.com/repos/" + LOGIN + "/" + repoName))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(repositoryJson(remote), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.cursor.com/v1/agents"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {
                  "agent": {"id": "%s", "latestRunId": "run-test-001", "status": "ACTIVE"},
                  "run": {"id": "run-test-001", "status": "CREATING", "createdAt": "2026-05-28T10:00:00Z"}
                }
                """.formatted(AGENT_ID), MediaType.APPLICATION_JSON));
    }

    private void expectPrMetadataFollowUp(
        String runId,
        String commitMessage,
        String prTitle,
        String prBodySummary
    ) {
        server.expect(requestTo("https://api.cursor.com/v1/agents/" + AGENT_ID + "/runs"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {
                  "run": {
                    "id": "%s",
                    "agentId": "%s",
                    "status": "CREATING",
                    "createdAt": "2026-05-29T10:00:00Z"
                  }
                }
                """.formatted(runId, AGENT_ID), MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.cursor.com/v1/agents/" + AGENT_ID + "/runs/" + runId))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                  "id": "%s",
                  "status": "FINISHED",
                  "result": "{\\"commitMessage\\":\\"%s\\",\\"prTitle\\":\\"%s\\",\\"prBody\\":\\"## %s\\\\n\\\\n## 변경 파일\\\\n- README.md\\"}",
                  "createdAt": "2026-05-29T10:00:00Z",
                  "updatedAt": "2026-05-29T10:00:05Z"
                }
                """.formatted(runId, commitMessage, prTitle, prBodySummary), MediaType.APPLICATION_JSON));
    }

    private String repositoryJson(Path remoteRepository) {
        return """
            {
              "full_name": "%s/%s",
              "html_url": "https://github.com/%s/%s",
              "clone_url": "%s",
              "default_branch": "master"
            }
            """.formatted(LOGIN, repoName, LOGIN, repoName, remoteRepository.toUri());
    }

    private Path createBareRemoteWithInitialCommit() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Path seed = REMOTES_ROOT.resolve("seed-" + suffix);
        Path bare = REMOTES_ROOT.resolve(repoName + "-" + suffix + ".git");
        Files.createDirectories(seed);
        try (Git seedGit = Git.init().setDirectory(seed.toFile()).call()) {
            Files.writeString(seed.resolve("README.md"), "# Demo\n");
            seedGit.add().addFilepattern("README.md").call();
            seedGit.commit()
                .setMessage("Initial commit")
                .setAuthor("Test", "test@example.com")
                .call();
            try (Git ignored = Git.init().setBare(true).setDirectory(bare.toFile()).call()) {
                // Bare remote stands in for the fork origin in this integration test.
            }
            seedGit.remoteAdd().setName("origin").setUri(new URIish(bare.toUri().toString())).call();
            seedGit.push().setRemote("origin").add("master").call();
        }
        return bare;
    }

    private void cleanupWorkspace() {
        Optional<RepositoryWorkspace> workspace = repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName);
        workspace.ifPresent(ignored -> {
            try {
                workspaceService.deleteWorkspace(UPSTREAM_OWNER, repoName);
            } catch (AppException ignoredException) {
                // Windows JGit pack files can remain locked briefly after the test.
            }
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOverrides {

        @Bean
        @Primary
        RestClient.Builder restClientBuilder(MockServerRestClientCustomizer customizer) {
            RestClient.Builder builder = RestClient.builder();
            customizer.customize(builder);
            return builder;
        }

        @Bean
        @Primary
        GitHubAuth gitHubAuth() {
            return mock(GitHubAuth.class);
        }

        @Bean
        @Primary
        CursorAuth cursorAuth() {
            return mock(CursorAuth.class);
        }
    }
}
