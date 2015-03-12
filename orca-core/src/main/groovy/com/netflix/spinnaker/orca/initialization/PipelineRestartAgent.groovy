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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.kork.eureka.EurekaStatusChangedEvent
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UP

@Component
@Slf4j
@CompileStatic
class PipelineRestartAgent implements ApplicationListener<EurekaStatusChangedEvent> {

  private final JobExplorer jobExplorer
  private final ExecutionRepository executionRepository
  private final PipelineStarter pipelineStarter

  PipelineRestartAgent(JobExplorer jobExplorer, ExecutionRepository executionRepository, PipelineStarter pipelineStarter) {
    this.jobExplorer = jobExplorer
    this.executionRepository = executionRepository
    this.pipelineStarter = pipelineStarter
  }

  @Override
  void onApplicationEvent(EurekaStatusChangedEvent event) {
    if (event.statusChangeEvent.status == UP) {
      log.info "Application is UP... checking for incomplete pipelines"
      jobExplorer.getJobNames().each { jobName ->
        def executions = jobExplorer.findRunningJobExecutions(jobName)
        executionsToPipelines(executions).each { pipeline ->
          log.warn "Resuming execution of pipeline $pipeline.application:$pipeline.name"
          pipelineStarter.resume pipeline
        }
      }
    }
  }

  private Collection<Pipeline> executionsToPipelines(Collection<JobExecution> executions) {
    def ids = executions*.getJobParameters()*.getString("pipeline")
    ids.collect { id ->
      executionRepository.retrievePipeline(id)
    }
  }
}
