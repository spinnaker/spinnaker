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
    val config = """
      |bionic:
      |  candidate: nflx-base-5.375.0-h1224.8808866
      |  unstable: nflx-base-5.375.1-h1225.8808866~unstable
      |  release: nflx-base-5.365.0-h1191.6a005e3
      |xenial:
      |  candidate: nflx-base-5.375.0-h1224.8808866
      |  unstable: nflx-base-5.375.1-h1225.8808866~unstable
      |  release: nflx-base-5.365.0-h1191.6a005e3
      |  previous: nflx-base-5.344.0-h1137.cc92ef3
      |""".trimMargin()
    val baseImageCache = DefaultBaseImageCache(YAMLMapper().readValue(config))
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    test("returns the base AMI version for a valid os/label") {
      expectThat(baseImageCache.getBaseAmiVersion("bionic", RELEASE))
        .isEqualTo("nflx-base-5.365.0-h1191.6a005e3")
    }

    test("throw an exception for an invalid label") {
      expectThrows<UnknownBaseImage> {
        baseImageCache.getBaseAmiVersion("bionic", PREVIOUS)
      }
    }

    test("throw an exception for an invalid os") {
      expectThrows<UnknownBaseImage> {
        baseImageCache.getBaseAmiVersion("windows", RELEASE)
      }
    }
  }
}
