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

import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import com.netflix.spinnaker.echo.pipelinetriggers.orca.PipelineInitiator
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Job run by the Quartz scheduler to actually trigger a pipeline
 */
@Component
class PipelineTriggerJob implements Job {
  static final Logger LOGGER = LoggerFactory.getLogger(PipelineTriggerJob)

  PipelineInitiator pipelineInitiator
  PipelineCache pipelineCache

  @Autowired
  PipelineTriggerJob(PipelineInitiator pipelineInitiator, PipelineCache pipelineCache) {
    this.pipelineInitiator = pipelineInitiator
    this.pipelineCache = pipelineCache
  }

  @Override
  void execute(JobExecutionContext context) throws JobExecutionException {
    Pipeline pipeline = null

    try {
      pipeline = TriggerConverter.toPipeline(pipelineCache, context.getMergedJobDataMap().getWrappedMap())
      def eventId = pipeline.trigger.eventId ? pipeline.trigger.eventId : "not set"

      LOGGER.info("Executing PipelineTriggerJob for '${pipeline}', eventId='${eventId}', triggerId='${context.trigger.key.name}'")
      pipelineInitiator.startPipeline(pipeline, PipelineInitiator.TriggerSource.CRON_SCHEDULER)
    } catch (Exception e) {
      LOGGER.error("Exception occurred while executing PipelineTriggerJob for ${pipeline}", e)
      throw new JobExecutionException(e)
    }
  }
}
