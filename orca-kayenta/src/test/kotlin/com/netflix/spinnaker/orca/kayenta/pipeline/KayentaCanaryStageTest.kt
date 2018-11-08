/*
 * Copyright 2018 Netflix, Inc.
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
 */

package com.netflix.spinnaker.orca.kayenta.pipeline

import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.time.fixedClock
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

object KayentaCanaryStageTest : Spek({

  val clock = fixedClock()
  val builder = KayentaCanaryStage(clock)

  describe("planning a canary stage") {
    given("canary deployments are requested") {
      val kayentaCanaryStage = stage {
        type = "kayentaCanary"
        name = "Run Kayenta Canary"
        context["canaryConfig"] = mapOf(
          "metricsAccountName" to "atlas-acct-1",
          "canaryConfigId" to "MySampleAtlasCanaryConfig",
          "scopes" to listOf(mapOf(
            "controlScope" to "some.host.node",
            "experimentScope" to "some.other.host.node",
            "step" to 60
          )),
          "scoreThresholds" to mapOf("marginal" to 75, "pass" to 90),
          "canaryAnalysisIntervalMins" to 6.hoursInMinutes,
          "lifetimeHours" to "12"
        )
        context["deployments"] = mapOf(
          "clusters" to listOf(
            mapOf("control" to mapOf<String, Any>()),
            mapOf("experiment" to mapOf<String, Any>())
          )
        )
      }

      val aroundStages = StageGraphBuilder.beforeStages(kayentaCanaryStage)
        .let { graph ->
          builder.beforeStages(kayentaCanaryStage, graph)
          graph.build()
        }

      it("injects a deploy canary stage") {
        aroundStages.first().apply {
          assertThat(type).isEqualTo(DeployCanaryServerGroupsStage.STAGE_TYPE)
        }
      }

      it("the canary analysis will only start once the deployment is complete") {
        aroundStages.toList()[0..1].let { (first, second) ->
          assertThat(second.requisiteStageRefIds).isEqualTo(setOf(first.refId))
        }
      }
    }
  }
})

private operator fun <E> List<E>.get(range: IntRange): List<E> {
  return subList(range.start, range.endExclusive)
}

private val IntRange.endExclusive: Int
  get() = endInclusive + 1
