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

import com.netflix.discovery.StatusChangeEvent
import com.netflix.spinnaker.kork.eureka.EurekaStatusChangedEvent
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.explore.JobExplorer
import spock.lang.Specification
import spock.lang.Subject
import static com.google.common.collect.Sets.newHashSet
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.STARTING
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import static org.apache.commons.lang.math.JVMRandom.nextLong

class PipelineRestarterSpec extends Specification {

  def jobExplorer = Mock(JobExplorer)
  def pipelineStarter = Mock(PipelineStarter)
  def executionRepository = Mock(ExecutionRepository)
  @Subject
    pipelineRestarter = new PipelineRestarter(jobExplorer, executionRepository, pipelineStarter)

  def "when the application comes up the restarter should look for incomplete jobs and resume them"() {
    when:
    pipelineRestarter.onApplicationEvent(new EurekaStatusChangedEvent(new StatusChangeEvent(STARTING, UP)))

    then:
    1 * jobExplorer.getJobNames() >> jobNames
    jobNames.eachWithIndex { name, i ->
      1 * jobExplorer.findRunningJobExecutions(name) >> newHashSet(executions[i])
      1 * executionRepository.retrievePipeline("pipeline-$name") >> new Pipeline(id: "pipeline-$name")
      1 * pipelineStarter.resume({ it.id == "pipeline-$name" })
    }

    where:
    jobNames = ["job1", "job2"]
    executions = jobNames.collect {
      new JobExecution(nextLong(100L), new JobParametersBuilder().addString("pipeline", "pipeline-$it").toJobParameters())
    }
  }
}
