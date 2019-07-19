/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.job

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.config.PreconfiguredJobStageProperties
import com.netflix.spinnaker.orca.clouddriver.exception.PreconfiguredJobNotFoundException
import com.netflix.spinnaker.orca.clouddriver.service.JobService
import com.netflix.spinnaker.orca.clouddriver.tasks.job.DestroyJobTask
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class PreconfiguredJobStage extends RunJobStage {

  private JobService jobService
  private ObjectMapper objectMapper

  @Autowired
  public PreconfiguredJobStage(DestroyJobTask destroyJobTask, Optional<JobService> optionalJobService) {
    super(destroyJobTask)
    this.jobService = optionalJobService.orElse(null)
    this.objectMapper = new ObjectMapper()
  }

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    def preconfiguredJob = jobService.getPreconfiguredStages().find { stage.type == it.type }

    if (!preconfiguredJob) {
      throw new PreconfiguredJobNotFoundException((String) stage.type)
    }

    stage.setContext(overrideIfNotSetInContextAndOverrideDefault(stage.context, preconfiguredJob, stage.execution.application))
    super.taskGraph(stage, builder)
  }

  private Map<String, Object> overrideIfNotSetInContextAndOverrideDefault(
    Map<String, Object> context,
    PreconfiguredJobStageProperties preconfiguredJob,
    String application
  ) {
    // without converting this object, assignments to `context[it]` will result in
    // references being assigned instead of values which causes the overrides in context
    // to override the underlying job. this avoids that problem by giving us a fresh "copy"
    // to work wit
    Map<String, Object> preconfiguredMap = objectMapper.convertValue(preconfiguredJob, Map.class)

    // if we don't specify an application for this preconfigured job, assign the current one.
    if (preconfiguredMap["cluster"] != null && preconfiguredMap["cluster"].application == null) {
      preconfiguredMap["cluster"].application = application
    }

    preconfiguredJob.getOverridableFields().each {
      if (context[it] == null || preconfiguredMap[it] != null) {
        context[it] = preconfiguredMap[it]
      }
    }
    preconfiguredJob.parameters.each { defaults ->
      if (defaults.defaultValue != null) {
        Eval.xy(context, defaults.defaultValue, "x.${defaults.mapping} = y.toString()")
      }
    }
    if (context.parameters) {
      context.parameters.each { k, v ->
        def parameterDefinition = preconfiguredJob.parameters.find { it.name == k }
        if (parameterDefinition) {
          Eval.xy(context, v, "x.${parameterDefinition.mapping} = y.toString()")
        }
      }
    }
    context.preconfiguredJobParameters = preconfiguredJob.parameters
    return context
  }

}
