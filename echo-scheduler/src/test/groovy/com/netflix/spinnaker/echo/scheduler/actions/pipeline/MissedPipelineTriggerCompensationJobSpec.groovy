/*
 * Copyright 2016 Netflix, Inc.
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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import com.netflix.spinnaker.echo.pipelinetriggers.QuietPeriodIndicator
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService
import com.netflix.spinnaker.echo.pipelinetriggers.orca.PipelineInitiator
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.impl.MissedPipelineTriggerCompensationJob
import org.quartz.CronExpression
import rx.schedulers.Schedulers
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class MissedPipelineTriggerCompensationJobSpec extends Specification {
  def scheduler = Schedulers.test()
  def pipelineCache = Mock(PipelineCache)
  def orcaService = Mock(OrcaService)
  def pipelineInitiator = Mock(PipelineInitiator)
  def quietPeriodIndicator = Mock(QuietPeriodIndicator)
  Registry registry = new NoopRegistry()

  def 'should trigger pipelines for all missed executions'() {
    given:
    def theTriggeringTrigger = new Trigger.TriggerBuilder().id('1').type(Trigger.Type.CRON.toString()).cronExpression('* 0/30 * * * ? *').enabled(true).build()
    def pipelines = [
      pipelineBuilder('1').disabled(false).triggers([
        theTriggeringTrigger,
        new Trigger.TriggerBuilder().id('2').type(Trigger.Type.JENKINS.toString()).enabled(true).build()
      ]).build(),
      pipelineBuilder('2').disabled(true).triggers([
        new Trigger.TriggerBuilder().id('3').type(Trigger.Type.CRON.toString()).cronExpression('* 0/30 * * * ? *').enabled(true).build()
      ]).build(),
      pipelineBuilder('3').disabled(false).triggers([
        new Trigger.TriggerBuilder().id('4').type(Trigger.Type.JENKINS.toString()).enabled(true).build()
      ]).build(),
      pipelineBuilder('4').disabled(false).triggers([
        new Trigger.TriggerBuilder().id('5').type(Trigger.Type.CRON.toString()).cronExpression('* 0/30 * * * ? *').enabled(false).build()
      ]).build()
    ]

    and:
    def dateContext = Mock(MissedPipelineTriggerCompensationJob.DateContext)
    dateContext.triggerWindowCeiling() >> getDateOffset(50)
    dateContext.triggerWindowFloor() >> getDateOffset(0)
    dateContext.getClock() >> Clock.system(ZoneId.of('America/Los_Angeles'))

    def compensationJob = new MissedPipelineTriggerCompensationJob(scheduler, pipelineCache, orcaService,
      pipelineInitiator, registry, quietPeriodIndicator, /* not used */ 30000, 2000, /* not used */ 'America/Los_Angeles', true, 900000, 20, dateContext)

    when:
    compensationJob.triggerMissedExecutions(pipelines)

    then:
    1 * orcaService.getLatestPipelineExecutions(_, _) >> {
      [
        new OrcaService.PipelineResponse(pipelineConfigId: '1', startTime: getDateOffset(0).time),
        new OrcaService.PipelineResponse(pipelineConfigId: '2', startTime: getDateOffset(0).time),
        new OrcaService.PipelineResponse(pipelineConfigId: '3', startTime: getDateOffset(0).time),
        new OrcaService.PipelineResponse(pipelineConfigId: '3', startTime: null),
        new OrcaService.PipelineResponse(pipelineConfigId: '4', startTime: getDateOffset(0).time),
        new OrcaService.PipelineResponse(pipelineConfigId: '4', startTime: getDateOffset(30).time)
      ]
    }
    1 * pipelineInitiator.startPipeline((Pipeline) pipelines[0].withTrigger(theTriggeringTrigger))
    0 * orcaService._
    0 * pipelineInitiator._
  }

  def 'should no-op with no missed executions'() {
    given: '2 pipelines that should execute at minute 5'
    def pipelines = [
      pipelineBuilder('1').disabled(false).triggers([
        new Trigger.TriggerBuilder().id('1').type(Trigger.Type.CRON.toString()).cronExpression('* 5 * * * ? *').enabled(true).build()
      ]).build(),
      pipelineBuilder('2').disabled(false).triggers([
        new Trigger.TriggerBuilder().id('2').type(Trigger.Type.CRON.toString()).cronExpression('H 5 * * * ? *').enabled(true).build()
      ]).build()
    ]

    and: 'a window that is scoped to minutes [0, 10]'
    def compensationJob = new MissedPipelineTriggerCompensationJob(scheduler, pipelineCache, orcaService,
      pipelineInitiator, registry, quietPeriodIndicator, 30000L, 2000L,
      'America/Los_Angeles', false, 900000, 20,
      stubDateContext(0, 10))

    when:
    compensationJob.triggerMissedExecutions(pipelines)

    then: 'they are both in window and queried, but have no missed execution'
    1 * orcaService.getLatestPipelineExecutions(['1', '2'], _) >> {
      [
        new OrcaService.PipelineResponse(pipelineConfigId: '1', startTime: getDateOffset(5).time)
        // pipeline 2 has _no_ execution, which is a special case that is not considered a missed execution
      ]
    }
    0 * pipelineInitiator.startPipeline(_)
    0 * _
  }

  @Unroll
  def 'should only look up execution history for pipelines that have an eligible trigger in window [#floorOffset, #ceilingOffset]'() {
    given:
    def dateContext = Mock(MissedPipelineTriggerCompensationJob.DateContext)
    dateContext.triggerWindowFloor() >> getDateOffset(floorOffset)
    dateContext.triggerWindowCeiling() >> getDateOffset(ceilingOffset)
    dateContext.getClock() >> Clock.system(ZoneId.of('America/Los_Angeles'))

    def pipelines = [
      pipelineBuilder('1').disabled(false).triggers([
        new Trigger.TriggerBuilder()
          .id('1')
          .type(Trigger.Type.CRON.toString())
          .cronExpression('* 10 * * * ? *')
          .enabled(true).build()
      ]).build(),
      pipelineBuilder('2').disabled(false).triggers([
        new Trigger.TriggerBuilder()
          .id('2')
          .type(Trigger.Type.CRON.toString())
          .cronExpression('* 20 * * * ? *')
          .enabled(true).build()
      ]).build()
    ]

    and:
    def compensationJob = new MissedPipelineTriggerCompensationJob(scheduler, pipelineCache, orcaService,
      pipelineInitiator, registry, quietPeriodIndicator, 30000L, 2000L,
      'America/Los_Angeles', true, 900000, 20, dateContext)

    when:
    compensationJob.triggerMissedExecutions(pipelines)

    then:
    numCalls * orcaService.getLatestPipelineExecutions(queried, _)
    0 * orcaService.getLatestPipelineExecutions(_, _) // does not have an eligible trigger in window, execution history should not be looked up

    where:
    floorOffset | ceilingOffset || numCalls || queried
    0           | 5             || 0        || []
    0           | 15            || 1        || ['1']
    0           | 25            || 1        || ['1', '2']
    15          | 25            || 1        || ['2']
    25          | 30            || 0        || []
  }

  def 'should use only enabled cron triggers'() {
    given:
    def pipelines = [
      pipelineBuilder('1').triggers([
        new Trigger.TriggerBuilder().id('1').type(Trigger.Type.CRON.toString()).enabled(true).build(),
        new Trigger.TriggerBuilder().id('2').type(Trigger.Type.CRON.toString()).enabled(false).build()
      ]).build(),
      pipelineBuilder('2').build(),
      pipelineBuilder('3').triggers([
        new Trigger.TriggerBuilder().id('3').enabled(true).build(),
        new Trigger.TriggerBuilder().id('4').enabled(false).build(),
        new Trigger.TriggerBuilder().id('5').type(Trigger.Type.CRON.toString()).enabled(true).build()
      ]).build()
    ]

    when:
    def result = MissedPipelineTriggerCompensationJob.getEnabledCronTriggers(pipelines, false)

    then:
    result.collect { it.id } == ['1', '5']
  }

  def 'should respect quiet periods'() {
    given:
    def pipelines = [
      pipelineBuilder('1').triggers([
        new Trigger.TriggerBuilder().id('1').type(Trigger.Type.CRON.toString()).enabled(true).build(),
        new Trigger.TriggerBuilder().id('2').type(Trigger.Type.CRON.toString()).enabled(false).build()
      ]).respectQuietPeriod(true)
      .build(),
      pipelineBuilder('2').build(),
      pipelineBuilder('3').triggers([
        new Trigger.TriggerBuilder().id('3').enabled(true).build(),
        new Trigger.TriggerBuilder().id('4').enabled(false).build(),
        new Trigger.TriggerBuilder().id('5').type(Trigger.Type.CRON.toString()).enabled(true).build()
      ]).build()
    ]

    when:
    def result = MissedPipelineTriggerCompensationJob.getEnabledCronTriggers(pipelines, true)

    then:
    result.collect { it.id } == ['5']
  }


  @Unroll
  def 'should evaluate missed executions only in window'() {
    given:
    def clock = Clock.systemDefaultZone()

    def expr = new CronExpression(cronExpression)
    expr.timeZone = TimeZone.getTimeZone(clock.zone)

    def lastExecution = (lastExecutionMinutes != null) ? getDateOffset(lastExecutionMinutes) : null
    def windowFloor = getDateOffset(windowFloorMinutes)
    def now = getDateOffset(nowMinutes)

    def dateContext = Mock(MissedPipelineTriggerCompensationJob.DateContext)
    def compensationJob = new MissedPipelineTriggerCompensationJob(scheduler, pipelineCache, orcaService,
      pipelineInitiator, registry, quietPeriodIndicator, 30000L, 2000L, 'America/Los_Angeles', true, 900000, 20, dateContext)

    when:
    def result = compensationJob.missedExecution(expr, lastExecution, windowFloor, now)

    then:
    result == missedExecution

    where:
    // windowFloorMinutes is a little weird: This is the time marker the windowFloor is located at.
    // If we had a `triggerWindowCeiling` of 40 and our `windowFloorMs = 1800000` (30m), `windowFloorMinutes` would be `10`.
    cronExpression     | lastExecutionMinutes | windowFloorMinutes | nowMinutes || missedExecution
    '* 0/30 * * * ? *' | 0                    | 0                  | 0          || false  // lastExecution was triggerWindowCeiling and floor is triggerWindowCeiling; no missed execution
    '* 0/30 * * * ? *' | 0                    | 30                 | 90         || true   // lastExecution was 90 minutes ago, floor is at 30m mark; no missed execution
    '* 0/30 * * * ? *' | null                 | 9                  | 39         || true   // trigger in window and no last execution; missed execution!
    '* 0/30 * * * ? *' | 29                   | 9                  | 39         || true   // lastExecution was before trigger time in window; missed execution!
    '* 0/30 * * * ? *' | 30                   | 9                  | 39         || false  // lastExecution lines up with trigger time in window; no missed execution
    '* 0/30 * * * ? *' | 31                   | 9                  | 39         || false  // lastExecution was after trigger time in window; no missed execution
    '* 0/30 * * * ? *' | 60                   | 30                 | 60         || false  // lastExecution was triggerWindowCeiling, floor 30m mark; no missed execution (maybe redundant case)
    '* 0/30 * * * ? *' | 30                   | 0                  | 70         || true   // lastExecution was 40 minutes ago, floor is at 0m mark; missed execution (60m)
  }

  def 'should find pipeline config ids for pipeline executions'() {
    given:
    def triggers = [
      new Trigger.TriggerBuilder().enabled(true).cronExpression('* 0/30 * * * ? *').build(),
      new Trigger.TriggerBuilder().enabled(true).cronExpression('* 0/30 * * * ? *').build()
    ]
    def pipelines = [
      pipelineBuilder('1').disabled(false).build(),
      pipelineBuilder('2').disabled(true).triggers([triggers[0]]).build(),
      pipelineBuilder('3').disabled(false).triggers([triggers[1]]).build()
    ]

    when:
    def configIds = MissedPipelineTriggerCompensationJob.getPipelineConfigIds(pipelines, triggers)

    then:
    configIds == ['3']
  }

  def 'should be able to retrigger daily executions'() {
    def expr = new CronExpression('0 0 10 ? * * *')
    expr.timeZone = TimeZone.getTimeZone('America/Los_Angeles')

    def lastExecutionTs = 1527008402073
    def lastExecution = new Date(lastExecutionTs) // day 1 at 10:00:02
    def now = new Date(lastExecutionTs + TimeUnit.HOURS.toMillis(24) + TimeUnit.MINUTES.toMillis(5)) // day 2 at 10:05:02
    def windowFloor = new Date(now.getTime() - TimeUnit.MINUTES.toMillis(30))   // triggerWindowCeiling - 30m

    def dateContext = Mock(MissedPipelineTriggerCompensationJob.DateContext)
    def compensationJob = new MissedPipelineTriggerCompensationJob(scheduler, pipelineCache, orcaService,
      pipelineInitiator, registry, quietPeriodIndicator, 30000L, 2000L, 'America/Los_Angeles', true, 900000, 20, dateContext)

    when:
    def missedExecution = compensationJob.missedExecution(expr, lastExecution, windowFloor, now)

    then:
    missedExecution == true
  }

  def 'verify that the present is a fleeting moment, the past is no more'() {
    def compensationJob = new MissedPipelineTriggerCompensationJob(scheduler, pipelineCache, orcaService,
      pipelineInitiator, registry, quietPeriodIndicator, 30000L, 2000L, 'America/Los_Angeles', true, 900000, 20)

    def sleepyTimeMs = 100

    when:
    def t1 = compensationJob.dateContext.triggerWindowCeiling().toInstant()
    sleep(sleepyTimeMs)
    def t2 = compensationJob.dateContext.triggerWindowCeiling().toInstant()

    then:
    t2 > t1
    Duration.between(t1, t2).toMillis() >= sleepyTimeMs
  }

  Pipeline.PipelineBuilder pipelineBuilder(String id) {
    new Pipeline.PipelineBuilder().id(id).name("pipeline${id}").application('myapp')
  }

  Date getDateOffset(int minutesOffset) {
    return getDateOffsetMillis(TimeUnit.MINUTES.toMillis(minutesOffset))
  }

  Date getDateOffsetMillis(long millisOffset) {
    def clock = Clock.systemDefaultZone()
    Date.from(Instant.now(clock).truncatedTo(ChronoUnit.HOURS).plusMillis(millisOffset))
  }

  MissedPipelineTriggerCompensationJob.DateContext stubDateContext(windowFloorOffset, windowCeilingOffset) {
    return Stub(MissedPipelineTriggerCompensationJob.DateContext) {
      getClock() >> Clock.system(ZoneId.of('America/Los_Angeles'))
      triggerWindowFloor() >> getDateOffset(windowFloorOffset)
      triggerWindowCeiling() >> getDateOffset(windowCeilingOffset)
    }
  }
}
