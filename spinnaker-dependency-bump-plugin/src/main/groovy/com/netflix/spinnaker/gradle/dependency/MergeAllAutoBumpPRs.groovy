package com.netflix.spinnaker.gradle.dependency

import org.eclipse.egit.github.core.Issue
import org.eclipse.egit.github.core.Repository
import org.eclipse.egit.github.core.client.GitHubClient
import org.eclipse.egit.github.core.service.IssueService
import org.eclipse.egit.github.core.service.PullRequestService
import org.eclipse.egit.github.core.service.RepositoryService
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class MergeAllAutoBumpPRs extends DefaultTask {

  @TaskAction
  void closeAllAutoBumpPRs() {
    GitHubClient client = new GitHubClient()
    String accessToken = project.property('github.token')
    client.setOAuth2Token(accessToken)

    RepositoryService repoSvc = new RepositoryService(client)
    repoSvc.getRepositories().findAll {
      SpinnakerDependencyBumpPlugin.REPOS.contains(it.name) && it.owner.login == "spinnaker"
    }.collect { Repository upstream ->
      IssueService issueService = new IssueService(client)
      PullRequestService prService = new PullRequestService(client)
      issueService.getIssues(upstream, ["labels": "autobump", "state": "open"]).findAll { Issue i ->
        i.pullRequest != null
      }.each { Issue i ->
        try {
          logger.lifecycle("Merging found PR: ${i.htmlUrl} ")
          def pr = prService.getPullRequest(upstream, i.number)
          if (pr.isMergeable()) {
            prService.merge(upstream, i.number, "Merged with autobump tool.")
          }
          logger.lifecycle("Merged ${i.htmlUrl}")
        } catch (Exception e) {
          logger.lifecycle("Error merging PR ${i.htmlUrl}: ${e.message}")
        }
      }
    }
  }
}
