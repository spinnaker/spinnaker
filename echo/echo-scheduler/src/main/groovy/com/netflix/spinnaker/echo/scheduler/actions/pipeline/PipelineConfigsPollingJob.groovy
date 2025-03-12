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

import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import groovy.util.logging.Slf4j
import org.quartz.CronTrigger
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.Scheduler
import org.quartz.SchedulerException
import org.quartz.TriggerKey
import org.quartz.impl.matchers.GroupMatcher
import org.quartz.spi.OperableTrigger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

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
  private PipelineConfigPollingMetrics metrics
  private PipelineCache pipelineCache

  @Autowired
  PipelineConfigsPollingJob(PipelineConfigPollingMetrics metrics, PipelineCache pipelineCache) {
    this.pipelineCache = pipelineCache
    this.metrics = metrics
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

      metrics.triggerCount(pipelineTriggers.triggers().size())
    } catch (Exception e) {
      log.error("Failed to synchronize pipeline triggers", e)
      metrics.incrementTriggerSyncError();
    } finally {
      long elapsedMillis = System.currentTimeMillis() - start
      log.info("Done polling for pipeline configs in ${elapsedMillis / 1000}s")
      metrics.recordSyncTime(elapsedMillis)
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

    metrics.removeCount(removeCount)
    metrics.failedRemoveCount(failCount)
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

        if (trigger == null) {
          try {
            if (storeTrigger(pipelineTrigger)) {
              addCount++
            }
          } catch (Exception e) {
            log.error("Failed to create a new trigger: id: ${pipelineTrigger.id} for pipeline: ${pipelineTrigger.parent.id} " +
              "with CRON expression '${pipelineTrigger.cronExpression}'", e)
            failCount++
          }
        } else {
          if (!TriggerConverter.isInSync(trigger, pipelineTrigger, timeZoneId)) {
            try {
              if (storeTrigger(pipelineTrigger, trigger.getKey())) {
                updateCount++
              } else {
                scheduler.unscheduleJob(trigger.getKey())
              }
            } catch (Exception e) {
              log.error("Failed to update an existing trigger: id: ${pipelineTrigger.id} for pipeline: ${pipelineTrigger.parent.id} " +
                "with CRON expression '${pipelineTrigger.cronExpression}'", e)
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

    metrics.failedUpdateCount(failCount)
    metrics.addCount(addCount)
  }

  boolean storeTrigger(Trigger pipelineTrigger) {
    return storeTrigger(pipelineTrigger, null)
  }

  /**
   * Register a new (or re-register existing) scheduler trigger given a pipeline trigger
   * @param pipelineTrigger
   */
  boolean storeTrigger(Trigger pipelineTrigger, TriggerKey triggerKey) {
    org.quartz.Trigger trigger

    try {
      trigger = TriggerConverter.toQuartzTrigger(pipelineTrigger, timeZoneId)
    } catch (InvalidCronExpressionException e) {
      log.warn("Failed to create a new trigger: id: ${pipelineTrigger.id} for pipeline: ${pipelineTrigger.parent.application}:${pipelineTrigger.parent.name} (${pipelineTrigger.parent.id}). " +
        "The CRON expression '${pipelineTrigger.cronExpression}' is not valid", e)

      return false
    }

    // It's possible the user has created a trigger that will never fire (e.g. a trigger in the past)
    // That's ok, just don't even bother creating it
    try {
      boolean willTriggerFire = willTriggerFire(trigger)

      if (!willTriggerFire) {
        throw new SchedulerException("Trigger is in the past")
      }
    } catch (SchedulerException e) {
      log.warn("Failed to create a new trigger: id: ${pipelineTrigger.id} for pipeline: ${pipelineTrigger.parent.application}:${pipelineTrigger.parent.name} (${pipelineTrigger.parent.id}). " +
        "The CRON expression '${pipelineTrigger.cronExpression}' will never fire", e)
      return false
    }

    // Any exceptions in this call should trickle up as they represent a serious issue
    if (triggerKey != null) {
      scheduler.rescheduleJob(triggerKey, trigger)
    } else {
      scheduler.scheduleJob(trigger)
    }
    return true
  }

  /**
   * Check if the trigger is valid/will successfully fire
   * (quartz just throws a bunch of generic {@link SchedulerException} so we can't tell if there was a legitimate failure,
   * or the trigger is just somehow invalid (user error).
   *
   * This method does the same validation that quartz does internally so we can detect bad triggers
   * @param trigger trigger to check
   * @return true if the trigger will fire (according to quartz)
   * @throws SchedulerException
   */
  static boolean willTriggerFire(org.quartz.Trigger trigger) throws SchedulerException {
    OperableTrigger operableTrigger = (OperableTrigger) trigger

    operableTrigger.validate()
    operableTrigger.computeFirstFireTime(null)

    if (operableTrigger.getNextFireTime() == null) {
      return false
    }

    return true
  }
}
