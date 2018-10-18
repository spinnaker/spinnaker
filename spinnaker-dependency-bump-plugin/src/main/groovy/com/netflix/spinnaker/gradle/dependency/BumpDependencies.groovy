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

  private static final String COMMIT_BODY =
      '''This is an automated PR! If you have any issues, please contact ttomsu@.

You can merge all of these PRs from the `spinnaker-dependencies` project using the command below and a Github access token. Generate a GH token from https://github.com/settings/tokens, and run the following:

```
GITHUB_ACCESS_TOKEN=
./gradlew mergeAllAutoBumpPRs -Pgithub.token=$GITHUB_ACCESS_TOKEN
```

If something has gone wrong, and you don't want to manually close all of the autobump PRs, you can close all open PRs with the 'autobump' label using this command:

```
GITHUB_ACCESS_TOKEN=
./gradlew closeAllAutoBumpPRs -Pgithub.token=$GITHUB_ACCESS_TOKEN
```
'''

  @TaskAction
  void bumpDependencies() {
    GitHubClient client = new GitHubClient()
    String accessToken = project.property('github.token')
    client.setOAuth2Token(accessToken)

    UserService userSvc = new UserService(client)
    User user = userSvc.getUser()

    String reviewer
    try {
      String latestReleaseUri = "/repos/spinnaker/spinnaker-dependencies/releases/latest"
      GitHubResponse resp = client.get(new GitHubRequest(uri: latestReleaseUri, type: LatestRelease.class))
      reviewer = (resp.body as LatestRelease)?.author?.login
      logger.lifecycle("Found author of last release: $reviewer.")
    } catch (Exception e) {
      logger.lifecycle("Could not get most recent spinnaker-dependencies releaser: ${e.getMessage()}")
    }


    RepositoryService repoSvc = new RepositoryService(client)
    repoSvc.getRepositories().findAll {
      SpinnakerDependencyBumpPlugin.REPOS.contains(it.name) && it.owner.login == user.login
    }.collect { Repository userFork ->
      // The parent and source details are only populated from this get request
      // (as in they're not in the list request above).
      logger.lifecycle("Getting details for ${userFork.cloneUrl}")
      repoSvc.getRepository(userFork)
    }.each { Repository userFork ->
      Repository upstream = userFork.source

      def dest = new File("clones")
      if (dest.exists()) {
        dest.deleteDir()
      }

      logger.lifecycle("Cloning repo ${upstream.cloneUrl} into clones/${upstream.name}")
      def grgit = Grgit.clone(
          dir: "clones/${userFork.name}",
          uri: upstream.cloneUrl,
          remote: "upstream",
          credentials: new Credentials("ignored", accessToken))

      def branches = grgit.branch.list()
      if (branches.find { it.name == "auto-bump" }) {
        logger.lifecycle("Deleting branch 'auto-bump' in repo $upstream.name")
        grgit.branch.remove(names: ["auto-bump"], force: true)
      }

      grgit.checkout(
          branch: "auto-bump",
          createBranch: true
      )

      logger.lifecycle("Updating spinnakerDependenciesVersion to ${project.version}")
      ant.replaceregexp(
          file: "clones/${upstream.name}/build.gradle",
          match: "spinnakerDependenciesVersion = '.*?'",
          replace: "spinnakerDependenciesVersion = '${project.version}'")

      logger.lifecycle("Committing changes")
      grgit.commit(
          message: "chore(dependencies): Autobump spinnaker-dependencies",
          all: true)
      grgit.remote.add(name: "userFork", url: userFork.cloneUrl)

      logger.lifecycle("Pushing changes to 'auto-bump' branch of ${userFork.cloneUrl}")
      grgit.push(
          remote: "userFork",
          refsOrSpecs: ["auto-bump"],
          force: true)

      PullRequest pr
      if (!project.hasProperty("dryrun")) {
        PullRequestService prSvc = new PullRequestService(client)
        try {
          pr = prSvc.createPullRequest(upstream, new PullRequest(
              title: "chore(dependencies): Autobump spinnaker-dependencies",
              body: COMMIT_BODY,
              base: new PullRequestMarker(label: "master"),
              head: new PullRequestMarker(label: "${userFork.owner.login}:auto-bump"),
          ))
          logger.lifecycle("Created PR ${pr.htmlUrl}")
        } catch (Exception e) {
          logger.lifecycle("Could not create PR in ${userFork.cloneUrl}: ${e.getMessage()}")
          pr = prSvc.getPullRequests(upstream, "open").find { PullRequest existingPR ->
            existingPR.head.label == "${userFork.owner.login}:auto-bump"
          }
          if (!pr) {
            logger.lifecycle("Couldn't find existing PR")
            return
          }
        }

        def labelsUri = "/repos/spinnaker/${upstream.name}/issues/${pr.number}/labels"
        List<String> labels = ["autobump"]
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
        logger.lifecycle("Skipping PR creation because of -Pdryrun")
      }
    }
  }

  static class LatestRelease {
    User author
  }
}
