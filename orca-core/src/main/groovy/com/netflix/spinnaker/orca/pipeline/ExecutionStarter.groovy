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

import groovy.transform.CompileStatic
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Autowired

@CompileStatic
abstract class ExecutionStarter<T extends Execution> {

  private final String type

  ExecutionStarter(String type) {
    this.type = type
  }

  @Autowired protected JobLauncher launcher
  @Autowired protected JobOperator jobOperator
  @Autowired protected JobRepository jobRepository
  @Autowired protected ObjectMapper mapper

  T start(String configJson) {
    Map<String, Serializable> config = mapper.readValue(configJson, Map)
    def subject = create(config)
    persistExecution(subject)
    def job = executionJobBuilder.build(config, subject)
    persistExecution(subject)
    launcher.run job, createJobParameters(subject)
    subject
  }

  void resume(T subject) {
    def jobName = executionJobBuilder.jobNameFor(subject)
    def execution = jobRepository.getLastJobExecution(jobName, createJobParameters(subject))
    jobOperator.restart(execution.id)
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
}
