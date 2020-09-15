package com.netflix.spinnaker.keel.core

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isLessThan

class VersioningStrategyComparatorsTests : JUnit5Minutests {
  fun tests() = rootContext<Comparator<String>> {
    context("NETFLIX_SEMVER_COMPARATOR") {

      fixture {
        NETFLIX_SEMVER_COMPARATOR
      }

      test("compares semver versions correctly") {
        // note: comparator is descending by default, hence the backward less/greater than
        expectThat(compare("0.368.0-h473.dfc46fb", "0.367.0-h468.0493add"))
          .isLessThan(0)
        expectThat(compare("0.367.0-h468.0493add", "0.368.0-h473.dfc46fb"))
          .isGreaterThan(0)
        expectThat(compare("0.368.0-h473.dfc46fb", "0.368.0-h473.dfc46fb"))
          .isEqualTo(0)

        // pre-release versions
        expectThat(compare("1.0.0-dev-h18.d5230a7", "1.0.0-dev-h17.7d1e1c3"))
          .isLessThan(0)
        expectThat(compare("1.0.0-rc-h18.d5230a7", "1.0.0-dev-h18.d5230a7"))
          .isLessThan(0)
        expectThat(compare("1.0.0-h18.d5230a7", "1.0.0-rc-h18.d5230a7"))
          .isLessThan(0)
        expectThat(compare("1.0.0", "1.0.0-rc-h18.d5230a7"))
          .isLessThan(0)
      }

      test("compares versions prefixed by debian package names correctly") {
        expectThat(compare("mypkg-0.368.0-h473.dfc46fb", "mypkg-0.367.0-h468.0493add"))
          .isLessThan(0)
        expectThat(compare("mypkg-0.367.0-h468.0493add", "mypkg-0.368.0-h473.dfc46fb"))
          .isGreaterThan(0)
        expectThat(compare("mypkg-0.368.0-h473.dfc46fb", "mypkg-0.368.0-h473.dfc46fb"))
          .isEqualTo(0)

        // pre-release versions
        expectThat(compare("mypkg-1.0.0-dev-h18.d5230a7", "mypkg-1.0.0-dev-h17.7d1e1c3"))
          .isLessThan(0)
        expectThat(compare("mypkg-1.0.0-rc-h18.d5230a7", "mypkg-1.0.0-dev-h18.d5230a7"))
          .isLessThan(0)
        expectThat(compare("mypkg-1.0.0-h18.d5230a7", "mypkg-1.0.0-rc-h18.d5230a7"))
          .isLessThan(0)
        expectThat(compare("mypkg-1.0.0", "mypkg-1.0.0-rc-h18.d5230a7"))
          .isLessThan(0)
      }
    }
  }
}