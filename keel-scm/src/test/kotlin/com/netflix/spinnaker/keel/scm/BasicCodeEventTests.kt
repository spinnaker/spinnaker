package com.netflix.spinnaker.keel.scm

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isFailure
import strikt.assertions.isSuccess

class BasicCodeEventTests : JUnit5Minutests {
  fun tests() = rootContext {
    context("base class validation") {
      test("passes when repo key is well-formed") {
        expectCatching {
          val goodCodeEvent = object : CodeEvent("stash/project/repo", "master")  {
            override val type: String = "fake"
          }
          goodCodeEvent.validate()
        }.isSuccess()
      }

      test("throws an exception when the repo key is mal-formed") {
        expectCatching {
          val badCodeEvent = object : CodeEvent("a-bad-repo-key", "master") {
            override val type: String = "fake"
          }
          badCodeEvent.validate()
        }.isFailure()
          .isA<InvalidCodeEvent>()
      }
    }
  }
}
