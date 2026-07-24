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
import com.demo.githubcopilotwithcursor.domain.AgentStatus;
import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import com.demo.githubcopilotwithcursor.domain.WorkspaceMode;
import com.demo.githubcopilotwithcursor.domain.WorkspaceStatus;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

@SpringBootTest(properties = {
    "app.github.token=test-token",
    "app.cursor.api-key=test-cursor-key",
    "app.cursor.composer.enabled=false",
    "app.workspace.root=build/tmp/agent-e2e-flow",
    "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
@AutoConfigureMockRestServiceServer
class AgentE2EFlowIntegrationTest {

    private static final String UPSTREAM_OWNER = "spring-projects";
    private static final String LOGIN = "octocat";
    private static final String AGENT_ID = "bc-agent-e2e-001";
    private static final String RUN_ID = "run-agent-e2e-001";
    private static final Path WORKSPACE_ROOT = Path.of("build", "tmp", "agent-e2e-flow").toAbsolutePath().normalize();
    private static final Path REMOTES_ROOT = Path.of("build", "tmp", "agent-e2e-flow-remotes").toAbsolutePath().normalize();

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
    private final String repoName = "agent-e2e-" + UUID.randomUUID().toString().substring(0, 8);
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
    void reviewFlowUsesAgentStatusPollingSyncAndDiff() throws Exception {
        expectForkAndAgentStart();

        mockMvc.perform(post("/web/clone")
                .param("repoUrl", upstreamUrl)
                .param("agentPrompt", "Review refactor Owner.java only")
                .param("mode", "REVIEW"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/wait"));

        RepositoryWorkspace workspace = repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName).orElseThrow();
        String branchName = workspace.getBranchName();
        assertThat(workspace.getMode()).isEqualTo(WorkspaceMode.REVIEW);
        assertThat(workspace.getCursorAgentId()).isEqualTo(AGENT_ID);
        server.verify();
        server.reset();

        simulateAgentPushToRemote(branchName, "# Agent change\n");
        expectAgentRunFinished();

        mockMvc.perform(get("/api/agents/" + UPSTREAM_OWNER + "/" + repoName + "/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.agentStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.cursorStatus").value("FINISHED"))
            .andExpect(jsonPath("$.syncedHeadSha").isNotEmpty());

        workspace = repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName).orElseThrow();
        assertThat(workspace.getAgentStatus()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(workspace.getStatus()).isEqualTo(WorkspaceStatus.READY_FOR_REVIEW);

        mockMvc.perform(get("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/diff"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("홈으로")))
            .andExpect(content().string(not(containsString("PR 진행"))))
            .andExpect(content().string(containsString("README.md")));

        mockMvc.perform(get("/api/workspaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].mode").value("REVIEW"))
            .andExpect(jsonPath("$.items[0].agentStatus").value("COMPLETED"));
    }

    @Test
    void contributeFlowUsesAgentStatusPollingPrepareCommitPushAndStatusApi() throws Exception {
        expectForkAndAgentStart();

        mockMvc.perform(post("/web/clone")
                .param("repoUrl", upstreamUrl)
                .param("agentPrompt", "Contribute refactor for PR")
                .param("mode", "CONTRIBUTE"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/wait"));

        RepositoryWorkspace workspace = repository.findByRepoOwnerAndRepoName(UPSTREAM_OWNER, repoName).orElseThrow();
        String branchName = workspace.getBranchName();
        server.verify();
        server.reset();

        simulateAgentPushToRemote(branchName, "# Contribute agent change\n");
        expectAgentRunFinished();

        mockMvc.perform(get("/api/agents/" + UPSTREAM_OWNER + "/" + repoName + "/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.agentStatus").value("COMPLETED"));

        mockMvc.perform(get("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/diff"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("PR 진행")));

        mockMvc.perform(post("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr/prepare"))
            .andExpect(status().is3xxRedirection());

        Path workspacePath = WORKSPACE_ROOT.resolve(UPSTREAM_OWNER).resolve(repoName);
        Files.writeString(workspacePath.resolve("README.md"), "# Demo\n\nLocal uncommitted\n");

        mockMvc.perform(post("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr/prepare"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/commit"));

        mockMvc.perform(post("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/commit-push")
                .param("message", "Agent E2E commit")
                .param("authorName", "The Octocat")
                .param("authorEmail", "octocat@example.com"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/pr"));
        server.reset();

        expectSuccessfulPullRequest();
        expectPullRequestStatusLookup();

        mockMvc.perform(post("/web/workspaces/" + UPSTREAM_OWNER + "/" + repoName + "/create-pr")
                .param("title", "Agent E2E PR")
                .param("body", "## 변경 파일\n\n- M README.md")
                .param("base", "main")
                .param("draft", "true"))
            .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/api/contribute/" + UPSTREAM_OWNER + "/" + repoName + "/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("CONTRIBUTE"))
            .andExpect(jsonPath("$.agent.agentId").value(AGENT_ID))
            .andExpect(jsonPath("$.agent.status").value("COMPLETED"))
            .andExpect(jsonPath("$.llmCache.hasCommitMessage").value(true))
            .andExpect(jsonPath("$.llmCache.hasPrTitle").value(true))
            .andExpect(jsonPath("$.llmCache.hasPrBody").value(true))
            .andExpect(jsonPath("$.prState").value("open"));

        server.verify();
    }

    private void expectForkAndAgentStart() {
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
                  "agent": {"id": "%s", "latestRunId": "%s", "status": "ACTIVE"},
                  "run": {"id": "%s", "status": "CREATING", "createdAt": "2026-05-28T10:00:00Z"}
                }
                """.formatted(AGENT_ID, RUN_ID, RUN_ID), MediaType.APPLICATION_JSON));
    }

    private void expectAgentRunFinished() {
        server.expect(requestTo("https://api.cursor.com/v1/agents/" + AGENT_ID + "/runs/" + RUN_ID))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                  "id": "%s",
                  "status": "FINISHED",
                  "createdAt": "2026-05-28T10:00:00Z",
                  "updatedAt": "2026-05-28T10:05:00Z"
                }
                """.formatted(RUN_ID), MediaType.APPLICATION_JSON));
    }

    private void expectSuccessfulPullRequest() {
        server.expect(requestTo("https://api.github.com/repos/" + UPSTREAM_OWNER + "/" + repoName + "/pulls"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {
                  "html_url": "https://github.com/%s/%s/pull/901",
                  "number": 901,
                  "state": "open",
                  "draft": true
                }
                """.formatted(UPSTREAM_OWNER, repoName), MediaType.APPLICATION_JSON));
    }

    private void expectPullRequestStatusLookup() {
        server.expect(requestTo("https://api.github.com/repos/" + UPSTREAM_OWNER + "/" + repoName + "/pulls/901"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                  "html_url": "https://github.com/%s/%s/pull/901",
                  "number": 901,
                  "state": "open",
                  "draft": true,
                  "merged": false
                }
                """.formatted(UPSTREAM_OWNER, repoName), MediaType.APPLICATION_JSON));
    }

    private void simulateAgentPushToRemote(String branchName, String readmeContent) throws Exception {
        Path agentCloneDir = REMOTES_ROOT.resolve("agent-clone-" + UUID.randomUUID());
        Files.createDirectories(agentCloneDir.getParent());
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(agentCloneDir.toFile()).call()) {
            git.fetch().call();
            git.checkout()
                .setCreateBranch(true)
                .setName(branchName)
                .setStartPoint("origin/" + branchName)
                .call();
            Files.writeString(agentCloneDir.resolve("README.md"), readmeContent);
            git.add().addFilepattern("README.md").call();
            git.commit().setMessage("Agent refactor").setAuthor("Agent", "agent@test.com").call();
            git.push().setRemote("origin").add(branchName).call();
        } finally {
            if (Files.exists(agentCloneDir)) {
                FileUtils.delete(agentCloneDir.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
            }
        }
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
                // bare remote
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
                // Windows JGit pack lock tolerance
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
