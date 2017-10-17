/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.config

import com.netflix.spinnaker.orca.dryrun.DryRunStage
import com.netflix.spinnaker.orca.pipeline.DefaultStageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("dryrun.enabled")
@ComponentScan("com.netflix.spinnaker.orca.dryrun")
@EnableConfigurationProperties(DryRunProperties::class)
open class DryRunConfiguration {
  @Bean
  open fun dryRunStageDefinitionBuilderFactory(
    stageDefinitionBuilders: Collection<StageDefinitionBuilder>
  ): StageDefinitionBuilderFactory {
    log.info("Dry run trigger support enabled")
    return object : DefaultStageDefinitionBuilderFactory(stageDefinitionBuilders) {
      override fun builderFor(stage: Stage<*>): StageDefinitionBuilder =
        stage.getExecution().let { execution ->
          super.builderFor(stage).let {
            if (execution is Pipeline && execution.trigger["type"] == "dryrun") {
              DryRunStage(it)
            } else {
              it
            }
          }
        }
    }
  }

  private val log = LoggerFactory.getLogger(javaClass)
}
