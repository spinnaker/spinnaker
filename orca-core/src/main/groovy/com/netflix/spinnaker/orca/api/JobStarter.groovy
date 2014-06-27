/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.api

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Maps
import com.netflix.spinnaker.orca.workflow.WorkflowBuilder
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.job.builder.FlowJobBuilder
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.builder.JobBuilderHelper
import org.springframework.batch.core.job.builder.SimpleJobBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
@CompileStatic
class JobStarter {

  @Autowired private ApplicationContext applicationContext
  @Autowired private JobLauncher launcher
  @Autowired private JobBuilderFactory jobs
  @Autowired private ObjectMapper mapper

  JobExecution start(String config) {
    def context = buildJobFrom(config)
    launcher.run(context.job, context.parameters)
  }

  private WorkflowContext buildJobFrom(String config) {
    def steps = mapper.readValue(config, new TypeReference<List<Map>>() {})
    def context = new WorkflowContext(jobs.get("xxx"))
    (WorkflowContext) steps.inject(context, this.&foo)
  }

  @CompileDynamic
  private WorkflowContext foo(WorkflowContext context, Map stepConfig) {
    def workflowBuilder = applicationContext.getBean("${stepConfig.type}WorkflowBuilder", WorkflowBuilder)
    context.withStep(workflowBuilder).withParameters(stepConfig)
  }
}

@CompileStatic
class WorkflowContext {
  private JobBuilderHelper jobBuilder
  private JobParametersBuilder parametersBuilder = new JobParametersBuilder()

  WorkflowContext(JobBuilder jobBuilder) {
    this.jobBuilder = jobBuilder
  }

  @CompileDynamic
  @TypeChecked
  WorkflowContext withStep(WorkflowBuilder workflowBuilder) {
    jobBuilder = workflowBuilder.build(jobBuilder)
    return this
  }

  @CompileDynamic
  WorkflowContext withParameters(Map<String, ?> config) {
    Maps.filterKeys(config) {
      it != "type"
    } each {
      parametersBuilder.addParameter(it.key, new JobParameter(it.value))
    }
    return this
  }

  Job getJob() {
    // Have to do some horror here as we don't know what type of builder we'll end up with.
    // Two of them have a build method that returns a Job but it's not on a common superclass.
    // If we end up with a plain JobBuilder it implies no steps or flows got added above which I guess is an error.
    switch (jobBuilder) {
      case SimpleJobBuilder:
        return (jobBuilder as SimpleJobBuilder).build()
      case FlowJobBuilder:
        return (jobBuilder as FlowJobBuilder).build()
      default:
        // (╯°□°)╯︵ ┻━┻
        throw new IllegalStateException("Cannot build a Job using a ${jobBuilder.getClass()} - did you add any steps to it?")
    }
  }

  JobParameters getParameters() {
    parametersBuilder.toJobParameters()
  }
}