/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.front50.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;
import com.netflix.spinnaker.orca.front50.DependentPipelineStarter;
import com.netflix.spinnaker.orca.front50.multiplepipelines.App;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class TriggerInOrder implements Runnable {

  private final Logger logger = LoggerFactory.getLogger(TriggerInOrder.class);

  private final Map<String, Object> pipelineConfigCopy;
  private final StageExecution stage;
  private final App app;
  private final DependentPipelineStarter dependentPipelineStarter;
  private PipelineExecution pipelineExecution;

  public TriggerInOrder(
      Map<String, Object> pipelineConfigCopy,
      StageExecution stage,
      App app,
      DependentPipelineStarter dependentPipelineStarter) {
    this.pipelineConfigCopy = pipelineConfigCopy;
    this.stage = stage;
    this.app = app;
    this.dependentPipelineStarter = dependentPipelineStarter;
  }

  @SneakyThrows
  @Override
  public void run() {
    /*
    Reduce size of child execution as ParentPipeline is stored under trigger
    We removed other stages and only keep the RunMultiPipeline Stage that will trigger the child
    Also we removed context and outputs to store less info
    */
    ObjectMapper objectOrcaMapper = OrcaObjectMapper.getInstance();
    PipelineExecution pipelineExecutionCopy =
        objectOrcaMapper.readValue(
            objectOrcaMapper.writeValueAsString(stage.getExecution()), PipelineExecution.class);
    pipelineExecutionCopy.getStages().clear();
    StageExecution runMultiPipelineStage =
        stage.getExecution().getStages().stream()
            .filter(s -> s.getId().equals(stage.getId()))
            .findFirst()
            .get();
    StageExecution runMultiPipelineStageCopy =
        objectOrcaMapper.convertValue(runMultiPipelineStage, StageExecution.class);
    runMultiPipelineStageCopy.setExecution(stage.getExecution());
    runMultiPipelineStageCopy.getContext().clear();
    runMultiPipelineStageCopy.getOutputs().clear();
    pipelineExecutionCopy.getStages().add(runMultiPipelineStageCopy);

    /*
    Create a Trigger with one extra property "executionIdentifier" to identify the child execution
    ExecutionIdentifier property is under trigger.parentExecution.trigger
    Check the runMultiplePipelines stage execution details in deck for an example of where it is used
    */
    Map modifiedTrigger =
        objectOrcaMapper.readValue(
            objectOrcaMapper.writeValueAsString(pipelineExecutionCopy.getTrigger()), Map.class);
    modifiedTrigger.put("executionIdentifier", app.getArguments().remove("executionIdentifier"));
    Trigger trigger =
        objectOrcaMapper.readValue(
            objectOrcaMapper.writeValueAsString(modifiedTrigger), Trigger.class);
    pipelineExecutionCopy.setTrigger(trigger);

    try {
      this.pipelineExecution =
          dependentPipelineStarter.trigger(
              pipelineConfigCopy,
              stage.getExecution().getAuthentication().getUser(),
              pipelineExecutionCopy,
              app.getArguments(),
              stage.getId(),
              getUser(stage.getExecution()));
    } catch (Throwable e) {
      logger.error("Entering try catch message {} ", e.getMessage());
      if (e.getMessage() != null) {
        stage.appendErrorMessage(e.getMessage());
        stage.getOutputs().put("failureMessage", e.getMessage());
      }
      return;
    }
    logger.info(
        "Triggered Execution of child pipeline " + modifiedTrigger.get("executionIdentifier"));
  }

  private PipelineExecution.AuthenticationDetails getUser(PipelineExecution parentPipeline) {
    Optional<String> korkUsername = AuthenticatedRequest.getSpinnakerUser();
    if (korkUsername.isPresent()) {
      String korkAccounts = AuthenticatedRequest.getSpinnakerAccounts().orElse("");
      return new PipelineExecution.AuthenticationDetails(
          korkUsername.get(), korkAccounts.split(","));
    }

    if (parentPipeline.getAuthentication().getUser() != null) {
      return parentPipeline.getAuthentication();
    }

    return null;
  }
}
