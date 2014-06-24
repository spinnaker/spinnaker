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

import groovy.transform.CompileStatic
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.workflow.WorkflowBuilder
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
@CompileStatic
class JobStarter implements ApplicationContextAware {

  private ApplicationContext applicationContext

  @Autowired private JobBuilder jobs

  private final ObjectMapper mapper = new ObjectMapper() // TODO: ensure this is thread-safe

  void start(String config) {
    def steps = mapper.readValue(config, new TypeReference<List<Map>>() {})
    steps.inject(jobs) { JobBuilder jobBuilder, Map stepConfig ->
      def workflowBuilder = applicationContext.getBean("${stepConfig.type}WorkflowBuilder", WorkflowBuilder)
      workflowBuilder.build(jobBuilder)
    }
  }

  @Override
  void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext
  }
}
