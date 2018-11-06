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
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.services.Front50Service
import com.netflix.spinnaker.echo.test.RetrofitStubs
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import static java.util.concurrent.TimeUnit.SECONDS

class PipelineCacheSpec extends Specification implements RetrofitStubs {
  def front50 = Mock(Front50Service)
  def registry = new NoopRegistry()

  @Shared
  def interval = 30

  @Shared
  def sleepMs = 100

  @Subject
  def pipelineCache = new PipelineCache(Mock(ScheduledExecutorService), interval, sleepMs, front50, registry)

  def "keeps polling if Front50 returns an error"() {
    given:
    def pipeline = Pipeline.builder().application('application').name('Pipeline').id('P1').build()
    def initialLoad = []
    front50.getPipelines() >> initialLoad >> { throw unavailable() } >> [pipeline]
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

  def "we can serialize pipelines with triggers that have a parent"() {
    given:
    ObjectMapper objectMapper = new ObjectMapper()
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
    ObjectMapper objectMapper = new ObjectMapper()
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
}

