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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import groovy.util.logging.Slf4j
import org.quartz.CronTrigger
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.Scheduler
import org.quartz.TriggerKey
import org.quartz.impl.matchers.GroupMatcher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

/**
 * Syncs triggers from pipelines with the triggers in the scheduler
 */
@Slf4j
@Component
@DisallowConcurrentExecution
class PipelineConfigsPollingJob implements Job {
  public static final String PIPELINE_TRIGGER_GROUP_PREFIX = "trigger_"

  private TimeZone timeZoneId
  private Scheduler scheduler
  private Registry registry
  private PipelineCache pipelineCache

  @Autowired
  PipelineConfigsPollingJob(Registry registry, PipelineCache pipelineCache) {
    this.pipelineCache = pipelineCache
    this.registry = registry
  }

  void execute(JobExecutionContext context) {
    long start = System.currentTimeMillis()

    try {
      log.info("Running the pipeline configs polling agent...")

      timeZoneId = TimeZone.getTimeZone(context.mergedJobDataMap.getString("timeZoneId"))
      scheduler = context.scheduler

      // Only interested in pipelines that have enabled CRON triggers
      def pipelinesWithCronTriggers = pipelineCache.getPipelinesSync().findAll { pipeline ->
        !pipeline.disabled && pipeline.triggers
      }

      def pipelineTriggers = new TriggerRepository(pipelinesWithCronTriggers)

      log.debug("Found ${pipelineTriggers.triggers().size()} pipeline CRON triggers that are active")

      removeStaleTriggers(pipelineTriggers)
      updateChangedTriggers(pipelineTriggers)

      registry.gauge("echo.triggers.count").set(pipelineTriggers.triggers().size())
    } catch (Exception e) {
      log.error("Failed to synchronize pipeline triggers", e)
      registry.counter("echo.triggers.sync.error").increment()
    } finally {
      long elapsedMillis = System.currentTimeMillis() - start
      log.info("Done polling for pipeline configs in ${elapsedMillis/1000}s")
      registry.timer("echo.triggers.sync.executionTimeMillis").record(elapsedMillis, TimeUnit.MILLISECONDS)
    }
  }

  /**
   * Remove all triggers no longer needed
   * e.g. triggers that are still registered in our scheduler but no longer found on any pipeline
   * @param pipelineTriggers all active pipeline CRON triggers
   */
  void removeStaleTriggers(TriggerRepository pipelineTriggers) {
    int removeCount = 0
    int failCount = 0

    try {
      Set<TriggerKey> triggerKeys = scheduler.getTriggerKeys(
        GroupMatcher.triggerGroupStartsWith(PIPELINE_TRIGGER_GROUP_PREFIX))

      triggerKeys.each { triggerKey ->
        if (pipelineTriggers.getTrigger(triggerKey.name) == null) {
          try {
            scheduler.unscheduleJob(triggerKey)
            removeCount++
          } catch (Exception e) {
            log.error("Failed to unschedule job with triggerId: ${triggerKey.name}", e)
            failCount++
          }
        }
      }
    }
    catch (Exception e) {
      log.error("Failed during stale trigger removal", e)
    }

    if ((removeCount + failCount) > 0) {
      log.debug("Removed $removeCount stale triggers successfully, $failCount removals failed")
    }

    registry.gauge("echo.triggers.sync.removeCount").set(removeCount)
    registry.gauge("echo.triggers.sync.removeFailCount").set(failCount)
  }

  /**
   * Update triggers: Compare triggers in the system with those of the parsed pipelines
   * create triggers for new pipeline triggers
   * update triggers that have changed
   * @param pipelineTriggers all active pipeline CRON triggers
   */
  void updateChangedTriggers(TriggerRepository pipelineTriggers) {
    int addCount = 0
    int updateCount = 0
    int failCount = 0

    try {
      pipelineTriggers.triggers().each { pipelineTrigger ->
        CronTrigger trigger = scheduler.getTrigger(
          TriggerKey.triggerKey(pipelineTrigger.id, PIPELINE_TRIGGER_GROUP_PREFIX + pipelineTrigger.parent.id)
        ) as CronTrigger

        if (!trigger) {
          if (registerNewTrigger(pipelineTrigger)) {
            addCount++
          } else {
            failCount++
          }
        } else {
          if (!TriggerConverter.isInSync(trigger, pipelineTrigger, timeZoneId)) {
            try {
              def newTrigger = TriggerConverter.toQuartzTrigger(pipelineTrigger, timeZoneId)
              scheduler.rescheduleJob(trigger.key, newTrigger)
              updateCount++
            } catch (Exception e) {
              log.error("Failed to update existing trigger: id: ${pipelineTrigger.id} for pipeline: ${pipelineTrigger.parent.id}", e)
              failCount++
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("Failed to upsert a trigger", e)
    }

    if ((addCount + updateCount + failCount) > 0) {
      log.debug("Added $addCount new triggers, updated $updateCount existing triggers, $failCount failed")
    }

    registry.gauge("echo.triggers.sync.failedUpdateCount").set(failCount)
    registry.gauge("echo.triggers.sync.addCount").set(addCount)
  }

  /**
   * Register a new scheduler trigger given a pipeline trigger
   * @param pipelineTrigger
   */
  boolean registerNewTrigger(Trigger pipelineTrigger) {
    boolean isSuccess = false

    try {
      scheduler.scheduleJob(TriggerConverter.toQuartzTrigger(pipelineTrigger, timeZoneId))
      isSuccess = true
    } catch (InvalidCronExpressionException e) {
      log.error("Failed to create a new trigger: id: ${pipelineTrigger.id} for pipeline: ${pipelineTrigger.parent.application}:${pipelineTrigger.parent.name} (${pipelineTrigger.parent.id}). " +
        "The CRON expression '${pipelineTrigger.cronExpression}' is not valid")
    } catch (Exception e) {
      log.error("Failed to create a new trigger: id: ${pipelineTrigger.id} for pipeline: ${pipelineTrigger.parent.id}. " +
        "The CRON expression '${pipelineTrigger.cronExpression}'", e)
    }

    return isSuccess
  }
}
