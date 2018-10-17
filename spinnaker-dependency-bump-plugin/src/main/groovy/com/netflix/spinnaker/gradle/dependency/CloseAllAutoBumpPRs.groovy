package com.netflix.spinnaker.gradle.dependency

import org.eclipse.egit.github.core.Issue
import org.eclipse.egit.github.core.Repository
import org.eclipse.egit.github.core.client.GitHubClient
import org.eclipse.egit.github.core.service.IssueService
import org.eclipse.egit.github.core.service.RepositoryService
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class CloseAllAutoBumpPRs extends DefaultTask {

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
      issueService.getIssues(upstream, ["labels": "autobump", "state": "open"]).findAll { Issue i ->
        i.pullRequest != null
      }.each { Issue i ->
        try {
          def commentURI = "/repos/spinnaker/${upstream.name}/issues/${i.number}/comments"
          def data = ["body": "Closed by autobump tool"]
          client.post(commentURI, data, null /* return type */)
        } catch (Exception e) {
          logger.lifecycle("Could not comment on issue ${i.htmlUrl}: ${e.message}")
        }

        try {
          logger.lifecycle("Closing found issue: ${i.htmlUrl} ")
          i.state = "closed"
          issueService.editIssue(upstream, i)
          logger.lifecycle("Closed ${i.htmlUrl}")
        } catch (Exception e) {
          logger.lifecycle("Error closing issue ${i.htmlUrl}: ${e.message}")
          return
        }
      }
    }
  }
}
