package com.netflix.spinnaker.gradle.dependency

import org.eclipse.egit.github.core.Issue
import org.eclipse.egit.github.core.MergeStatus
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
            merge(client, upstream, i.number)
          }
          logger.lifecycle("Merged ${i.htmlUrl} successfully")
        } catch (Exception e) {
          logger.lifecycle("Error merging PR ${i.htmlUrl}: ${e.message}")
        }
      }
    }
  }

  // Need to do our own `merge` command because the built in one doesn't support squash merges.
  // See PullRequestService.merge() for original impl.
  MergeStatus merge(GitHubClient client, Repository repo, int pullRequestID) {
    def uri = "/repos/spinnaker/${repo.name}/pulls/${pullRequestID}/merge"
    def body = ["merge_method": "squash"]
    return client.put(uri, body, MergeStatus.class)
  }
}
