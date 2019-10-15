/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */
package com.netflix.spinnaker.keel.diff

import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.plugin.CannotResolveCurrentState
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.test.DummyResource
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.test.submittedResource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull

class AdHocDifferTests : JUnit5Minutests {
  class Fixture {
    val plugin1 = mockk<ResourceHandler<DummyResourceSpec, DummyResource>>(relaxUnitFun = true)
    val plugin2 = mockk<ResourceHandler<DummyResourceSpec, DummyResource>>(relaxUnitFun = true)
    val subject = AdHocDiffer(listOf(plugin1, plugin2))
    val resource = resource(
      apiVersion = SPINNAKER_API_V1.subApi("plugin1"),
      kind = "foo"
    )
    val subResource = submittedResource(
      apiVersion = SPINNAKER_API_V1.subApi("plugin1"),
      kind = "foo"
    )
  }

  fun tests() = rootContext<Fixture> {

    fixture { Fixture() }

    before {
      every { plugin1.name } returns "plugin1"
      every { plugin1.apiVersion } returns SPINNAKER_API_V1.subApi("plugin1")
      every { plugin1.supportedKind } returns (ResourceKind(SPINNAKER_API_V1.subApi("plugin1").group, "foo", "foos") to DummyResourceSpec::class.java)
      every { plugin2.name } returns "plugin2"
      every { plugin2.apiVersion } returns SPINNAKER_API_V1.subApi("plugin2")
      every { plugin2.supportedKind } returns (ResourceKind(SPINNAKER_API_V1.subApi("plugin2").group, "bar", "bars") to DummyResourceSpec::class.java)

      coEvery {
        plugin1.normalize(subResource)
      } returns resource
      coEvery {
        plugin1.desired(resource)
      } returns DummyResource(resource.spec)
    }

    context("current state and desired state match") {
      before {
        coEvery {
          plugin1.current(resource)
        } returns DummyResource(resource.spec)
      }
      test("no diff result") {
        val diffResult = subject.calculate(subResource)
        expect {
          that(diffResult.status).isEqualTo(DiffStatus.NO_DIFF)
          that(diffResult.diff).isNull()
          that(diffResult.errorMsg).isNull()
          that(diffResult.resourceId).isEqualTo(resource.id)
          that(diffResult.resource).isEqualTo(resource)
        }
      }
    }

    context("resource doesn't exist") {
      before {
        coEvery {
          plugin1.current(resource)
        } returns null
      }
      test("resource missing result") {
        val diffResult = subject.calculate(subResource)
        expect {
          that(diffResult.status).isEqualTo(DiffStatus.MISSING)
          that(diffResult.diff).isNull()
        }
      }
    }

    context("current is different") {
      before {
        coEvery {
          plugin1.current(resource)
        } returns DummyResource(resource.spec.copy(data = "oh wow this is different"))
      }
      test("resource diff result") {
        val diffResult = subject.calculate(subResource)
        expect {
          that(diffResult.status).isEqualTo(DiffStatus.DIFF)
          that(diffResult.diff).isNotNull()
        }
      }
    }

    context("exception resolving current") {
      before {
        coEvery {
          plugin1.current(resource)
        } throws CannotResolveCurrentState(resource.id, RuntimeException("oopsie"))
      }
      test("resource diff result") {
        val diffResult = subject.calculate(subResource)
        expect {
          that(diffResult.status).isEqualTo(DiffStatus.ERROR)
          that(diffResult.diff).isNull()
          that(diffResult.errorMsg).isNotNull()
          that(diffResult.resourceId).isNotNull()
          that(diffResult.resource).isNotNull()
        }
      }
    }
  }
}
