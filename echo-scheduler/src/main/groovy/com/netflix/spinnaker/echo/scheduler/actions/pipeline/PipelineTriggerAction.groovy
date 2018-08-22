/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.scheduler.actions.pipeline
import com.netflix.scheduledactions.ActionSupport
import com.netflix.scheduledactions.Context
import com.netflix.scheduledactions.Execution
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import com.netflix.spinnaker.echo.pipelinetriggers.orca.PipelineInitiator
import com.netflix.spinnaker.echo.scheduler.actions.ActionDependencies

import com.netflix.spinnaker.echo.scheduler.actions.pipeline.impl.PipelineTriggerConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PipelineTriggerAction extends ActionSupport {
  static final Logger LOGGER = LoggerFactory.getLogger(PipelineTriggerAction)

  private final Registry registry

  PipelineTriggerAction(Registry registry) {
    this.registry = registry
  }

  @Override
  void execute(Context context, Execution execution) throws Exception {
    try {
      def pipelineInitiator = (PipelineInitiator) ActionDependencies.getBean(PipelineInitiator)
      def pipelineCache = (PipelineCache) ActionDependencies.getBean(PipelineCache)
      def pipeline = PipelineTriggerConverter.fromParameters(pipelineCache, context.parameters)

      LOGGER.info("Executing PipelineTriggerAction for '${pipeline}'...")
      pipelineInitiator.call(pipeline)

      Id id = registry.createId("pipelines.triggered")
        .withTag("monitor", getClass().getSimpleName())
        .withTag("application", Optional.ofNullable(pipeline.getApplication()).orElse("null"))
      registry.counter(id).increment()

      LOGGER.info("Successfully executed PipelineTriggerAction for '${pipeline}")
    } catch (Exception e) {
      Id id = registry.createId("pipelines.triggered.errors")
        .withTag("monitor", getClass().getSimpleName())
      registry.counter(id).increment()

      LOGGER.error("Exception occurred while executing PipelineTriggerAction", e)
      throw e
    }
  }
}
