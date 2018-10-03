/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class SpinnakerDependencyBumpPlugin implements Plugin<Project> {
  @Override
  void apply(Project project) {
    project.tasks.create("bump", BumpDependencies).dependsOn("updateDependencies")
  }

  static class BumpDependencies extends DefaultTask {

    @TaskAction
    void bumpDependencies() {
      def repos = [
          "clouddriver",
          "echo",
          "fiat",
          "front50",
          "gate",
          "halyard",
          "igor",
          // "kayenta", // https://github.com/spinnaker/kayenta/pull/389
          "orca",
          "rosco",
      ]

      GitHubClient client = new GitHubClient()
      String accessToken = project.property('github.token')
      client.setOAuth2Token(accessToken)

      UserService userSvc = new UserService(client)
      User user = userSvc.getUser()

      RepositoryService repoSvc = new RepositoryService(client)
      repoSvc.getRepositories().findAll {
        repos.contains(it.name) && it.owner.login == user.login
      }.collect { Repository userFork ->
        // The parent and source details are only populated from this get request
        // (as in they're not in the list request above).
        println "Getting details for ${userFork.cloneUrl}"
        repoSvc.getRepository(userFork)
      }.each { Repository userFork ->
        Repository upstream = userFork.source

        def dest = new File("clones")
        if (dest.exists()) {
          dest.deleteDir()
        }

        println "Cloning repo ${upstream.cloneUrl} into clones/${upstream.name}"
        def grgit = Grgit.clone(
            dir: "clones/${userFork.name}",
            uri: upstream.cloneUrl,
            remote: "upstream",
            credentials: new Credentials("ignored", accessToken))

        def branches = grgit.branch.list()
        if (branches.find { it.name == "auto-bump" }) {
          println "Deleting branch 'auto-bump' in repo $upstream.name"
          grgit.branch.remove(names: ["auto-bump"], force: true)
        }

        grgit.checkout(
            branch: "auto-bump",
            createBranch: true
        )

        println "Updating spinnakerDependenciesVersion to ${project.version}"
        ant.replaceregexp(
            file: "clones/${upstream.name}/build.gradle",
            match: "spinnakerDependenciesVersion = '.*?'",
            replace: "spinnakerDependenciesVersion = '${project.version}'")

        println "Committing changes"
        grgit.commit(
            message: "chore(dependencies): Autobump spinnaker-dependencies",
            all: true)
        grgit.remote.add(name: "userFork", url: userFork.gitUrl)

        println "Pushing changes to 'auto-bump' branch of ${userFork.gitUrl}"
        grgit.push(
            remote: "userFork",
            refsOrSpecs: ["auto-bump"],
            force: true)

        if (!project.hasProperty("dryrun")) {
          PullRequestService prSvc = new PullRequestService(client)
          def createdPr = prSvc.createPullRequest(upstream, new PullRequest(
                  title: "chore(dependencies): Autobump spinnaker-dependencies",
                  body: "This is an automated PR! If you have any issues, please contact @ttomsu.",
                  base: new PullRequestMarker(label: "master"),
                  head: new PullRequestMarker(label: "${userFork.owner.login}:auto-bump"),
          ))
          println "Created PR ${createdPr.htmlUrl}"

          def latestReleaseUri = "/repos/spinnaker/spinnaker-dependencies/releases/latest"
          GitHubResponse resp = client.get(new GitHubRequest(uri: latestReleaseUri, type: LatestRelease.class))
          String reviewer = (resp.body as LatestRelease)?.author?.login
          println "Found author of last release: $reviewer."

          if (reviewer) {
            println "Requesting review from ${reviewer}"
            def uri = "/repos/spinnaker/${upstream.name}/pulls/${createdPr.number}/requested_reviewers"
            def data = ["reviewers": [reviewer]]
            client.post(uri, data, null /* return Type */)
            println "Reviewers requested!"
          }
        } else {
          println "Skipping PR creation because of -Pdryrun"
        }
      }
    }
  }

  static class LatestRelease {
    User author
  }
}
