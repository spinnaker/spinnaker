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

package com.netflix.spinnaker.orca.plugins.test

import com.netflix.spinnaker.kork.plugins.tck.PluginsTck
import com.netflix.spinnaker.kork.plugins.tck.serviceFixture
import com.netflix.spinnaker.orca.api.preconfigured.jobs.TitusPreconfiguredJobProperties
import com.netflix.spinnaker.orca.plugins.StageDefinitionBuilderExtension
import com.netflix.spinnaker.orca.plugins.TaskExtension1
import com.netflix.spinnaker.orca.plugins.TaskExtension2
import dev.minutest.rootContext
import strikt.api.expect
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

class OrcaPluginsTest : PluginsTck<OrcaPluginsFixture>() {

  fun tests() = rootContext<OrcaPluginsFixture> {
    context("an orca integration test environment and an orca plugin") {
      serviceFixture {
        OrcaPluginsFixture()
      }

      defaultPluginTests()

      test("preconfigured job configuration is correctly loaded from extension") {
        val titusPreconfiguredJobProperties = objectMapper
          .readValue(
            this::class.java.getResource("/preconfigured.yml").readText(),
            TitusPreconfiguredJobProperties::class.java
          )

        expect {
          that(jobService.preconfiguredStages).hasSize(1)
          jobService.preconfiguredStages.first().let { preconfiguredStage ->
            that(preconfiguredStage).isEqualTo(titusPreconfiguredJobProperties)
          }
        }
      }

      test("Stage defintion builder for extension is resolved to the correct type") {
        val stageDefinitionBuilder = stageResolver.getStageDefinitionBuilder(
          StageDefinitionBuilderExtension::class.java.simpleName, "extensionStage"
        )

        expect {
          that(stageDefinitionBuilder.type).isEqualTo(StageDefinitionBuilderExtension().type)
        }
      }

      test("Task extensions are resolved to the correct type") {
        val taskExtension1 = taskResolver.getTaskClass(TaskExtension1::class.java.name)
        val taskExtension2 = taskResolver.getTaskClass(TaskExtension2::class.java.name)

        expect {
          that(taskExtension1.typeName).isEqualTo(TaskExtension1().javaClass.typeName)
          that(taskExtension2.typeName).isEqualTo(TaskExtension2().javaClass.typeName)
        }
      }
    }
  }
}
