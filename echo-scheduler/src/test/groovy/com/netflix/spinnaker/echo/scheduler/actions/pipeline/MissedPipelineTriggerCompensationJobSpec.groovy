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

import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService
import com.netflix.spinnaker.echo.pipelinetriggers.orca.PipelineInitiator
import com.netflix.spinnaker.echo.scheduler.actions.pipeline.impl.MissedPipelineTriggerCompensationJob
import org.quartz.CronExpression
import org.springframework.boot.actuate.metrics.CounterService
import org.springframework.boot.actuate.metrics.GaugeService
import rx.schedulers.Schedulers
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class MissedPipelineTriggerCompensationJobSpec extends Specification {
  def scheduler = Schedulers.test()
  def pipelineCache = Mock(PipelineCache)
  def orcaService = Mock(OrcaService)
  def pipelineInitiator = Mock(PipelineInitiator)
  def counterService = Stub(CounterService)
  def gaugeService = Mock(GaugeService)

  def 'should trigger pipelines for all missed executions'() {
    given:
    def pipelines = [
      pipelineBuilder('1').disabled(false).triggers([
        new Trigger.TriggerBuilder().id('1').type(Trigger.Type.CRON.toString()).cronExpression('* 0/30 * * * ? *').enabled(true).build(),
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
    def dateContext = new MissedPipelineTriggerCompensationJob.DateContext(
      timeZone: TimeZone.getTimeZone('America/Los_Angeles'),
      triggerWindowFloor: getDateOffset(0),
      now: getDateOffset(50)
    )
    def compensationJob = new MissedPipelineTriggerCompensationJob(scheduler, pipelineCache, orcaService, pipelineInitiator, counterService, gaugeService, 30000, 'America/Los_Angeles', true, dateContext)

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
    1 * pipelineInitiator.call((Pipeline) pipelines[0])
    1 * gaugeService.submit(_, _)
    0 * _
  }

  def 'should no-op with no missed executions'() {
    given:
    def pipelines = [
      pipelineBuilder('1').disabled(false).triggers([
        new Trigger.TriggerBuilder().id('1').type(Trigger.Type.CRON.toString()).cronExpression('* 0/30 * * * ? *').enabled(true).build()
      ]).build(),
      pipelineBuilder('2').disabled(false).triggers([
        new Trigger.TriggerBuilder().id('2').type(Trigger.Type.CRON.toString()).cronExpression('* 0/30 * * * ? *').enabled(true).build()
      ]).build()
    ]

    and:
    def dateContext = new MissedPipelineTriggerCompensationJob.DateContext(
      timeZone: TimeZone.getTimeZone('America/Los_Angeles'),
      triggerWindowFloor: getDateOffset(0),
      now: getDateOffset(0)
    )
    def compensationJob = new MissedPipelineTriggerCompensationJob(scheduler, pipelineCache, orcaService, pipelineInitiator, counterService, gaugeService, 30000, 'America/Los_Angeles', true, dateContext)

    when:
    compensationJob.triggerMissedExecutions(pipelines)

    then:
    1 * orcaService.getLatestPipelineExecutions(_, _) >> {
      [
        new OrcaService.PipelineResponse(pipelineConfigId: '1', startTime: getDateOffset(0).time)
      ]
    }
    0 * pipelineInitiator.call(_)
    1 * gaugeService.submit(_, _)
    0 * _
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
    def result = MissedPipelineTriggerCompensationJob.getEnabledCronTriggers(pipelines)

    then:
    result.collect { it.id } == ['1', '5']
  }

  @Unroll
  def 'should evaluate missed executions only in window'() {
    given:
    def clock = Clock.systemDefaultZone()

    def expr = new CronExpression(cronExpression)
    expr.timeZone = TimeZone.getTimeZone(clock.zone)

    def lastExecution = getDateOffset(lastExecutionMinutes)
    def windowFloor = getDateOffset(windowFloorMinutes)
    def now = getDateOffset(nowMinutes)

    when:
    def result = MissedPipelineTriggerCompensationJob.missedExecution(expr, lastExecution, windowFloor, now)

    then:
    result == missedExecution

    where:
    // windowFloorMinutes is a little weird: This is the time marker the windowFloor is located at.
    // If we had a `now` of 40 and our `windowFloorMs = 1800000` (30m), `windowFloorMinutes` would be `10`.
    cronExpression     | lastExecutionMinutes | windowFloorMinutes | nowMinutes || missedExecution
    '* 0/30 * * * ? *' | 0                    | 0                  | 0          || false  // lastExecution was now and floor is now; no missed execution
    '* 0/30 * * * ? *' | 0                    | 30                 | 90         || false  // lastExecution was 90 minutes ago, floor is at 30m mark; no missed execution
    '* 0/30 * * * ? *' | 30                   | 9                  | 39         || false  // lastExecution was 9 minutes ago, floor is at 9m mark; no missed execution
    '* 0/30 * * * ? *' | 60                   | 30                 | 60         || false  // lastExecution was now, floor 30m mark; no missed execution (maybe redundant case)
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

  Pipeline.PipelineBuilder pipelineBuilder(String id) {
    new Pipeline.PipelineBuilder().id(id).name("pipeline${id}").application('myapp')
  }

  Date getDateOffset(int minutesOffset) {
    def clock = Clock.systemDefaultZone()
    Date.from(Instant.now(clock).truncatedTo(ChronoUnit.HOURS).plusMillis(TimeUnit.MINUTES.toMillis(minutesOffset)))
  }
}
