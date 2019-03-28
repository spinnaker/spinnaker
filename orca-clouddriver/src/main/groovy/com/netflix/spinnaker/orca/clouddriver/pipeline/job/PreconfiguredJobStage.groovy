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


import com.netflix.spinnaker.orca.clouddriver.config.PreconfiguredJobStageProperties
import com.netflix.spinnaker.orca.clouddriver.exception.PreconfiguredJobNotFoundException
import com.netflix.spinnaker.orca.clouddriver.service.JobService
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

@Component
class PreconfiguredJobStage extends RunJobStage {

  private JobService jobService

  public PreconfiguredJobStage(Optional<JobService> optionalJobService) {
    this.jobService = optionalJobService.orElse(null)
  }

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    def preconfiguredJob = jobService.getPreconfiguredStages().find { stage.type == it.type }

    if (!preconfiguredJob) {
      throw new PreconfiguredJobNotFoundException((String) stage.type)
    }

    stage.setContext(overrideIfNotSetInContextAndOverrideDefault(stage.context, preconfiguredJob))
    super.taskGraph(stage, builder)
  }

  private Map<String, Object> overrideIfNotSetInContextAndOverrideDefault(Map<String, Object> context, PreconfiguredJobStageProperties preconfiguredJob) {
    preconfiguredJob.getOverridableFields().each {
      if (context[it] == null || preconfiguredJob[it] != null) {
        context[it] = preconfiguredJob[it]
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
