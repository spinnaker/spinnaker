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

import org.ajoberstar.grgit.Credentials
import org.ajoberstar.grgit.Grgit
import org.eclipse.egit.github.core.PullRequest
import org.eclipse.egit.github.core.PullRequestMarker
import org.eclipse.egit.github.core.Repository
import org.eclipse.egit.github.core.User
import org.eclipse.egit.github.core.client.GitHubClient
import org.eclipse.egit.github.core.client.GitHubRequest
import org.eclipse.egit.github.core.client.GitHubResponse
import org.eclipse.egit.github.core.service.PullRequestService
import org.eclipse.egit.github.core.service.RepositoryService
import org.eclipse.egit.github.core.service.UserService
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class BumpDependencies extends DefaultTask {

  private static final String COMMIT_BODY = '''\
    This is an automated PR!
    
    You can merge all of these PRs from the `%s` project using the command below and a Github access token.
    Generate a GH token from https://github.com/settings/tokens, and run the following:
    
    ```
    GITHUB_ACCESS_TOKEN=
    ./gradlew mergeAllAutoBumpPRs -Pgithub.token=$GITHUB_ACCESS_TOKEN
    ```
    
    If something has gone wrong, and you don't want to manually close all of the autobump PRs, you can
    close all open PRs with the 'autobump' label using this command:
    
    ```
    GITHUB_ACCESS_TOKEN=
    ./gradlew closeAllAutoBumpPRs -Pgithub.token=$GITHUB_ACCESS_TOKEN
    ```
    '''.stripIndent()

  @TaskAction
  void bumpDependencies() {
    GitHubClient client = new GitHubClient()
    String accessToken = project.property('github.token')
    client.setOAuth2Token(accessToken)

    UserService userSvc = new UserService(client)
    User user = userSvc.getUser()

    DependencyBumpExtension extension = project.extensions.findByType(DependencyBumpExtension)

    String reviewer
    try {
      String latestReleaseUri = "/repos/spinnaker/${extension.artifact}/releases/latest"
      GitHubResponse resp = client.get(new GitHubRequest(uri: latestReleaseUri, type: LatestRelease.class))
      reviewer = (resp.body as LatestRelease)?.author?.login
      logger.lifecycle("Found author of last release: $reviewer.")
    } catch (Exception e) {
      logger.lifecycle("Could not get most recent $extension.artifact release author: ${e.getMessage()}")
    }


    def repos = extension.reposForArtifact
    RepositoryService repoSvc = new RepositoryService(client)
    repoSvc.getRepositories().findAll {
      repos.contains(it.name) && it.owner.login == user.login
    }.collect { Repository userFork ->
      // The parent and source details are only populated from this get request
      // (as in they're not in the list request above).
      logger.lifecycle("Getting details for ${userFork.cloneUrl}")
      repoSvc.getRepository(userFork)
    }.each { Repository userFork ->
      Repository upstream = userFork.source



      def dest = new File(extension.rootProject.buildDir, "clones")
      if (dest.exists()) {
        dest.deleteDir()
      }

      def cloneDir = new File(dest, upstream.name)
      logger.lifecycle("Cloning repo ${upstream.cloneUrl} into $cloneDir")
      def grgit = Grgit.clone(
          dir: cloneDir,
          uri: upstream.cloneUrl,
          remote: "upstream",
          credentials: new Credentials("ignored", accessToken))

      def repoConfig = grgit.repository.jgit.repository.config
      repoConfig.load()
      repoConfig.setBoolean("commit", null, "gpgsign", false)
      repoConfig.save()

      String autoBumpBranch = extension.autobumpBranchName
      def branches = grgit.branch.list()
      if (branches.find { it.name == autoBumpBranch }) {
        logger.lifecycle("Deleting branch '$autoBumpBranch' in repo $upstream.name")
        grgit.branch.remove(names: [autoBumpBranch], force: true)
      }

      grgit.checkout(
          branch: autoBumpBranch,
          createBranch: true
      )

      logger.lifecycle("Updating ${extension.versionStringForArtifact} to ${project.version}")
      Properties props = new Properties()
      File gradleProps = new File(cloneDir, "gradle.properties")
      if (gradleProps.exists()) {
        gradleProps.withInputStream { props.load(it) }
      }
      def version = project.version.toString()
      def orig = props.setProperty(extension.versionStringForArtifact, project.version.toString())
      boolean versionChanged = orig != version
      if (!versionChanged) {
        logger.lifecycle("No changes detected, skipping git commit/push/PR")
        return
      }
      gradleProps.withOutputStream { props.store(it, null) }

      logger.lifecycle("Committing changes")
      grgit.commit(
          message: "chore(dependencies): Autobump $extension.versionStringForArtifact",
          all: true)
      grgit.remote.add(name: "userFork", url: userFork.cloneUrl)

      logger.lifecycle("Pushing changes to '$autoBumpBranch' branch of ${userFork.cloneUrl}")
      grgit.push(
          remote: "userFork",
          refsOrSpecs: [autoBumpBranch],
          force: true)

      PullRequest pr
      boolean dryRun = Boolean.valueOf(project.findProperty("dryRun")?.toString() ?: "false")
      if (!dryRun) {
        PullRequestService prSvc = new PullRequestService(client)
        try {
          pr = prSvc.createPullRequest(upstream, new PullRequest(
              title: "chore(dependencies): Autobump $extension.versionStringForArtifact",
              body: String.format(COMMIT_BODY, extension.artifact),
              base: new PullRequestMarker(label: "master"),
              head: new PullRequestMarker(label: "${userFork.owner.login}:$autoBumpBranch"),
          ))
          logger.lifecycle("Created PR ${pr.htmlUrl}")
        } catch (Exception e) {
          logger.lifecycle("Could not create PR in ${userFork.cloneUrl}: ${e.getMessage()}")
          pr = prSvc.getPullRequests(upstream, "open").find { PullRequest existingPR ->
            existingPR.head.label == "${userFork.owner.login}:$autoBumpBranch"
          }
          if (!pr) {
            logger.lifecycle("Couldn't find existing PR")
            return
          }
        }

        def labelsUri = "/repos/spinnaker/${upstream.name}/issues/${pr.number}/labels"
        List<String> labels = [extension.getAutobumpLabel()]
        logger.lifecycle("Applying labels ${labels} to issue using ${labelsUri}")
        try {
          client.post(labelsUri, labels, List.class)
        } catch (Exception e) {
          logger.lifecycle("Could not apply labels ${labels} to PR ${pr.htmlUrl}: ${e.getMessage()}")
        }

        if (reviewer) {
          logger.lifecycle("Requesting review from ${reviewer}")
          def uri = "/repos/spinnaker/${upstream.name}/pulls/${pr.number}/requested_reviewers"
          def data = ["reviewers": [reviewer]]
          client.post(uri, data, null /* return Type */)
          logger.lifecycle("Reviewers requested!")
        }
      } else {
        logger.lifecycle("Skipping PR creation because of -PdryRun")
      }
    }
  }

  static class LatestRelease {
    User author
  }
}
