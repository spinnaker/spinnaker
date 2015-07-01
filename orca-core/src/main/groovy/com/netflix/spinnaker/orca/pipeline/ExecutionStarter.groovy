/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.*
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
@CompileStatic
abstract class ExecutionStarter<T extends Execution> {

  private final String type

  ExecutionStarter(String type) {
    this.type = type
  }

  @Autowired
  protected JobLauncher launcher
  @Autowired
  protected JobOperator jobOperator
  @Autowired
  protected JobRepository jobRepository
  @Autowired
  protected ObjectMapper mapper
  @Autowired(required = false)
  protected PipelineStartTracker startTracker

  @Autowired(required = false)
  List<JobExecutionListener> pipelineListeners

  T start(String configJson) {
    boolean startImmediately = true
    Map<String, Serializable> config = mapper.readValue(configJson, Map)
    def subject = create(config)
    persistExecution(subject)

    if (startTracker && subject instanceof Pipeline) {
      def pipeline = (Pipeline) subject

      if (startTracker &&
          pipeline.pipelineConfigId &&
          (pipeline.limitConcurrent == true) &&
          startTracker.queueIfNotStarted(pipeline.pipelineConfigId, subject.id)){
        log.info "Queueing: $subject.id"
        startImmediately = false
      }
    }

    if (startImmediately) {
      subject = startExecution(subject)
    }

    subject
  }

  T startExecution(T subject) {
    def job = executionJobBuilder.build(subject)
    persistExecution(subject)
    if (subject.status.isComplete()) {
      log.warn(
        "Unable to start execution that has previously been completed (${subject.class.simpleName}:${subject.id}:${subject.status})")
      if (subject instanceof Pipeline) {
        pipelineListeners?.each {
          it.afterJob(new JobExecution(0L, new JobParameters([pipeline: new JobParameter(subject.id)])))
        }
      }
      return subject
    }
    log.info "Starting $subject.id"
    launcher.run job, createJobParameters(subject)
    if (startTracker && subject instanceof Pipeline) {
      startTracker.addToStarted(((Pipeline) subject).pipelineConfigId, subject.id)
    }
    subject
  }

  void resume(T subject) {
    log.warn "Resuming $subject.id"
    def jobName = executionJobBuilder.jobNameFor(subject)
    def execution = jobRepository.getLastJobExecution(jobName, createJobParameters(subject))
    if (execution) {
      resetExecution(execution)
      jobOperator.restart(execution.id)
    } else {
      throw new IllegalStateException("Could not find a previous JobExecution for pipeline $jobName")
    }
  }

  protected abstract ExecutionJobBuilder<T> getExecutionJobBuilder()

  protected abstract void persistExecution(T subject)

  protected abstract T create(Map<String, Serializable> config)

  protected JobParameters createJobParameters(T subject) {
    def params = new JobParametersBuilder()
    params.addString(type, subject.id)
    params.addString("application", subject.application)
    params.toJobParameters()
  }

  /**
   * Because "restartability" of a Spring Batch job relies on it having been cleanly stopped and we can't guarantee
   * that we need to update the job to a STOPPED state.
   */
  private void resetExecution(JobExecution execution) {
    if (execution.status != BatchStatus.STOPPED) {
      execution.setExitStatus(ExitStatus.STOPPED.addExitDescription("restarted after instance shutdown"))
      execution.setStatus(BatchStatus.STOPPED)
      execution.setEndTime(new Date())
      jobRepository.update(execution)
    }
  }
}
