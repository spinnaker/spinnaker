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

import com.netflix.spinnaker.keel.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DependsOnConstraint
import com.netflix.spinnaker.keel.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.normalize
import com.netflix.spinnaker.keel.plugin.CannotResolveCurrentState
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.SupportedKind
import com.netflix.spinnaker.keel.test.DummyResource
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.submittedResource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull

class AdHocDifferTests : JUnit5Minutests {
  class Fixture {
    val plugin1 = mockk<ResourceHandler<DummyResourceSpec, DummyResource>>(relaxUnitFun = true)
    val plugin2 = mockk<ResourceHandler<DummyResourceSpec, DummyResource>>(relaxUnitFun = true)
    val subject = AdHocDiffer(listOf(plugin1, plugin2))
    val subResource = submittedResource(
      apiVersion = "plugin1.$SPINNAKER_API_V1",
      kind = "foo"
    )
    val resource = subResource.normalize()
    val deliveryConfig = SubmittedDeliveryConfig(
      name = "keel-manifest",
      application = "keel",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(DebianArtifact(name = "keel")),
      environments = setOf(
        SubmittedEnvironment(
          name = "test",
          resources = setOf(subResource)
        ),
        SubmittedEnvironment(
          name = "prod",
          resources = setOf(subResource),
          constraints = setOf(DependsOnConstraint("test"))
        )
      )
    )
  }

  fun tests() = rootContext<Fixture> {

    fixture { Fixture() }

    before {
      every { plugin1.name } returns "plugin1"
      every { plugin1.supportedKind } returns SupportedKind("plugin1.$SPINNAKER_API_V1", "foo", DummyResourceSpec::class.java)
      every { plugin2.name } returns "plugin2"
      every { plugin2.supportedKind } returns SupportedKind("plugin2.$SPINNAKER_API_V1", "bar", DummyResourceSpec::class.java)

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
        val diffResult = runBlocking { subject.calculate(subResource) }
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
        val diffResult = runBlocking { subject.calculate(subResource) }
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
        val diffResult = runBlocking { subject.calculate(subResource) }
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
      test("resource error result") {
        val diffResult = runBlocking { subject.calculate(subResource) }
        expect {
          that(diffResult.status).isEqualTo(DiffStatus.ERROR)
          that(diffResult.diff).isNull()
          that(diffResult.errorMsg).isNotNull()
          that(diffResult.resourceId).isNotNull()
          that(diffResult.resource).isNotNull()
        }
      }
    }

    context("diffing a delivery config") {
      before {
        coEvery {
          plugin1.current(resource)
        } returns DummyResource(resource.spec)
      }
      test("valid diff") {
        val diff = subject.calculate(deliveryConfig)
        expect {
          that(diff.size).isEqualTo(2)
          that(diff.first().name).isEqualTo("test")
          that(diff.first().resourceDiffs.size).isEqualTo(1)
          that(diff.last().name).isEqualTo("prod")
          that(diff.last().resourceDiffs.size).isEqualTo(1)
        }
      }
    }
  }
}
