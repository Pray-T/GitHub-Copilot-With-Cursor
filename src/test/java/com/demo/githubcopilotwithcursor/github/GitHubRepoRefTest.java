package com.demo.githubcopilotwithcursor.github;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class GitHubRepoRefTest {

    @Test
    void fromUriParsesOwnerAndRepo() throws Exception {
        GitHubRepoRef ref = GitHubRepoRef.from(new URI("https://github.com/spring-projects/spring-petclinic.git"));

        assertThat(ref.owner()).isEqualTo("spring-projects");
        assertThat(ref.repo()).isEqualTo("spring-petclinic");
    }

    @Test
    void fromUrlParsesOwnerAndRepo() {
        GitHubRepoRef ref = GitHubRepoRef.fromUrl("https://github.com/octocat/Hello-World");

        assertThat(ref.owner()).isEqualTo("octocat");
        assertThat(ref.repo()).isEqualTo("Hello-World");
    }
}
