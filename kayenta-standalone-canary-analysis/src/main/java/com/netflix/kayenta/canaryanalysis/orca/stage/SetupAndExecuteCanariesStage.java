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

package com.netflix.kayenta.canaryanalysis.orca.stage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopePair;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisConfig;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.WaitStage;
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.kayenta.canaryanalysis.domain.CanaryAnalysisExecutionRequest;
import com.netflix.kayenta.canaryanalysis.domain.RunCanaryContext;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.netflix.kayenta.canaryanalysis.service.CanaryAnalysisService.CANARY_ANALYSIS_CONFIG_CONTEXT_KEY;
import static java.time.Duration.ZERO;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MINUTES;

/**
 * This stage setups up the canary execution stages and executes / monitors them.
 * This stage will trigger the GenerateCanaryAnalysisResultStage always.
 */
@Component
public class SetupAndExecuteCanariesStage implements StageDefinitionBuilder {

  public static final String STAGE_TYPE = "setupAndExecuteCanariesStage";
  public static final String STAGE_DESCRIPTION = "Sets up and executes the wait, run and monitor canary task chain.";
  private static final Instant ZERO_AS_INSTANT = Instant.ofEpochMilli(ZERO.toMillis());

  private final Clock clock;
  private final ObjectMapper kayentaObjectMapper;

  @Autowired
  public SetupAndExecuteCanariesStage(Clock clock,
                                      ObjectMapper kayentaObjectMapper) {

    this.clock = clock;
    this.kayentaObjectMapper = kayentaObjectMapper;
  }

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    // Do nothing, is there a better way to do this? All the real logic is in the before stages method.
  }

  @Override
  public void beforeStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {
    CanaryAnalysisConfig canaryAnalysisConfig = kayentaObjectMapper
        .convertValue(parent.getContext().get(CANARY_ANALYSIS_CONFIG_CONTEXT_KEY), CanaryAnalysisConfig.class);

    CanaryAnalysisExecutionRequest canaryAnalysisExecutionRequest = canaryAnalysisConfig.getExecutionRequest();

    if (canaryAnalysisExecutionRequest.getScopes().isEmpty()) {
      throw new IllegalArgumentException("Canary stage configuration must contain at least one scope.");
    }

    // Calculate start, end, lifetime and judgement intervals.
    Instant start = Optional.ofNullable(canaryAnalysisExecutionRequest.getStartTime()).orElse(now(clock));
    Instant endTime = canaryAnalysisExecutionRequest.getEndTime();
    Duration lifetime = calculateLifetime(start, endTime, canaryAnalysisExecutionRequest);
    Duration analysisInterval = calculateAnalysisInterval(canaryAnalysisExecutionRequest, lifetime);

    // If a wait time was defined at the wait stage to the execution pipeline for the defined wait time.
    if (canaryAnalysisExecutionRequest.getBeginCanaryAnalysisAfterAsInstant().isAfter(ZERO_AS_INSTANT)) {
      graph.append(stage -> {
        stage.setType(WaitStage.STAGE_TYPE);
        stage.setName("Warmup Wait");
        stage.getContext().put("waitTime", canaryAnalysisExecutionRequest.getBeginCanaryAnalysisAfterAsDuration().getSeconds());
      });
    }

    // For each interval add a wait and execute canary stage to the execution pipeline
    int numberOfJudgements = Math.toIntExact((lifetime.toMinutes() / analysisInterval.toMinutes()));
    for (int i = 1; i < numberOfJudgements + 1; i++) {
      final int index = i;
      // If an end time was explicitly specified, we don't need to synchronize
      // the execution of the canary pipeline with the real time.
      if (endTime == null) {
        graph.append(stage -> {
          stage.setType(WaitStage.STAGE_TYPE);
          stage.setName("Interval Wait #" + index);
          stage.getContext().put("waitTime", analysisInterval.getSeconds());
        });
      }

      RunCanaryContext runCanaryContext = RunCanaryContext.builder()
          .application(canaryAnalysisConfig.getApplication())
          .user(canaryAnalysisConfig.getUser())
          .parentPipelineExecutionId(canaryAnalysisConfig.getParentPipelineExecutionId())
          .canaryConfigId(canaryAnalysisConfig.getCanaryConfigId())
          .metricsAccountName(canaryAnalysisConfig.getMetricsAccountName())
          .storageAccountName(canaryAnalysisConfig.getStorageAccountName())
          .canaryConfig(canaryAnalysisConfig.getCanaryConfig())
          .scopes(buildRequestScopes(canaryAnalysisExecutionRequest, i, analysisInterval))
          .scoreThresholds(canaryAnalysisExecutionRequest.getThresholds())
          .siteLocal(canaryAnalysisExecutionRequest.getSiteLocal())
          .build();

      graph.append(stage -> {
        stage.setType(RunCanaryStage.STAGE_TYPE);
        stage.setName(RunCanaryStage.STAGE_NAME_PREFIX + index);
        stage.getContext().putAll(kayentaObjectMapper.convertValue(runCanaryContext,
            new TypeReference<HashMap<String,Object>>() {}));
      });
    }
  }

  /**
   * Calculates the lifetime duration for the canary analysis execution.
   *
   * @param start The calculated start time for the execution
   * @param endTime The calculated endtime
   * @param canaryAnalysisExecutionRequest The execution request
   * @return The calculated duration of the canary analysis
   */
  protected Duration calculateLifetime(Instant start,
                                       Instant endTime,
                                       CanaryAnalysisExecutionRequest canaryAnalysisExecutionRequest) {

    Duration lifetime;
    if (endTime != null) {
      lifetime = Duration.ofMinutes(start.until(endTime, MINUTES));
    } else if (canaryAnalysisExecutionRequest.getLifetimeDuration() != null) {
      lifetime = canaryAnalysisExecutionRequest.getLifetimeDuration();
    } else {
      throw new IllegalArgumentException("Canary stage configuration must include either `endTime` or `lifetimeDuration`.");
    }

    return lifetime;
  }

  /**
   * Calculates the how often a canary judgement should be performed during the lifetime of the canary analysis execution.
   *
   * @param canaryAnalysisExecutionRequest The execution requests
   * @param lifetime The calculated lifetime of the canary analysis execution
   * @return How often a judgement should be performed
   */
  protected Duration calculateAnalysisInterval(CanaryAnalysisExecutionRequest canaryAnalysisExecutionRequest, Duration lifetime) {
    Duration analysisInterval;
    if (canaryAnalysisExecutionRequest.getAnalysisIntervalMins() != null) {
      analysisInterval = Duration.ofMinutes(canaryAnalysisExecutionRequest.getAnalysisIntervalMins());
    } else {
      analysisInterval = lifetime;
    }

    if (analysisInterval == ZERO ||
        Instant.ofEpochMilli(analysisInterval.toMillis()).isAfter(Instant.ofEpochMilli(lifetime.toMillis()))) {

      analysisInterval = lifetime;
    }

    return analysisInterval;
  }

  protected Map<String, CanaryScopePair> buildRequestScopes(CanaryAnalysisExecutionRequest config,
                                                          long interval,
                                                          Duration intervalDuration) {

    Map<String, CanaryScopePair> scopes = new HashMap<>();

    config.getScopes().forEach(scope -> {
      ScopeTimeConfig scopeTimeConfig = calculateStartAndEndForJudgement(config, interval, intervalDuration);

      CanaryScope controlScope = new CanaryScope(
          scope.getControlScope(),
          scope.getControlLocation(),
          scopeTimeConfig.start,
          scopeTimeConfig.end,
          config.getStep().getSeconds(),
          scope.getExtendedScopeParams()
      );

      CanaryScope experimentScope = new CanaryScope(
          scope.getExperimentScope(),
          scope.getExperimentLocation(),
          scopeTimeConfig.start,
          scopeTimeConfig.end,
          config.getStep().getSeconds(),
          scope.getExtendedScopeParams()
      );

      CanaryScopePair canaryScopePair = CanaryScopePair.builder()
          .controlScope(controlScope)
          .experimentScope(experimentScope)
          .build();

      scopes.put(scope.getScopeName(), canaryScopePair);
    });

    return scopes;
  }

  /**
   * Calculates the start and end timestamps that will be used when quering the metrics sources when doing the
   * canary judgements for each judgement interval.
   *
   * @param judgementNumber The judgement number / index for the canary analysis execution
   * @param judgementDuration The duration of the judgement window
   * @param config The execution request config
   * @return A wrapper object containing the start and end times to be used as Instants
   */
  protected ScopeTimeConfig calculateStartAndEndForJudgement(CanaryAnalysisExecutionRequest config,
                                                             long judgementNumber,
                                                             Duration judgementDuration) {

    Duration warmupDuration = config.getBeginCanaryAnalysisAfterAsDuration();
    Duration offset = judgementDuration.multipliedBy(judgementNumber);

    ScopeTimeConfig scopeTimeConfig = new ScopeTimeConfig();

    Instant startTime = Optional.ofNullable(config.getStartTime()).orElse(now(clock));
    scopeTimeConfig.start = startTime;
    scopeTimeConfig.end = startTime.plus(offset);

    if (config.getEndTime() == null) {
      scopeTimeConfig.start = scopeTimeConfig.start.plus(warmupDuration);
      scopeTimeConfig.end   = scopeTimeConfig.end.plus(warmupDuration);
    }

    // If the look back is defined, use it to recalculate the start time, this is used to do sliding window judgements
    if (config.getLookBackAsInstant().isAfter(ZERO_AS_INSTANT)) {
      scopeTimeConfig.start = scopeTimeConfig.end.minus(config.getLookBackAsDuration());
    }

    return scopeTimeConfig;
  }

  @Data
  class ScopeTimeConfig {
    private Instant start;
    private Instant end;
  }

  @Override
  public void onFailureStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {
    addAlwaysRunResultStage(parent, graph);
  }

  @Override
  public void afterStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {
    addAlwaysRunResultStage(parent, graph);
  }

  /**
   * Always run the GenerateCanaryAnalysisResultStage.
   */
  private void addAlwaysRunResultStage(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {
    graph.append(stage -> {
      stage.setType(GenerateCanaryAnalysisResultStage.STAGE_TYPE);
      stage.setName(GenerateCanaryAnalysisResultStage.STAGE_DESCRIPTION);
      stage.setContext(parent.getContext());
    });
  }

  @Nonnull
  @Override
  public String getType() {
    return STAGE_TYPE;
  }
}
