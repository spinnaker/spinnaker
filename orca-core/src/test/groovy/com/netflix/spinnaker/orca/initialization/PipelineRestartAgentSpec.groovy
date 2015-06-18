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

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.appinfo.InstanceInfo.InstanceStatus
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spinnaker.kork.eureka.EurekaStatusChangedEvent
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import net.greghaines.jesque.client.Client
import org.springframework.batch.core.*
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.repository.JobRepository
import rx.schedulers.Schedulers
import spock.lang.*
import static com.google.common.collect.Sets.newHashSet
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.*
import static java.time.temporal.ChronoUnit.MINUTES
import static java.util.concurrent.TimeUnit.SECONDS
import static org.apache.commons.lang.math.JVMRandom.nextLong

class PipelineRestartAgentSpec extends Specification {

  def jobExplorer = Mock(JobExplorer)
  def executionRepository = Mock(ExecutionRepository)
  def jobRepository = Mock(JobRepository)
  def jesqueClient = Mock(Client)

  @Shared scheduler = Schedulers.test()
  @Shared clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
  @Shared minInactivity = Duration.of(2, MINUTES)

  @Subject pipelineRestarter = new PipelineRestartAgent(new ObjectMapper(), jesqueClient, clock, minInactivity,
                                                        jobRepository, jobExplorer, executionRepository)

  def setup() {
    pipelineRestarter.scheduler = scheduler // TODO: evil. Make this a constructor param
  }

  def "the agent should look for incomplete jobs and resume them"() {
    given:
    jobExplorer.getJobNames() >> jobNames
    jobNames.eachWithIndex { name, i ->
      jobExplorer.findRunningJobExecutions(name) >> newHashSet(executions[i])
      executionRepository.retrievePipeline("pipeline-$name") >> new Pipeline(id: "pipeline-$name")
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
    jobNames = ["job1", "job2"]
    executions = jobNames.collect { staleJobExecution(it) }
  }

  @Ignore
  def "the job execution related to the pipeline should get updated so it can restart cleanly"() {
    given:
    jobExplorer.getJobNames() >> [jobName]
    jobExplorer.findRunningJobExecutions(jobName) >> newHashSet(execution)
    executionRepository.retrievePipeline("pipeline-$jobName") >> new Pipeline(id: "pipeline-$jobName")

    and:
    applicationIsUp()

    when:
    advanceTime()

    then:
    1 * jobRepository.update({
      it.status == BatchStatus.STOPPED && it.exitStatus.exitCode == ExitStatus.STOPPED.exitCode && it.endTime != null
    })

    then:
    1 * jesqueClient.enqueue("stalePipeline", { it.args[0].id == "pipeline-$jobName" })

    where:
    jobName = "job1"
    execution = staleJobExecution(jobName)
  }

  @Unroll
  def "if any step in the job has been updated within #stepsUpdatedAt it is assumed to be running on another instance"() {
    given:
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
    stepsUpdatedAt                                          | _
    [clock.instant().minus(minInactivity), clock.instant()] | _
    [clock.instant(), clock.instant().minus(minInactivity)] | _

    n = Duration.of(2, MINUTES)
    jobName = "job1"
    execution = jobExecutionWithActivity(jobName, *stepsUpdatedAt)
  }

  @Unroll
  def "if the application changes state from #from to #to the restarter doesn't attempt to do anything"() {
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
    executionRepository.retrievePipeline("pipeline-${jobNames[1]}") >> new Pipeline(id: "pipeline-${jobNames[1]}")

    and:
    applicationIsUp()

    when:
    advanceTime()

    then:
    1 * jesqueClient.enqueue("stalePipeline", { it.args[0].id == "pipeline-${jobNames[1]}" })

    where:
    jobNames = ["job1", "job2"]
    executions = jobNames.collect { staleJobExecution(it) }
  }

  private advanceTime() {
    scheduler.advanceTimeBy(pipelineRestarter.pollingInterval, SECONDS)
  }

  private applicationIsUp() {
    pipelineRestarter.onApplicationEvent(statusChangeEvent(STARTING, UP))
  }

  private JobExecution jobExecutionWithActivity(String name, Instant... updatedAt) {
    def execution = new JobExecution(nextLong(100L),
                                     new JobParametersBuilder().addString("pipeline",
                                                                          "pipeline-$name").toJobParameters())
    updatedAt.each {
      addStepExecutions(execution, it)
    }
    execution
  }

  private JobExecution staleJobExecution(String name) {
    jobExecutionWithActivity(name, clock.instant().minus(minInactivity))
  }

  private addStepExecutions(JobExecution execution, Instant... updatedAt) {
    execution.addStepExecutions updatedAt.collect {
      def step = new StepExecution("foo", execution, nextLong(100L))
      step.setLastUpdated(new Date(it.toEpochMilli()))
      step
    }
  }

  private static EurekaStatusChangedEvent statusChangeEvent(
    InstanceStatus from,
    InstanceStatus to) {
    new EurekaStatusChangedEvent(new StatusChangeEvent(from, to))
  }
}
