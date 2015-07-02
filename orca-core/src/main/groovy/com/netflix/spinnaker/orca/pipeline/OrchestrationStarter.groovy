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

import com.netflix.spinnaker.orca.pipeline.model.Execution
import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class OrchestrationStarter extends ExecutionStarter<Orchestration> {

  @Autowired ExecutionRepository executionRepository
  @Autowired OrchestrationJobBuilder executionJobBuilder

  OrchestrationStarter() {
    super("orchestration")
  }

  @Override
  protected Orchestration create(Map<String, Serializable> config) {
    def orchestration = new Orchestration()
    if (config.containsKey("application")) {
      orchestration.application = config.application
    }
    if (config.containsKey("name")) {
      orchestration.description = config.name
    }
    if (config.containsKey("description")) {
      orchestration.description = config.description
    }
    if (config.appConfig) {
      orchestration.appConfig.putAll(config.appConfig as Map)
    }

    for (context in ((List<Map<String, Object>>) config.stages)) {
      def type = context.remove("type").toString()

      if (context.providerType) {
        type += "_$context.providerType"
      }

      if (executionJobBuilder.isValidStage(type)) {
        def stage = new OrchestrationStage(orchestration, type, context)
        orchestration.stages << stage
      } else {
        throw new NoSuchStageException(type)
      }
    }

    orchestration.buildTime = System.currentTimeMillis()
    orchestration.authentication = Execution.AuthenticationDetails.build().orElse(new Execution.AuthenticationDetails())

    return orchestration
  }

  @Override
  protected void persistExecution(Orchestration orchestration) {
    executionRepository.store(orchestration)
  }

  @Override
  protected JobParameters createJobParameters(Orchestration orchestration) {
    def params = new JobParametersBuilder(super.createJobParameters(orchestration))
    params.addString("description", orchestration.description)
    params.toJobParameters()
  }
}
