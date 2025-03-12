package com.netflix.spinnaker.keel.scm

import com.netflix.spinnaker.keel.caffeine.CacheFactory
import com.netflix.spinnaker.keel.caffeine.CacheProperties
import com.netflix.spinnaker.keel.igor.ScmService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

class ScmUtilsTests : JUnit5Minutests {

  class Fixture {
    private val scmData = mapOf("stash" to "https://stash")
    private val cacheFactory = CacheFactory(mockk(relaxed = true), CacheProperties())
    val scmService: ScmService = mockk()

    val subject = ScmUtils(cacheFactory, scmService)

    fun setupMocks() {
      every {
        scmService.getScmInfo()
      } returns scmData
    }
  }


  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    before {
      setupMocks()
    }

    test("branch links") {
      val branch = "main"
      expectThat(subject.getBranchLink("stash", "proj", "keel", branch)).isNotNull().contains(branch)
      expectThat(subject.getBranchLink("otherScm", "proj", "keel", branch)).isNull()

      verify(exactly = 2) {
        scmService.getScmInfo()
      }
    }

    test("commit links") {
      val commitHash = "hash123"
      expectThat(subject.getCommitLink(
        CommitCreatedEvent(repoKey = "stash/project/repo", targetBranch =  "main", commitHash = commitHash)
      )).isNotNull().contains(commitHash)
    }

    test("cache works properly") {
      subject.getBranchLink("stash", "proj", "keel", "branch")
      subject.getBranchLink("stash", "proj2", "keel2", "branch")

      verify(exactly = 1) {
        scmService.getScmInfo()
      }
    }


  }
}
