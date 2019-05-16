/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    DependencyBumpExtension extension = project.extensions.getByType(DependencyBumpExtension)

    RepositoryService repoSvc = new RepositoryService(client)
    repoSvc.getRepositories().findAll {
      extension.reposForArtifact.contains(it.name) && it.owner.login == "spinnaker"
    }.collect { Repository upstream ->
      IssueService issueService = new IssueService(client)
      PullRequestService prService = new PullRequestService(client)
      issueService.getIssues(upstream, ["labels": extension.autobumpLabel, "state": "open"]).findAll { Issue i ->
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
