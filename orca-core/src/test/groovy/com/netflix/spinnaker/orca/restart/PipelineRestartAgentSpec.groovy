/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.restart

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.appinfo.InstanceInfo
import com.netflix.appinfo.InstanceInfo.InstanceStatus
import com.netflix.discovery.StatusChangeEvent
import com.netflix.discovery.shared.Application
import com.netflix.discovery.shared.LookupService
import com.netflix.spinnaker.kork.eureka.EurekaStatusChangedEvent
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import net.greghaines.jesque.client.Client
import rx.Observable
import rx.schedulers.Schedulers
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.*
import static com.netflix.spinnaker.orca.ExecutionStatus.CANCELED
import static java.util.concurrent.TimeUnit.SECONDS

class PipelineRestartAgentSpec extends Specification {

  def executionRepository = Mock(ExecutionRepository)
  def jesqueClient = Mock(Client)
  def orcaApplication = new Application("orca")
  def discoveryClient = [getApplication: { it == "orca" ? orcaApplication : null }] as LookupService
  @Shared def currentInstance = InstanceInfo.Builder.newBuilder().setAppName("orca").setHostName("localhost").build()
  @Shared scheduler = Schedulers.test()

  @Subject
    pipelineRestarter = new PipelineRestartAgent(new ObjectMapper(), jesqueClient, executionRepository, discoveryClient,
                                                 currentInstance)

  def setup() {
    pipelineRestarter.scheduler = scheduler // TODO: evil. Make this a constructor param
  }

  def "the agent should look for incomplete pipelines and resume them"() {
    given:
    executionRepository.retrievePipelines() >> Observable.from(pipelines)

    and:
    applicationIsUp()

    when:
    advanceTime()

    then:
    pipelines.eachWithIndex { pipeline, i ->
      1 * jesqueClient.enqueue("stalePipeline", { it.args[0].id == pipeline.id })
    }

    where:
    instanceId = "i-06ce57cd"
    pipelines = ["pipeline1", "pipeline2"].collect {
      new Pipeline(id: it, executingInstance: instanceId)
    }
  }

  @Unroll
  def "if a pipeline is #status it does not get restarted"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline)

    and:
    applicationIsUp()

    expect:
    pipeline.status == status

    when:
    advanceTime()

    then:
    0 * jesqueClient.enqueue(*_)

    where:
    status = CANCELED
    instanceId = "i-06ce57cd"
    pipeline = new Pipeline(id: "pipeline1", executingInstance: instanceId, canceled: true)
  }

  def "if the executing instance is in discovery it is assumed to still be running the pipeline"() {
    given:
    orcaApplication.addInstance(InstanceInfo.Builder.newBuilder().setAppName("orca").setHostName(instanceId).build())

    and:
    executionRepository.retrievePipelines() >> Observable.just(pipeline)

    and:
    applicationIsUp()

    when:
    advanceTime()

    then:
    0 * jesqueClient.enqueue(*_)

    where:
    instanceId = "i-06ce57cd"
    pipeline = new Pipeline(id: "pipeline1", executingInstance: instanceId)
  }

  def "if the executing instance is the current instance it is assumed to already be running the pipeline"() {
    given:
    executionRepository.retrievePipelines() >> Observable.just(pipeline)

    and:
    applicationIsUp()

    when:
    advanceTime()

    then:
    0 * jesqueClient.enqueue(*_)

    where:
    instanceId = currentInstance.id
    pipeline = new Pipeline(id: "pipeline1", executingInstance: instanceId)
  }

  @Unroll
  def "if the application changes state from UP to #to the restarter doesn't attempt to do anything"() {
    given:
    applicationIsUp()

    when:
    pipelineRestarter.onApplicationEvent(statusChangeEvent(UP, to))
    advanceTime()

    then:
    0 * _

    where:
    to << [OUT_OF_SERVICE, DOWN]
  }

  private advanceTime() {
    scheduler.advanceTimeBy(pipelineRestarter.pollingInterval, SECONDS)
  }

  private applicationIsUp() {
    pipelineRestarter.onApplicationEvent(statusChangeEvent(STARTING, UP))
  }

  private static EurekaStatusChangedEvent statusChangeEvent(
    InstanceStatus from,
    InstanceStatus to) {
    new EurekaStatusChangedEvent(new StatusChangeEvent(from, to))
  }
}
