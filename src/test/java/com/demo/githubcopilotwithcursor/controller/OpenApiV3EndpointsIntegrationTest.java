package com.demo.githubcopilotwithcursor.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "app.github.token=test-token",
    "app.cursor.api-key=test-cursor-key"
})
@AutoConfigureMockMvc
class OpenApiV3EndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocumentsExposeV3AgentWorkspaceAndPrepareRoutes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/agents/start']").exists())
            .andExpect(jsonPath("$.paths['/api/agents/{repoOwner}/{repoName}/status']").exists())
            .andExpect(jsonPath("$.paths['/api/workspaces/{repoOwner}/{repoName}/launch-ide']").exists())
            .andExpect(jsonPath("$.paths['/api/workspaces/{repoOwner}/{repoName}'].delete").exists())
            .andExpect(jsonPath("$.paths['/api/contribute/{repoOwner}/{repoName}/pr/prepare']").exists())
            .andExpect(jsonPath("$.tags[*].name", hasItem("Cursor Agent")));
    }
}
