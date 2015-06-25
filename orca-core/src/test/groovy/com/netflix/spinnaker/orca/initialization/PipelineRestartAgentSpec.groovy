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

package com.netflix.spinnaker.orca.initialization

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
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.explore.JobExplorer
import rx.schedulers.Schedulers
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.google.common.collect.Sets.newHashSet
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.*
import static com.netflix.spinnaker.orca.ExecutionStatus.CANCELED
import static java.util.concurrent.TimeUnit.SECONDS
import static org.apache.commons.lang.math.JVMRandom.nextLong

class PipelineRestartAgentSpec extends Specification {

  def jobExplorer = Mock(JobExplorer)
  def executionRepository = Mock(ExecutionRepository)
  def jesqueClient = Mock(Client)
  def orcaApplication = new Application("orca")
  def discoveryClient = [getApplication: { it == "orca" ? orcaApplication : null }] as LookupService

  @Shared scheduler = Schedulers.test()

  @Subject pipelineRestarter = new PipelineRestartAgent(new ObjectMapper(), jesqueClient, jobExplorer,
                                                        executionRepository, discoveryClient)

  def setup() {
    pipelineRestarter.scheduler = scheduler // TODO: evil. Make this a constructor param
  }

  def "the agent should look for incomplete jobs and resume them"() {
    given:
    jobExplorer.getJobNames() >> jobNames
    jobNames.eachWithIndex { name, i ->
      jobExplorer.findRunningJobExecutions(name) >> newHashSet(executions[i])
      executionRepository.retrievePipeline("pipeline-$name") >> new Pipeline(id: "pipeline-$name",
                                                                             executingInstance: instanceId)
    }

    and:
    applicationIsUp()

    when:
    advanceTime()

    then:
    jobNames.eachWithIndex { name, i ->
      1 * jesqueClient.enqueue("stalePipeline", { it.args[0].id == "pipeline-$name" })
    }

    where:
    instanceId = "i-06ce57cd"
    jobNames = ["job1", "job2"]
    executions = jobNames.collect { jobExecution(it) }
  }

  @Unroll
  def "if a pipeline is #status it does not get restarted"() {
    given:
    jobExplorer.getJobNames() >> [jobName]
    jobExplorer.findRunningJobExecutions(jobName) >> newHashSet(execution)
    executionRepository.retrievePipeline("pipeline-$jobName") >> pipeline

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
    jobName = "job1"
    execution = jobExecution(jobName)
    pipeline = new Pipeline(id: "pipeline-$jobName", executingInstance: instanceId, canceled: true)
  }

  def "if the executing instance is in discovery it is assumed to still be running the pipeline"() {
    given:
    orcaApplication.addInstance(InstanceInfo.Builder.newBuilder().setAppName("orca").setHostName(instanceId).build())

    and:
    jobExplorer.getJobNames() >> [jobName]
    jobExplorer.findRunningJobExecutions(jobName) >> newHashSet(execution)
    executionRepository.retrievePipeline("pipeline-$jobName") >> new Pipeline(id: "pipeline-$jobName")

    and:
    applicationIsUp()

    when:
    advanceTime()

    then:
    0 * jesqueClient.enqueue(*_)

    where:
    instanceId = "i-06ce57cd"
    jobName = "job1"
    execution = jobExecution(jobName)
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

  def "if a pipeline fails to restart the agent should continue"() {
    given:
    jobExplorer.getJobNames() >> jobNames
    jobNames.eachWithIndex { name, i ->
      jobExplorer.findRunningJobExecutions(name) >> newHashSet(executions[i])
    }
    executionRepository.retrievePipeline("pipeline-${jobNames[0]}") >> {
      throw new RuntimeException("failed to load pipeline")
    }
    executionRepository.retrievePipeline("pipeline-${jobNames[1]}") >> new Pipeline(id: "pipeline-${jobNames[1]}",
                                                                                    executingInstance: instanceId)

    and:
    applicationIsUp()

    when:
    advanceTime()

    then:
    1 * jesqueClient.enqueue("stalePipeline", { it.args[0].id == "pipeline-${jobNames[1]}" })

    where:
    instanceId = "i-06ce57cd"
    jobNames = ["job1", "job2"]
    executions = jobNames.collect { jobExecution(it) }
  }

  private advanceTime() {
    scheduler.advanceTimeBy(pipelineRestarter.pollingInterval, SECONDS)
  }

  private applicationIsUp() {
    pipelineRestarter.onApplicationEvent(statusChangeEvent(STARTING, UP))
  }

  private JobExecution jobExecution(String name) {
    new JobExecution(nextLong(100L),
                     new JobParametersBuilder().addString("pipeline", "pipeline-$name").toJobParameters())
  }

  private static EurekaStatusChangedEvent statusChangeEvent(
    InstanceStatus from,
    InstanceStatus to) {
    new EurekaStatusChangedEvent(new StatusChangeEvent(from, to))
  }
}
