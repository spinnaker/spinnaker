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

import com.netflix.spinnaker.kork.plugins.VersionRequirementsParser.VersionRequirementOperator
import com.netflix.spinnaker.kork.plugins.VersionRequirementsParser.VersionRequirements
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

class VersionRequirementsParserTest : JUnit5Minutests {

  fun tests() = rootContext {
    test("parses a version string") {
      expectThat(VersionRequirementsParser.parse("clouddriver>=1.0.0"))
        .and {
          get { service }.isEqualTo("clouddriver")
          get { operator }.isEqualTo(VersionRequirementOperator.GT_EQ)
          get { version }.isEqualTo("1.0.0")
        }
    }

    test("parses a list of version strings") {
      expectThat(VersionRequirementsParser.parseAll("orca<8.0.0,deck>=1.1.0"))
        .hasSize(2)
        .and {
          get { this[0].service }.isEqualTo("orca")
          get { this[1].service }.isEqualTo("deck")
        }
    }

    test("converts a list of requirements to a string") {
      val result = VersionRequirementsParser.stringify(listOf(
        VersionRequirements("clouddriver", VersionRequirementOperator.GT, "1.0.0"),
        VersionRequirements("orca", VersionRequirementOperator.GT, "3.0.0")
      ))
      expectThat(result).isEqualTo("clouddriver>1.0.0,orca>3.0.0")
    }

    test("InvalidPluginVersionRequirementException thrown") {
      expectThrows<VersionRequirementsParser.InvalidPluginVersionRequirementException> {
        VersionRequirementsParser.parseAll("gate=foo")
      }
    }
  }
}
