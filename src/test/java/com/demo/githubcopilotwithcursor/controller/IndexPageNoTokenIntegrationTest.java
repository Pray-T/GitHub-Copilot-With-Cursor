package com.demo.githubcopilotwithcursor.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.containsString;
import com.demo.githubcopilotwithcursor.repository.RepositoryWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "app.github.token=",
    "app.cursor.api-key=",
    "app.workspace.root=build/tmp/index-page-no-token"
})
@AutoConfigureMockMvc
class IndexPageNoTokenIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RepositoryWorkspaceRepository repository;

    @BeforeEach
    void clearWorkspaces() {
        repository.deleteAll();
    }

    @Test
    void indexDisablesContributeModeWhenGithubTokenIsMissing() throws Exception {
        mockMvc.perform(get("/web"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"mode\"")))
            .andExpect(content().string(containsString("disabled")))
            .andExpect(content().string(containsString("GITHUB_TOKEN")))
            .andExpect(content().string(containsString("CURSOR_API_KEY")));
    }
}
