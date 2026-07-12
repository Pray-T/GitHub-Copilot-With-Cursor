package com.demo.githubcopilotwithcursor;

import com.demo.githubcopilotwithcursor.config.ContributeProperties;
import com.demo.githubcopilotwithcursor.config.CursorProperties;
import com.demo.githubcopilotwithcursor.config.GitHubProperties;
import com.demo.githubcopilotwithcursor.config.WorkspaceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({WorkspaceProperties.class, GitHubProperties.class, ContributeProperties.class, CursorProperties.class})
public class GitHubCopilotWithCursorApplication {

	public static void main(String[] args) {
		SpringApplication.run(GitHubCopilotWithCursorApplication.class, args);
	}

}
