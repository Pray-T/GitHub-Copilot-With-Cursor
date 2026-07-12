package com.demo.githubcopilotwithcursor.repository;

import com.demo.githubcopilotwithcursor.domain.RepositoryWorkspace;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryWorkspaceRepository extends JpaRepository<RepositoryWorkspace, Long> {

    Optional<RepositoryWorkspace> findByRepoOwnerAndRepoName(String repoOwner, String repoName);

    boolean existsByRepoOwnerAndRepoName(String repoOwner, String repoName);

    List<RepositoryWorkspace> findAllByOrderByClonedAtDesc();
}
