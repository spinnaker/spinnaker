package com.netflix.spinnaker.keel.bakery

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.bakery.api.BaseLabel.PREVIOUS
import com.netflix.spinnaker.keel.bakery.api.BaseLabel.RELEASE
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo

internal class DefaultBaseImageCacheTests : JUnit5Minutests {

  object Fixture {
    val config = """
      |bionic:
      |  candidate: bionicbase-x86_64-201904232145-ebs
      |  unstable: bionicbase-unstable-x86_64-201904252133-ebs
      |  release: bionicbase-x86_64-201904041959-ebs
      |xenial:
      |  candidate: xenialbase-x86_64-201904232145-ebs
      |  unstable: xenialbase-unstable-x86_64-201904252133-ebs
      |  release: xenialbase-x86_64-201904041959-ebs
      |  previous: xenialbase-x86_64-201902202219-ebs
      |""".trimMargin()
    val baseImageCache = DefaultBaseImageCache(YAMLMapper().readValue(config))
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    test("returns the AMI id for a valid os/label") {
      expectThat(baseImageCache.getBaseImage("bionic", RELEASE))
        .isEqualTo("bionicbase-x86_64-201904041959-ebs")
    }

    test("throw an exception for an invalid label") {
      expectThrows<UnknownBaseImage> {
        baseImageCache.getBaseImage("bionic", PREVIOUS)
      }
    }

    test("throw an exception for an invalid os") {
      expectThrows<UnknownBaseImage> {
        baseImageCache.getBaseImage("windows", RELEASE)
      }
    }
  }
}
