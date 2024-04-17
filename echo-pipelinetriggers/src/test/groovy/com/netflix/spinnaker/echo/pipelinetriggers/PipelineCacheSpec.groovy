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


package com.netflix.spinnaker.echo.pipelinetriggers

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.BaseTriggerEventHandler
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService
import com.netflix.spinnaker.echo.services.Front50Service
import com.netflix.spinnaker.echo.test.RetrofitStubs
import spock.lang.Unroll
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.ScheduledExecutorService

class PipelineCacheSpec extends Specification implements RetrofitStubs {
  def front50 = Mock(Front50Service)
  def orca = Mock(OrcaService)
  def registry = new NoopRegistry()
  def objectMapper = EchoObjectMapper.getInstance()
  def pipelineCacheConfigurationProperties = new PipelineCacheConfigurationProperties()

  /**
   * To verify that PipelineCache passes the expected supportedTriggers
   * to front50's getPipelines endpoint.
   */
  def supportedTrigger = "arbitrary"
  def baseTriggerEventHandler = Mock(BaseTriggerEventHandler) {
    supportedTriggerTypes() >> List.of(supportedTrigger)
  }
  def supportedTriggers = "${supportedTrigger},cron"
  List<BaseTriggerEventHandler> triggerHandlers = List.of(baseTriggerEventHandler)

  @Shared
  def interval = 30

  @Shared
  def sleepMs = 100

  @Subject
  def pipelineCache = new PipelineCache(Mock(ScheduledExecutorService), interval, sleepMs, pipelineCacheConfigurationProperties, objectMapper, front50, orca, registry, triggerHandlers)

  def "keeps polling if Front50 returns an error"() {
    given:
    def pipelineMap = [
      application: 'application',
      name       : 'Pipeline',
      id         : 'P1'
    ]
    def pipeline = Pipeline.builder().application('application').name('Pipeline').id('P1').build()

    def initialLoad = []
    front50.getPipelines(true, true, supportedTriggers) >> initialLoad >> { throw unavailable() } >> [pipelineMap]
    pipelineCache.start()

    expect: 'null pipelines when we have not polled yet'
    pipelineCache.getPipelines() == null

    when: 'we complete our first polling cycle'
    pipelineCache.pollPipelineConfigs()

    then: 'we reflect the initial value'
    pipelineCache.getPipelines() == initialLoad

    when: 'a polling cycle encounters an error'
    pipelineCache.pollPipelineConfigs()

    then: 'we still return the cached value'
    pipelineCache.getPipelines() == initialLoad

    when: 'we recover after a failed poll'
    pipelineCache.pollPipelineConfigs()

    then: 'we return the updated value'
    pipelineCache.getPipelines() == [pipeline]
  }

  @Unroll
  def "filters front50 pipelines when configured to do so (#filterFront50Pipelines)"() {
    given:
    pipelineCacheConfigurationProperties.filterFront50Pipelines = filterFront50Pipelines

    when:
    pipelineCache.start()
    pipelineCache.pollPipelineConfigs()

    then:
    if (filterFront50Pipelines) {
      1 * front50.getPipelines(true, true, supportedTriggers) >> [] // arbitrary return value
    } else {
      1 * front50.getPipelines() >> [] // arbitrary return value
    }
    0 * front50._

    where:
    filterFront50Pipelines << [true, false]
  }

  def "getPipelineById calls front50's getPipeline endpoint"() {
    given:
    def pipelineId = "my-pipeline-id"
    def application = "application"
    def pipelineName = "my-pipeline-name"
    def pipelineMap = [
      application: application,
      name       : pipelineName,
      id         : pipelineId
    ]
    def pipeline = Pipeline.builder().application(application).name(pipelineName).id(pipelineId).build()

    when:
    Optional<Pipeline> result = pipelineCache.getPipelineById(pipelineId)

    then:
    1 * front50.getPipeline(pipelineId) >> pipelineMap
    0 * front50._

    assert result.isPresent()
    result.get() == pipeline
  }

  def "getPipelineByName calls front50's getPipelineByName endpoint"() {
    given:
    def pipelineId = "my-pipeline-id"
    def application = "application"
    def pipelineName = "my-pipeline-name"
    def pipelineMap = [
      application: application,
      name       : pipelineName,
      id         : pipelineId
    ]
    def pipeline = Pipeline.builder().application(application).name(pipelineName).id(pipelineId).build()

    when:
    Optional<Pipeline> result = pipelineCache.getPipelineByName(application, pipelineName)

    then:
    1 * front50.getPipelineByName(application, pipelineName) >> pipelineMap
    0 * front50._

    assert result.isPresent()
    result.get() == pipeline
  }

  def "we can serialize pipelines with triggers that have a parent"() {
    given:
    ObjectMapper objectMapper = EchoObjectMapper.getInstance()
    Trigger trigger = Trigger.builder().id('123-456').build()
    Pipeline pipeline = Pipeline.builder().application('app').name('pipe').id('idPipe').triggers([trigger]).build()
    Pipeline decorated = PipelineCache.decorateTriggers([pipeline])[0]

    expect:
    decorated.triggers[0].parent == decorated

    when:
    objectMapper.writeValueAsString(decorated)

    then:
    notThrown(JsonMappingException)
  }

  def "can handle pipelines without triggers"() {
    given:
    ObjectMapper objectMapper = EchoObjectMapper.getInstance()
    Trigger trigger = Trigger.builder().id('123-456').build()
    Pipeline pipeline = Pipeline.builder().application('app').name('pipe').id('idPipe').triggers([]).build()
    Pipeline decorated = PipelineCache.decorateTriggers([pipeline])[0]

    expect:
    decorated.triggers.isEmpty()

    when:
    objectMapper.writeValueAsString(decorated)

    then:
    notThrown(JsonMappingException)
  }

  def "disabled triggers and triggers for disabled pipelines do not appear in trigger index"() {
    given:
    Trigger enabledTrigger = Trigger.builder().type('git').enabled(true).build()
    Trigger disabledTrigger = Trigger.builder().type('jenkins').enabled(false).build()

    def enabledPipeline = Pipeline.builder().application('app').name('pipe').id('enabledPipeId').disabled(false)
      .triggers([enabledTrigger, disabledTrigger]).build()
    def disabledPipeline = Pipeline.builder().application('app').name('pipe').id('disabledPipedId').disabled(true)
      .triggers([enabledTrigger]).build()

    def pipelines = [enabledPipeline, disabledPipeline]

    when:
    def triggers = PipelineCache.extractEnabledTriggersFrom(PipelineCache.decorateTriggers(pipelines))

    then: 'we only get the enabled trigger for the enabled pipeline'
    triggers.size() == 1
    triggers.get('git').size() == 1
    triggers.get('git').first().parent.id == 'enabledPipeId'
  }

  def "trigger indexing supports pipelines with null triggers"() {
    given:
    def pipeline = Pipeline.builder().application('app').name('pipe').triggers(null).build()

    when:
    def triggers = PipelineCache.extractEnabledTriggersFrom(PipelineCache.decorateTriggers([pipeline]))

    then:
    triggers.isEmpty()
  }

  def "trigger indexing supports triggers with null type"() {
    given:
    def pipeline = Pipeline.builder().application('app').name('pipe').triggers(
      [Trigger.builder().type(null).enabled(true).build()]
    ).build()

    when:
    def triggers = PipelineCache.extractEnabledTriggersFrom(PipelineCache.decorateTriggers([pipeline]))

    then:
    triggers.isEmpty()
  }
}
