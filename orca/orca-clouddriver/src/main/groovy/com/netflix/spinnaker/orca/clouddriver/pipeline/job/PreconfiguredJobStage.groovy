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
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.preconfigured.jobs.PreconfiguredJobStageProperties
import com.netflix.spinnaker.orca.clouddriver.exception.PreconfiguredJobNotFoundException
import com.netflix.spinnaker.orca.clouddriver.service.JobService
import com.netflix.spinnaker.orca.clouddriver.tasks.job.DestroyJobTask
import io.kubernetes.client.openapi.JSON
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
class PreconfiguredJobStage extends RunJobStage {

  private JobService jobService
  private ObjectMapper objectMapper
  private JSON json

  @Autowired
  PreconfiguredJobStage(DestroyJobTask destroyJobTask, List<RunJobStageDecorator> runJobStageDecorators, Optional<JobService> optionalJobService) {
    super(destroyJobTask, runJobStageDecorators)
    this.jobService = optionalJobService.orElse(null)
    this.objectMapper = new ObjectMapper()
    this.json = new JSON()
  }

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
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
    // `manifest` field defined in `KubernetesPreconfiguredJobProperties` class has
    // `io.kubernetes.client.openapi.models.V1Job` type. This type is defined in Kubernetes
    // Open API models package. This package contains special JSON serializers e.g
    // `io.kubernetes.client.custom.Quantity.QuantityAdapter`. They must be used to get correct JSON format.
    String preconfiguredJobJson = json.getGson().toJson(preconfiguredJob)

    // without converting this object, assignments to `context[it]` will result in
    // references being assigned instead of values which causes the overrides in context
    // to override the underlying job. this avoids that problem by giving us a fresh "copy"
    // to work wit
    Map<String, Object> preconfiguredMap = objectMapper.readValue(preconfiguredJobJson, Map.class)

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
        setNestedValue(context, defaults.mapping, defaults.defaultValue)
      }
    }
    if (context.parameters) {
      context.parameters.each { k, v ->
        def parameterDefinition = preconfiguredJob.parameters.find { it.name == k }
        if (parameterDefinition) {
          setNestedValue(context, parameterDefinition.mapping, v.toString())
        }
      }
    }

    //Allow for parameters not defined in the preconfigured job parameters configuration to be passed
    //in via dynamicPreconfiguredParameters.  This way additional optional parameters may be used
    //without requiring an update to the configuration.
    //If the root property does not exist in the preconfigured job configuration, an
    //IllegalArgumentException is thrown.
    if (context.dynamicParameters) {
      context.dynamicParameters.each { k, v ->
        setNestedValue(context, (String)k, v.toString())
      }
    }

    context.preconfiguredJobParameters = preconfiguredJob.parameters
    return context
  }

  private static void setNestedValue(Object root, String mapping, Object value) {
    if (mapping == null) {
      return
    }

    String[] props = mapping.split(/\./)
    Object current = root
    for (int i = 0; i < props.length - 1; i++) {
      Object next
      if(props[i] ==~ /.*\[\d+\]$/) {
        next = getValueFromArrayExpression(current, props[i])
      } else {
        next = current[props[i]]
        if (next == null) {
          throw new IllegalArgumentException("no property ${props[i]} on $current")
        }
      }
      current = next
    }
    current[props.last()] = value
  }

  private static Object getValueFromArrayExpression(Object root, String expression) {
    // TODO: Do we need to handle arrays nested in other arrays?
    String[] parts = expression.split(/[\[\]]/)
    String propName = parts[0]
    int index = -1
    try {
      index = Integer.parseInt(parts[1])
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Unable to parse index from expresion ${expression}", ex)
    }
    List<Object> nextList = root[propName]
    if (nextList == null) {
      throw new IllegalArgumentException("no property ${propName} on $root")
    }
    if (nextList.size() <= index || index < 0) {
      throw new IllegalArgumentException("Invalid index $index for list $propName")
    }
    return nextList.get(index)
  }

}
