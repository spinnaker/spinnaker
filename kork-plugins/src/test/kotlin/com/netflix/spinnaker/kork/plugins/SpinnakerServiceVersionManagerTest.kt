/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.kork.plugins

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class SpinnakerServiceVersionManagerTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("Service version satisfies plugin requirement constraint") {
      val satisfiedConstraint = subject.checkVersionConstraint("0.0.9",
        "$serviceName<=1.0.0")
      expectThat(satisfiedConstraint).isTrue()
    }

    test("Service version does not satisfy plugin requirement constraint") {
      val satisfiedConstraint = subject.checkVersionConstraint("0.0.9",
        "$serviceName>=1.0.0")
      expectThat(satisfiedConstraint).isFalse()
    }

    test("Plugin version X is less than plugin version Y by -1") {
      val x = "2.9.8"
      val y = "2.9.9"
      val comparisonResult = subject.compareVersions(x, y)
      expectThat(comparisonResult).isEqualTo(-1)
    }

    test("Plugin version X is greater than plugin version Y by 1") {
      val x = "3.0.0"
      val y = "2.0.0"
      val comparisonResult = subject.compareVersions(x, y)
      expectThat(comparisonResult).isEqualTo(1)
    }

    test("Empty requires is allowed") {
      val satisfiedConstraint = subject.checkVersionConstraint("0.0.9", "")
      expectThat(satisfiedConstraint).isTrue()
    }
  }

  private class Fixture {
    val serviceName = "orca"
    val subject = SpinnakerServiceVersionManager(serviceName)
  }
}
