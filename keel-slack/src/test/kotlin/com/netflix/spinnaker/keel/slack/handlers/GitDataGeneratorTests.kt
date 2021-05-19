package com.netflix.spinnaker.keel.slack.handlers

import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
import com.netflix.spinnaker.keel.services.mockCacheFactory
import com.netflix.spinnaker.keel.services.mockScmInfo
import com.netflix.spinnaker.keel.slack.SlackService
import com.netflix.spinnaker.time.MutableClock
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.block.composition.TextObject
import com.slack.api.model.kotlin_extension.block.withBlocks
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isNotNull
import strikt.assertions.isTrue

class GitDataGeneratorTests : JUnit5Minutests {

  class Fixture {
    val scmInfo: ScmInfo = mockk()
    val slackService: SlackService = mockk()
    val config: BaseUrlConfig = BaseUrlConfig()
    val artifactVersionLinks = ArtifactVersionLinks(mockScmInfo(), mockCacheFactory())

    val subject = GitDataGenerator(scmInfo, config, slackService, artifactVersionLinks)

    val clock: MutableClock = MutableClock()

    val application = "keel"
    val environment = "test"
    val imageUrl = "http://image-of-something-cute"
    val altText = "notification about something"

    val artifactNoMetadata = PublishedArtifact(
      name = "keel-service",
      type = "docker",
      version = "keel-service-1234",
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    before {
      every {
        slackService.getUsernameByEmail(any())
      } returns "@keel"
    }

    context("generating commit info") {
      test("generates something even if there is no commit info") {
        val blocks = withBlocks {
          section {
            subject.generateCommitInfo(
              sectionBlockBuilder = this,
              application = application,
              imageUrl = imageUrl,
              artifact = artifactNoMetadata,
              altText = altText,
              env = environment
            )
          }
        }

        expectThat(blocks.first())
          .isA<SectionBlock>()
          .get { text }.isNotNull()

        val text: TextObject = (blocks.first() as SectionBlock).text
        expect {
          that(text.toString().contains(artifactNoMetadata.version)).isTrue()
          that(text.toString().contains(artifactNoMetadata.reference)).isTrue()
        }
      }
    }
  }
}
