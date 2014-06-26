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
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.workflow.WorkflowBuilder
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
@CompileStatic
class JobStarter {

  @Autowired ApplicationContext applicationContext
  @Autowired JobLauncher launcher
  @Autowired JobBuilderFactory jobs
  private final ObjectMapper mapper = new ObjectMapper()

  void start(String config) {
    launcher.run(buildJobFrom(config), new JobParameters())
  }

  @CompileDynamic
  private Job buildJobFrom(String config) {
    def steps = mapper.readValue(config, new TypeReference<List<Map>>() {})
    def builder = steps.inject(jobs.get("xxx")) { jobBuilder, Map stepConfig ->
      def workflowBuilder = applicationContext.getBean("${stepConfig.type}WorkflowBuilder", WorkflowBuilder)
      workflowBuilder.build(jobBuilder)
    }
    builder.build()
  }
}
