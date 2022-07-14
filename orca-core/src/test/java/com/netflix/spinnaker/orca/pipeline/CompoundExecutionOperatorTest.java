/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.orca.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class CompoundExecutionOperatorTest {
  private static final String APPLICATION = "myapp";
  private static final String PIPELINE = "mypipeline";
  private static final String EXECUTION_ID = "EXECUTION_ID";
  private static final String STAGE_ID = "STAGE_ID";
  private final ExecutionRepository repository = mock(ExecutionRepository.class);
  private final ExecutionRunner runner = mock(ExecutionRunner.class);
  private final RetrySupport retrySupport = mock(RetrySupport.class);
  private CompoundExecutionOperator executionOperator =
      new CompoundExecutionOperator(repository, runner, retrySupport);
  private PipelineExecution execution =
      new PipelineExecutionImpl(ExecutionType.PIPELINE, EXECUTION_ID, APPLICATION);

  @Test
  void restartStageWithValidExpression() {

    execution = buildExpectedExecution(execution, true);
    String expression = "'${( #stage(\"Jenkins1\")[\"status\"] matches ''SUCCEEDED|SKIPPED'')";
    Map expectedRestartDetails = buildExpectedRestartDetailsMap(expression);

    List<Map> expectedStageList = (List<Map>) expectedRestartDetails.get("stages");
    Map expectedStageMap = (Map) expectedStageList.get(0);

    when(repository.retrieve(any(), anyString())).thenReturn(execution);
    when(repository.handlesPartition(execution.getPartition())).thenReturn(true);

    PipelineExecution updatedExecution =
        executionOperator.restartStage(EXECUTION_ID, STAGE_ID, expectedRestartDetails);

    List<Map> updatedPreconditionsList =
        (List<Map>) updatedExecution.getStages().get(0).getContext().get("preconditions");
    Map updatedContextMap = (Map) updatedPreconditionsList.get(0).get("context");

    assertEquals(expression, updatedContextMap.get("expression"));
    assertEquals(expectedStageMap.get("type"), updatedExecution.getStages().get(0).getType());
    assertEquals(APPLICATION, updatedExecution.getApplication());
  }

  @Test
  void restartStageWithNoPreconditions() {

    execution = buildExpectedExecution(execution, false);
    Map expectedRestartDetails = buildExpectedRestartDetailsMap(null);

    List<Map> expectedStageList = (List<Map>) expectedRestartDetails.get("stages");
    Map expectedStageMap = (Map) expectedStageList.get(0);

    when(repository.retrieve(any(), anyString())).thenReturn(execution);
    when(repository.handlesPartition(execution.getPartition())).thenReturn(true);

    PipelineExecution updatedExecution =
        executionOperator.restartStage(EXECUTION_ID, STAGE_ID, expectedRestartDetails);

    Map<String, Object> updatedPreconditionsList = updatedExecution.getStages().get(0).getContext();

    assertEquals(null, updatedPreconditionsList.get("preconditions"));
    assertEquals(expectedStageMap.get("type"), updatedExecution.getStages().get(0).getType());
    assertEquals(APPLICATION, updatedExecution.getApplication());
  }

  private PipelineExecution buildExpectedExecution(
      PipelineExecution execution, boolean expression) {
    if (expression) {
      Map<String, Object> contextMap = new HashMap<>();
      List<Map> preconditionList = new ArrayList();
      Map preconditionMap = new HashMap<>();
      Map expressionContextMap = new HashMap<>();

      expressionContextMap.put("expression", expression);
      expressionContextMap.put("failureMessage", "Precondition failed");

      preconditionMap.put("context", expressionContextMap);
      preconditionMap.put("failPipeline", true);
      preconditionMap.put("type", "expression");

      preconditionList.add(preconditionMap);

      contextMap.put("preconditions", preconditionList);

      StageExecution preconditionStage = new StageExecutionImpl();
      preconditionStage.setId(STAGE_ID);
      preconditionStage.setType("checkPreconditions");
      preconditionStage.setName("Check Preconditions");
      preconditionStage.setContext(contextMap);

      execution.getStages().add(preconditionStage);
    } else {
      StageExecution jenkinsStage = new StageExecutionImpl();
      jenkinsStage.setId(STAGE_ID);
      jenkinsStage.setType("jenkins");
      jenkinsStage.setName("Jenkins1");

      execution.getStages().add(jenkinsStage);
    }
    return execution;
  }

  private Map buildExpectedRestartDetailsMap(String expression) {
    Map restartDetails = new HashMap<>();
    List<Map> pipelineStageList = new ArrayList();

    if (expression != null) {
      Map preconditionStageMap = new HashMap<>();
      List<Map> preconditionList = new ArrayList();
      Map preconditionMap = new HashMap<>();
      Map contextMap = new HashMap<>();

      contextMap.put("expression", expression);
      contextMap.put("failureMessage", "Precondition failed");

      preconditionMap.put("context", contextMap);
      preconditionMap.put("failPipeline", true);
      preconditionMap.put("type", "expression");

      preconditionList.add(preconditionMap);

      preconditionStageMap.put("name", "Check Preconditions");
      preconditionStageMap.put("type", "checkPreconditions");
      preconditionStageMap.put("preconditions", preconditionList);

      pipelineStageList.add(preconditionStageMap);
    } else {
      Map jenkinsStageMap = new HashMap<>();
      jenkinsStageMap.put("name", "Jenkins");
      jenkinsStageMap.put("job", "Jenkins_Job");
      jenkinsStageMap.put("type", "jenkins");

      pipelineStageList.add(jenkinsStageMap);
    }

    restartDetails.put("application", APPLICATION);
    restartDetails.put("name", PIPELINE);
    restartDetails.put("executionId", EXECUTION_ID);
    restartDetails.put("stages", pipelineStageList);

    return restartDetails;
  }
}
