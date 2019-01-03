/*
 * Copyright (c) 2018 Nike, inc.
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

package com.netflix.kayenta.canaryanalysis.orca.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.canary.CanaryExecutionStatusResponse;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisConfig;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisExecutionRequest;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisExecutionResult;
import com.netflix.kayenta.canaryanalysis.domain.CanaryExecutionResult;
import com.netflix.kayenta.canaryanalysis.orca.stage.RunCanaryStage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;
import static com.netflix.kayenta.canaryanalysis.orca.task.MonitorCanaryTask.CANARY_EXECUTION_STATUS_RESPONSE;
import static com.netflix.kayenta.canaryanalysis.service.CanaryAnalysisService.CANARY_ANALYSIS_CONFIG_CONTEXT_KEY;
import static java.util.Collections.emptyMap;

/**
 * Task that generates the final results for the canary analysis execution.
 */
@Component
@Slf4j
public class GenerateCanaryAnalysisResultTask implements Task {

  public static final String CANARY_ANALYSIS_EXECUTION_RESULT = "canaryAnalysisExecutionResult";
  private final ObjectMapper kayentaObjectMapper;

  @Autowired
  public GenerateCanaryAnalysisResultTask(ObjectMapper kayentaObjectMapper) {
    this.kayentaObjectMapper = kayentaObjectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    // Get the request out of the context
    CanaryAnalysisConfig canaryAnalysisConfig = kayentaObjectMapper
        .convertValue(stage.getContext().get(CANARY_ANALYSIS_CONFIG_CONTEXT_KEY), CanaryAnalysisConfig.class);

    CanaryAnalysisExecutionRequest canaryAnalysisExecutionRequest = canaryAnalysisConfig.getExecutionRequest();

    // Get the stages that have the canary execution results
    List<Stage> runCanaryStages = getRunCanaryStages(stage);

    // Get the ordered canary scores as a Linked List.)
    LinkedList<Double> canaryScores = runCanaryStages.stream()
        .map(scoreStage -> kayentaObjectMapper.convertValue(scoreStage.getContext()
            .getOrDefault("canaryScore", 0D), Double.class))
        .collect(Collectors.toCollection(LinkedList::new));

    AggregatedJudgement aggregatedJudgement = null;
    // Get the aggregated judgement if there was at least 1 canary score.
    if (canaryScores.size() > 0) {
      // Get the aggregated decision
      Double finalCanaryScore = canaryScores.getLast();
      Double marginalThreshold = canaryAnalysisExecutionRequest.getThresholds().getMarginal();
      Double passThreshold = canaryAnalysisExecutionRequest.getThresholds().getPass();
      aggregatedJudgement = getAggregatedJudgment(finalCanaryScore, marginalThreshold, passThreshold);
    } else {
      aggregatedJudgement = new AggregatedJudgement(false,
          "There were no successful canary judgements to aggregate");
    }

    // Generate the result from the canary executions and aggregated decision.
    CanaryAnalysisExecutionResult result = CanaryAnalysisExecutionResult.builder()
        .didPassThresholds(aggregatedJudgement.didPassThresholds)
        .canaryScoreMessage(aggregatedJudgement.getMsg())
        .canaryScores(canaryScores)
        .hasWarnings(runCanaryStages.stream()
            .map(runStage -> kayentaObjectMapper.convertValue(runStage.getContext()
                .getOrDefault("warnings", new LinkedList<>()), new TypeReference<LinkedList<String>>() {}))
            .anyMatch(warnings -> ((List<String>) warnings).size() > 0)
        )
        .canaryExecutionResults(runCanaryStages.stream().map(runStage -> {
          Object data = Optional.ofNullable(runStage.getContext().getOrDefault(CANARY_EXECUTION_STATUS_RESPONSE, null))
              .orElseThrow(() -> new IllegalStateException("Expected completed runCanaryStage to have canaryExecutionStatusResponse in context"));

          // Get the canaryExecutionStatusResponse out of the stage context
          CanaryExecutionStatusResponse canaryExecutionStatusResponse =
              kayentaObjectMapper.convertValue(data, CanaryExecutionStatusResponse.class);

          return CanaryExecutionResult.builder()
              .executionId(canaryExecutionStatusResponse.getPipelineId())
              .executionStatus(ExecutionStatus.valueOf(canaryExecutionStatusResponse.getStatus().toUpperCase()))
              // Grab the exception from the context, the monitor task adds an exception for canceled canary executions
              .exception(runStage.getContext().getOrDefault("exception", null))
              .warnings(kayentaObjectMapper.convertValue(runStage.getContext()
                  .getOrDefault("warnings", new LinkedList<>()), new TypeReference<LinkedList<String>>() {}))
              .result(canaryExecutionStatusResponse.getResult())
              .metricSetPairListId(canaryExecutionStatusResponse.getMetricSetPairListId())
              .judgementStartTimeIso(Optional.ofNullable(
                  (String) runStage.getContext()
                  .getOrDefault("judgementStartTimeIso", null))
                  .orElseThrow(() -> new IllegalStateException("Expected completed runCanaryStage to have judgementStartTimeIso in context")))
              .judgementStartTimeMillis(Optional.ofNullable(
                  (Long) runStage.getContext()
                      .getOrDefault("judgementStartTimeMillis", null))
                  .orElseThrow(() -> new IllegalStateException("Expected completed runCanaryStage to have judgementStartTimeIso in context")))
              .judgementEndTimeIso(Optional.ofNullable(
                  (String) runStage.getContext()
                      .getOrDefault("judgementEndTimeIso", null))
                  .orElseThrow(() -> new IllegalStateException("Expected completed runCanaryStage to have judgementStartTimeIso in context")))
              .judgementEndTimeMillis(Optional.ofNullable(
                  (Long) runStage.getContext()
                      .getOrDefault("judgementEndTimeMillis", null))
                  .orElseThrow(() -> new IllegalStateException("Expected completed runCanaryStage to have judgementStartTimeIso in context")))
              .buildTimeMillis(canaryExecutionStatusResponse.getBuildTimeMillis())
              .buildTimeIso(canaryExecutionStatusResponse.getBuildTimeIso())
              .startTimeMillis(canaryExecutionStatusResponse.getStartTimeMillis())
              .startTimeIso(canaryExecutionStatusResponse.getStartTimeIso())
              .endTimeMillis(canaryExecutionStatusResponse.getEndTimeMillis())
              .endTimeIso(canaryExecutionStatusResponse.getEndTimeIso())
              .storageAccountName(canaryExecutionStatusResponse.getStorageAccountName())
              .configurationAccountName(canaryExecutionStatusResponse.getConfigurationAccountName())
              .build();
        }).collect(Collectors.toList()))
        .build();

    return new TaskResult(SUCCEEDED, emptyMap(),
        Collections.singletonMap(CANARY_ANALYSIS_EXECUTION_RESULT, result));
  }

  /**
   * Gets the run canary stages that contain the results
   */
  @NotNull
  protected List<Stage> getRunCanaryStages(@Nonnull Stage stage) {
    // Collect the Run Canary Stages where the parent id is itself
    // Sorting by number after the # in the name
    return stage.getExecution().getStages().stream()
        .filter(s -> s.getType().equals(RunCanaryStage.STAGE_TYPE))
        .sorted(Comparator.comparing(s -> Integer.valueOf(StringUtils.substringAfterLast(s.getName(), "#"))))
        .collect(Collectors.toList());
  }

  /**
   * Generates the final didPassThresholds boolean and adds context around the decision.
   *
   * @param finalCanaryScore The final canary score of the last canary execution.
   * @param marginalThreshold The determined marginal threshold score.
   * @param passThreshold The determined pass threshold score.
   * @return
   */
  protected AggregatedJudgement getAggregatedJudgment(Double finalCanaryScore, Double marginalThreshold, Double passThreshold) {
    boolean didPassThresholds;
    String msg;
    if (marginalThreshold == null && passThreshold == null) {
      didPassThresholds = true;
      msg ="No score thresholds were specified.";
    } else if (marginalThreshold != null && finalCanaryScore <= marginalThreshold) {
      didPassThresholds = false;
      msg = String.format("Final canary score %s is not above the marginal score threshold.", finalCanaryScore);
    } else if (passThreshold == null) {
      didPassThresholds = true;
      msg = "No pass score threshold was specified.";
    } else if (finalCanaryScore < passThreshold) {
      didPassThresholds = false;
      msg = String.format("Final canary score %s is below the pass score threshold.", finalCanaryScore);
    } else {
      didPassThresholds = true;
      msg = String.format("Final canary score %s met or exceeded the pass score threshold.", finalCanaryScore);
    }
    return new AggregatedJudgement(didPassThresholds, msg);
  }

  /**
   * Wrapper object around tuple of data needed for decision.
   */
  @Data
  @AllArgsConstructor
  protected class AggregatedJudgement {
    private boolean didPassThresholds;
    private String msg;
  }
}
