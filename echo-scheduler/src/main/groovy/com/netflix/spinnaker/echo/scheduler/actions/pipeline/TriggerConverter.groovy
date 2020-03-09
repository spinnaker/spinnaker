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

import com.netflix.spinnaker.echo.cron.CronExpressionFuzzer
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import org.quartz.CronExpression
import org.quartz.CronTrigger
import org.quartz.JobDataMap

import java.text.ParseException

import static org.quartz.CronScheduleBuilder.cronSchedule
import static org.quartz.TriggerBuilder.newTrigger

class TriggerConverter {
  public static final String JOB_ID = "Pipeline Trigger"

  static Map<String, Object> toParamMap(Trigger trigger, String timeZoneId) {
    def params = [
      id                   : trigger.parent.id,
      application          : trigger.parent.application,
      triggerId            : trigger.id,
      triggerType          : Trigger.Type.CRON.toString(),
      triggerCronExpression: trigger.cronExpression,
      triggerTimeZoneId    : timeZoneId,
      triggerRebake        : Boolean.toString(trigger.rebake),
      triggerEnabled       : "true",
      triggerParameters    : trigger.parameters
    ]

    if (trigger.runAsUser) {
      params.runAsUser = trigger.runAsUser
    }

    return params
  }

  static org.quartz.Trigger toQuartzTrigger(Trigger pipelineTrigger, TimeZone timeZoneId) {
    if (pipelineTrigger.cronExpression == null) {
      throw new InvalidCronExpressionException("null", "cron expression can't be null")
    }

    String cronExpression = CronExpressionFuzzer.fuzz(pipelineTrigger.id, pipelineTrigger.cronExpression)

    try {
      new CronExpression(cronExpression)
    } catch (ParseException e) {
      throw new InvalidCronExpressionException(cronExpression, e.getMessage())
    }

    org.quartz.Trigger trigger = newTrigger()
      .withIdentity(pipelineTrigger.id, PipelineConfigsPollingJob.PIPELINE_TRIGGER_GROUP_PREFIX + pipelineTrigger.parent.id)
      .withSchedule(cronSchedule(cronExpression)
        .inTimeZone(timeZoneId)
        .withMisfireHandlingInstructionDoNothing())
      .usingJobData(new JobDataMap(toParamMap(pipelineTrigger, timeZoneId.getID())))
      .forJob(JOB_ID)
      .build()

    return trigger
  }

  static Pipeline toPipeline(PipelineCache pipelineCache, Map<String, Object> parameters) {
    def existingPipeline = pipelineCache.getPipelinesSync().find { it.id == parameters.id }
    if (!existingPipeline) {
      throw new IllegalStateException("No pipeline found (id: ${parameters.id})")
    }

    def triggerBuilder = Trigger
      .builder()
      .enabled(Boolean.parseBoolean(parameters.triggerEnabled as String))
      .rebake(Boolean.parseBoolean(parameters.triggerRebake as String))
      .id(parameters.triggerId as String)
      .type(Trigger.Type.CRON.toString())
      .eventId(UUID.randomUUID().toString())
      .cronExpression(parameters.triggerCronExpression as String)
      .parameters(parameters.triggerParameters as Map)

    if (parameters.runAsUser) {
      triggerBuilder.runAsUser(parameters.runAsUser as String)
    }

    return existingPipeline.withTrigger(triggerBuilder.build())
  }

  static boolean isInSync(CronTrigger trigger, Trigger pipelineTrigger, TimeZone timeZoneId) {
    CronTrigger other = toQuartzTrigger(pipelineTrigger, timeZoneId) as CronTrigger

    return (trigger.cronExpression == other.cronExpression
      && trigger.timeZone.hasSameRules(other.timeZone)
      && trigger.jobDataMap.getString("runAsUser") == other.jobDataMap.getString("runAsUser")
      && trigger.jobDataMap.getWrappedMap().triggerParameters == other.jobDataMap.getWrappedMap().triggerParameters)
  }
}
