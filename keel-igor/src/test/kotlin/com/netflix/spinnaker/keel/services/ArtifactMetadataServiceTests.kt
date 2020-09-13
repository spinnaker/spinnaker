package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.igor.BuildService
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.Job
import com.netflix.spinnaker.keel.api.artifacts.PullRequest
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.model.Build
import com.netflix.spinnaker.model.GenericGitRevision
import com.netflix.spinnaker.model.Result
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import retrofit.RetrofitError
import retrofit.client.Response
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure

class ArtifactMetadataServiceTests : JUnit5Minutests {
  object Fixture {
    val buildService: BuildService = mockk()
    val artifactMetadataService = ArtifactMetadataService(buildService)
    val buildsList: List<Build> = listOf(
      Build(
        number = 1,
        name = "job bla bla",
        id = "1234",
        building = false,
        fullDisplayName = "job bla bla",
        url = "jenkins.com",
        result = Result.SUCCESS,
        scm = listOf(
          GenericGitRevision(
            sha1 = "a15p0",
            message = "this is a commit message",
            committer = "keel-user",
            compareUrl = "https://github.com/spinnaker/keel/commit/a15p0"
          )
        ),
        properties = mapOf(
          "startedAt" to "yesterday",
          "completedAt" to "today",
          "projectKey" to "spkr",
          "repoSlug" to "keel",
          "author" to "keel-user",
          "message" to "this is a commit message",
          "pullRequestNumber" to "111",
          "pullRequestUrl" to "www.github.com/pr/111"
        )
      )
    )
  }

  fun tests() = rootContext<Fixture> {
    context("get artifact metadata") {
      fixture { Fixture }


      context("with valid commit id and build number") {
        before {
          coEvery {
            buildService.getArtifactMetadata(any(), any())
          } returns buildsList
        }

        test("succeeds and converted the results correctly") {
          val results = runBlocking {
            artifactMetadataService.getArtifactMetadata("1", "a15p0")
          }

          expectThat(results).isEqualTo(
            ArtifactMetadata(
              BuildMetadata(
                id = 1,
                uid = "1234",
                startedAt = "yesterday",
                completedAt = "today",
                job = Job(
                  name = "job bla bla",
                  link = "jenkins.com"
                ),
                number = "1",
                status = Result.SUCCESS.toString()
              ),
              GitMetadata(
                commit = "a15p0",
                author = "keel-user",
                repo = Repo(
                  name = "keel",
                  link = ""
                ),
                pullRequest = PullRequest(
                  number = "111",
                  url = "www.github.com/pr/111"
                ),
                commitInfo = Commit(
                  sha = "a15p0",
                  message = "this is a commit message",
                  link = "https://github.com/spinnaker/keel/commit/a15p0"
                ),
                project = "spkr"
              )
            )
          )
        }

        test("commit id length is long, expect short commit in return") {
          val results = runBlocking {
            artifactMetadataService.getArtifactMetadata("1", "a15p0a15p0a15p0")
          }
          expectThat(results).get {
              results?.gitMetadata?.commit
            }.isEqualTo("a15p0a1")
          }
        }


      context("return an empty results from the CI provider") {
        before {
          coEvery {
            buildService.getArtifactMetadata(any(), any())
          } returns listOf()
        }

        test("return null") {
          val results = runBlocking {
            artifactMetadataService.getArtifactMetadata("1", "a15p0")
          }

          expectThat(results).isEqualTo(null)
        }
      }

      context("with HTTP error coming from igor") {
        val retrofitError = RetrofitError.httpError(
          "http://igor",
          Response("http://igor", 404, "not found", emptyList(), null),
          null, null
        )

        before {
          coEvery {
            buildService.getArtifactMetadata(any(), any())
          } throws retrofitError
        }

        test("throw an http error from fallback method") {
          expectCatching {
            artifactMetadataService.getArtifactMetadata("1", "a15p0")
          }
            .isFailure()
            .isEqualTo(retrofitError)
        }
      }
    }
  }
}
