package com.netflix.spinnaker.keel.bakery

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.artifacts.BaseLabel.PREVIOUS
import com.netflix.spinnaker.keel.api.artifacts.BaseLabel.RELEASE
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo

internal class DefaultBaseImageCacheTests : JUnit5Minutests {

  object Fixture {
    val config =
      """
      |bionic:
      |  candidate: bionicbase-x86_64-202103092356-ebs
      |  unstable: bionicbase-unstable-x86_64-202103092010-ebs
      |  release: bionicbase-x86_64-202103092356-ebs
      |bionic-classic:
      |  release: bionic-classicbase-x86_64-202102241716-ebs
      |  candidate: bionic-classicbase-x86_64-202103092356-ebs
      |  unstable: bionic-classicbase-unstable-x86_64-202103092010-ebs
      |  previous: bionic-classicbase-x86_64-202101262358-ebs
      |xenial:
      |  candidate: xenialbase-x86_64-202103092356-ebs
      |  unstable: xenialbase-unstable-x86_64-202103092010-ebs
      |  release: xenialbase-x86_64-202103092356-ebs
      |  previous: xenialbase-x86_64-202101262358-ebs
      |""".trimMargin()
    val baseImageCache = DefaultBaseImageCache(YAMLMapper().readValue(config))
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    test("returns the base AMI name for a valid os/label") {
      expectThat(baseImageCache.getBaseAmiName("bionic", RELEASE))
        .isEqualTo("bionicbase-x86_64-202103092356-ebs")
    }

    test("throw an exception for an invalid label") {
      expectThrows<UnknownBaseImage> {
        baseImageCache.getBaseAmiName("bionic", PREVIOUS)
      }
    }

    test("throw an exception for an invalid os") {
      expectThrows<UnknownBaseImage> {
        baseImageCache.getBaseAmiName("windows", RELEASE)
      }
    }
  }
}
