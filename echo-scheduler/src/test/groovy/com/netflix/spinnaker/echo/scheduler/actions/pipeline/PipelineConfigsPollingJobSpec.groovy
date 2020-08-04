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
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import org.quartz.CronTrigger
import org.quartz.JobDataMap
import org.quartz.JobExecutionContext
import org.quartz.Scheduler
import org.quartz.TriggerKey
import org.quartz.impl.triggers.CronTriggerImpl
import spock.lang.Specification
import spock.lang.Subject

class PipelineConfigsPollingJobSpec extends Specification {

  PipelineConfigPollingMetrics metrics = Mock(PipelineConfigPollingMetrics)
  def scheduler = Mock(Scheduler)
  def pipelineCache = Mock(PipelineCache)
  def pollingJobContext = Mock(JobExecutionContext)
  def jobDataMap = new JobDataMap()

  @Subject
    pollingAgent = new PipelineConfigsPollingJob(metrics, pipelineCache)

  void setup() {
    jobDataMap.put("timeZoneId", "America/Los_Angeles")
    pollingJobContext.scheduler >> scheduler
    pollingJobContext.mergedJobDataMap >> jobDataMap
  }

  void 'when a new pipeline trigger is added, a scheduled action instance is registered with an id same as the trigger id'() {
    given:
    Trigger trigger = Trigger.builder()
      .enabled(true)
      .type('cron')
      .cronExpression('* 0/30 * * * ? *')
      .build()
    Pipeline pipeline = buildPipeline([trigger])

    List<Pipeline> pipelines = PipelineCache.decorateTriggers([pipeline])
    pipelineCache.getPipelinesSync() >> pipelines

    when:
    pollingAgent.execute(pollingJobContext)

    then:
    1 * scheduler.scheduleJob(_ as CronTrigger) >> { args ->
      assert (args[0] as CronTrigger).key.name == pipelines[0].triggers[0].id
    }
    0 * scheduler.unscheduleJob(_)
  }

  void 'when an existing pipeline trigger is removed, corresponding scheduled action is also removed'() {
    given:
    Pipeline pipeline = buildPipeline([])
    pipelineCache.getPipelinesSync() >> PipelineCache.decorateTriggers([pipeline])
    CronTrigger trigger1 = makeTrigger("1", "America/New_York", true)

    scheduler.getTriggerKeys(_) >> [trigger1.key]

    when:
    pollingAgent.execute(pollingJobContext)

    then:
    1 * scheduler.unscheduleJob(_ as TriggerKey) >> { args ->
      assert (args[0] as TriggerKey) == trigger1.key
    }
    0 * scheduler.scheduleJob(_)
  }

  void 'invalid new triggers are not causing errors'() {
    given:
    Trigger trigger = Trigger.builder()
      .enabled(true)
      .type('cron')
      .cronExpression('0 0/5 13-14 2 8 ? 1995')
      .build()
    Pipeline pipeline = buildPipeline([trigger])

    List<Pipeline> pipelines = PipelineCache.decorateTriggers([pipeline])
    pipelineCache.getPipelinesSync() >> pipelines

    when: 'creating a new trigger'
    pollingAgent.execute(pollingJobContext)

    then: 'the bad trigger is ignored'
    noExceptionThrown()
    0 * scheduler.scheduleJob(_)
    1 * metrics.addCount(0)
    1 * metrics.failedUpdateCount(0)
  }

  void 'invalid updates to existing triggers are not causing errors'() {
    given:
    Trigger triggerInvalid = Trigger.builder()
      .enabled(true)
      .type('cron')
      .cronExpression('0 0/5 13-14 2 8 ? 1995')
      .build()
    Pipeline pipelineWithInvalidTrigger = buildPipeline([triggerInvalid])
    List<Pipeline> pipelines2 = PipelineCache.decorateTriggers([pipelineWithInvalidTrigger])

    when: 'updating with an invalid trigger'
    pipelineCache.getPipelinesSync() >> pipelines2
    scheduler.getTrigger(_) >> makeTrigger("1", "America/New_York", true)
    pollingAgent.execute(pollingJobContext)

    then: 'the bad trigger is ignored and original trigger is removed'
    noExceptionThrown()
    0 * scheduler.scheduleJob(_)
    1 * scheduler.unscheduleJob(_)
    1 * metrics.addCount(0)
    1 * metrics.failedUpdateCount(0)
  }

  private static Pipeline buildPipeline(List<Trigger> triggers) {
    Pipeline
      .builder()
      .application('api')
      .name('Pipeline 1')
      .id('p1')
      .parallel(true)
      .triggers(triggers)
      .build()
  }

  private static CronTrigger makeTrigger(String id, String timezone, boolean rebake) {
    return makeTrigger(id, timezone, rebake, id + " 10 0/12 1/1 * ? *")
  }

  private static CronTrigger makeTrigger(String id, String timezone, boolean rebake, String cronExpression) {
    def trigger = new CronTriggerImpl(
      "key" + id, PipelineConfigsPollingJob.PIPELINE_TRIGGER_GROUP_PREFIX, "job", "job",
      cronExpression)

    trigger.jobDataMap.put("application", "app" + id)
    trigger.jobDataMap.put("id", "id" + id)
    trigger.jobDataMap.put("runAsUser", "runAsUser" + id)
    trigger.jobDataMap.put("timeZone", timezone)
    trigger.jobDataMap.put("triggerRebake", rebake)

    return trigger
  }
}
