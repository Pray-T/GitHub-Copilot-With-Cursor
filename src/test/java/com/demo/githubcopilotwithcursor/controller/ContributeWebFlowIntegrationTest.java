package com.demo.githubcopilotwithcursor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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

import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.exception.AppException;
import com.demo.githubcopilotwithcursor.cursor.CursorAuth;
import com.demo.githubcopilotwithcursor.cursor.dto.CursorIdentity;
import com.demo.githubcopilotwithcursor.domain.AgentStatus;
import com.demo.githubcopilotwithcursor.domain.WorkspaceMode;
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
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FileUtils;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.FlashMap;

@SpringBootTest(properties = {
    "app.github.token=test-token",
    "app.cursor.api-key=test-cursor-key",
    "app.cursor.composer.enabled=false",
    "app.workspace.root=build/tmp/contribute-web-flow",
    "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
@AutoConfigureMockRestServiceServer
class ContributeWebFlowIntegrationTest {

    private static final String UPSTREAM_OWNER = "spring-projects";
    private static final String LOGIN = "octocat";
    private static final Path WORKSPACE_ROOT = Path.of("build", "tmp", "contribute-web-flow").toAbsolutePath().normalize();
    private static final Path REMOTES_ROOT = Path.of("build", "tmp", "contribute-web-flow-remotes").toAbsolutePath().normalize();

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
    private final String repoName = "demo-flow-e2e-" + UUID.randomUUID().toString().substring(0, 8);
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
        when(gitHubAuth.defaultAuthorName()).thenReturn("The Octocat");
        when(gitHubAuth.defaultAuthorEmail()).thenReturn("octocat@example.com");
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
    void contributeFlowCoversCloneDiffCommitPushDuplicatePrSuccessAndStatusApi() throws Exception {
        expectContributeStartForkFlow();

        mockMvc.perform(post("/web/clone")
                .param("repoUrl", upstreamUrl)
                .param("agentPrompt", "Refactor README for integration test")
                .param("mode", "CONTRIBUTE"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/wait"));

        RepositoryWorkspace workspace = repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName).orElseThrow();
        assertThat(workspace.getForkUrl()).isEqualTo("https://github.com/" + LOGIN + "/" + repoName);
        assertThat(workspace.isForkReused()).isFalse();
        assertThat(workspace.getCursorAgentId()).isEqualTo("bc-test-001");
        assertThat(workspace.getAgentStatus()).isEqualTo(AgentStatus.RUNNING);
        assertRemoteBranchHeadMatchesWorkspace(workspace.getBranchName(), WORKSPACE_ROOT.resolve(UPSTREAM_OWNER).resolve(repoName));
        server.verify();
        server.reset();

        workspace.markAgentCompleted(java.time.OffsetDateTime.now());
        workspaceService.saveWorkspace(workspace);

        mockMvc.perform(get("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/wait"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Cursor Cloud Agent")))
            .andExpect(content().string(containsString("변경 확인")));

        Path workspacePath = WORKSPACE_ROOT.resolve(UPSTREAM_OWNER).resolve(repoName);
        Files.writeString(workspacePath.resolve("README.md"), "# Demo\n\nChanged by integration test\n");

        mockMvc.perform(get("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/diff"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("PR 진행")))
            .andExpect(content().string(containsString("추가 수정")))
            .andExpect(content().string(containsString("README.md")));

        mockMvc.perform(post("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr/prepare"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/commit"));

        mockMvc.perform(post("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/commit-push")
                .param("message", "Refactor README")
                .param("authorName", "The Octocat")
                .param("authorEmail", "octocat@example.com"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr"));

        RepositoryWorkspace pushedWorkspace = repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName).orElseThrow();
        assertRemoteBranchHeadMatchesWorkspace(pushedWorkspace.getBranchName(), workspacePath);

        mockMvc.perform(get("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/diff"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("README.md")))
            .andExpect(content().string(containsString("Changed by integration test")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("변경된 파일이 없습니다"))));

        expectDuplicatePullRequest(pushedWorkspace.getBranchName());

        MvcResult duplicateResult = mockMvc.perform(post("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/create-pr")
                .param("title", "Refactor README")
                .param("body", "## 변경 파일\n\n- M README.md")
                .param("base", "main")
                .param("draft", "true"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr"))
            .andReturn();

        FlashMap duplicateFlash = duplicateResult.getFlashMap();
        assertThat(duplicateFlash.get("existingPrUrl"))
            .isEqualTo("https://github.com/" + UPSTREAM_OWNER + "/" + repoName + "/pull/654");
        server.verify();
        server.reset();

        expectDraftPageBaseLookup();

        mockMvc.perform(get("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr").flashAttrs(duplicateFlash))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("동일한 head 조합으로 이미 열린 PR이 있습니다")))
            .andExpect(content().string(containsString("https://github.com/" + UPSTREAM_OWNER + "/" + repoName + "/pull/654")));
        server.verify();
        server.reset();

        expectSuccessfulPullRequest();

        mockMvc.perform(post("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/create-pr")
                .param("title", "Refactor README")
                .param("body", "## 변경 파일\n\n- M README.md")
                .param("base", "main")
                .param("draft", "true"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr"));

        mockMvc.perform(get("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("이미 PR이 생성되었습니다.")))
            .andExpect(content().string(containsString("https://github.com/" + UPSTREAM_OWNER + "/" + repoName + "/pull/777")));
        server.verify();
        server.reset();

        expectPullRequestStatusLookup();

        mockMvc.perform(get("/api/contribute/" + UPSTREAM_OWNER + "/" + repoName + "/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repoName").value(repoName))
            .andExpect(jsonPath("$.mode").value("CONTRIBUTE"))
            .andExpect(jsonPath("$.fork.url").value("https://github.com/" + LOGIN + "/" + repoName))
            .andExpect(jsonPath("$.fork.reused").value(false))
            .andExpect(jsonPath("$.branch").value(pushedWorkspace.getBranchName()))
            .andExpect(jsonPath("$.agent.agentId").value("bc-test-001"))
            .andExpect(jsonPath("$.lastCommit.message").value("Refactor README"))
            .andExpect(jsonPath("$.prUrl").value("https://github.com/" + UPSTREAM_OWNER + "/" + repoName + "/pull/777"))
            .andExpect(jsonPath("$.prState").value("open"))
            .andExpect(jsonPath("$.llmCache.hasCommitMessage").value(true));

        server.verify();
    }

    @Test
    void reviewCloneAlsoForksAndStartsCursorAgentBeforeWaitPage() throws Exception {
        expectContributeStartForkFlow();

        mockMvc.perform(post("/web/clone")
                .param("repoUrl", upstreamUrl)
                .param("agentPrompt", "Review only refactor")
                .param("mode", "REVIEW"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/wait"));

        RepositoryWorkspace workspace = repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName).orElseThrow();
        assertThat(workspace.getMode()).isEqualTo(WorkspaceMode.REVIEW);
        assertThat(workspace.getForkUrl()).isEqualTo("https://github.com/" + LOGIN + "/" + repoName);
        assertThat(workspace.getCursorAgentId()).isEqualTo("bc-test-001");
        assertThat(workspace.getAgentStatus()).isEqualTo(AgentStatus.RUNNING);
        assertRemoteBranchHeadMatchesWorkspace(workspace.getBranchName(), WORKSPACE_ROOT.resolve(UPSTREAM_OWNER).resolve(repoName));
        server.verify();
    }

    @Test
    void missingLocalWorkspaceIsReconciledFromDbAndCanBeRestarted() throws Exception {
        expectContributeStartForkFlow();

        mockMvc.perform(post("/web/clone")
                .param("repoUrl", upstreamUrl)
                .param("agentPrompt", "Refactor for reconcile test")
                .param("mode", "CONTRIBUTE"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/wait"));

        assertThat(repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName)).isPresent();
        Path workspacePath = WORKSPACE_ROOT.resolve(UPSTREAM_OWNER).resolve(repoName);
        assertThat(Files.isDirectory(workspacePath.resolve(".git"))).isTrue();
        server.verify();
        server.reset();

        deleteDirectoryRecursively(workspacePath);
        assertThat(repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName)).isPresent();

        mockMvc.perform(get("/api/workspaces"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("\"repoName\":\"" + repoName + "\""))));

        assertThat(repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName)).isEmpty();

        mockMvc.perform(get("/api/diff/" + UPSTREAM_OWNER + "/" + repoName))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));

        expectContributeStartForkFlow();

        mockMvc.perform(post("/api/contribute/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repoUrl\":\"" + upstreamUrl + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repoName").value(repoName));

        assertThat(repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName)).isPresent();
        assertThat(Files.isDirectory(WORKSPACE_ROOT.resolve(UPSTREAM_OWNER).resolve(repoName).resolve(".git"))).isTrue();
        server.verify();
    }

    private void deleteDirectoryRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        FileUtils.delete(root.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
    }

    private void expectContributeStartForkFlow() {
        // The expected order is intentional: GitHub fork discovery/creation must finish
        // before Cursor Cloud Agent receives POST /v1/agents for the prepared fork branch.
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
                  "agent": {"id": "bc-test-001", "latestRunId": "run-test-001", "status": "ACTIVE"},
                  "run": {"id": "run-test-001", "status": "CREATING", "createdAt": "2026-05-28T10:00:00Z"}
                }
                """, MediaType.APPLICATION_JSON));
    }

    private void expectDuplicatePullRequest(String branchName) {
        server.expect(requestTo("https://api.github.com/repos/" + UPSTREAM_OWNER + "/" + repoName + "/pulls"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatusCode.valueOf(422))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {
                      "message": "A pull request already exists for octocat:refactor/demo-flow-e2e."
                    }
                    """));
        server.expect(requestTo("https://api.github.com/repos/" + UPSTREAM_OWNER + "/" + repoName + "/pulls?head=" + LOGIN + ":" + branchName + "&state=open"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(("""
                [
                  {
                    "html_url": "https://github.com/%s/%s/pull/654",
                    "number": 654,
                    "state": "open",
                    "draft": true
                  }
                ]
                """).formatted(UPSTREAM_OWNER, repoName), MediaType.APPLICATION_JSON));
    }

    private void expectDraftPageBaseLookup() {
        server.expect(requestTo("https://api.github.com/repos/" + UPSTREAM_OWNER + "/" + repoName))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(("""
                {
                  "full_name": "%s/%s",
                  "html_url": "https://github.com/%s/%s",
                  "clone_url": "https://github.com/%s/%s.git",
                  "default_branch": "main"
                }
                """).formatted(UPSTREAM_OWNER, repoName, UPSTREAM_OWNER, repoName, UPSTREAM_OWNER, repoName), MediaType.APPLICATION_JSON));
    }

    private void expectSuccessfulPullRequest() {
        server.expect(requestTo("https://api.github.com/repos/" + UPSTREAM_OWNER + "/" + repoName + "/pulls"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(("""
                {
                  "html_url": "https://github.com/%s/%s/pull/777",
                  "number": 777,
                  "state": "open",
                  "draft": true
                }
                """).formatted(UPSTREAM_OWNER, repoName), MediaType.APPLICATION_JSON));
    }

    private void expectPullRequestStatusLookup() {
        server.expect(requestTo("https://api.github.com/repos/" + UPSTREAM_OWNER + "/" + repoName + "/pulls/777"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(("""
                {
                  "html_url": "https://github.com/%s/%s/pull/777",
                  "number": 777,
                  "state": "open",
                  "draft": true,
                  "merged": false
                }
                """).formatted(UPSTREAM_OWNER, repoName), MediaType.APPLICATION_JSON));
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

    private void assertRemoteBranchHeadMatchesWorkspace(String branchName, Path workspacePath) throws Exception {
        String localHead;
        try (Git workspaceGit = Git.open(workspacePath.toFile())) {
            localHead = workspaceGit.getRepository().resolve("HEAD").name();
        }
        try (Repository remoteRepository = new FileRepositoryBuilder()
            .setGitDir(remote.toFile())
            .setBare()
            .build()) {
            ObjectId pushedHead = remoteRepository.resolve("refs/heads/" + branchName);
            assertThat(pushedHead).isNotNull();
            assertThat(pushedHead.name()).isEqualTo(localHead);
        }
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
                // Bare remote is used as the fork origin in this integration test.
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
                // Windows JGit pack files can remain locked briefly after the test; ignore cleanup flakiness.
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
