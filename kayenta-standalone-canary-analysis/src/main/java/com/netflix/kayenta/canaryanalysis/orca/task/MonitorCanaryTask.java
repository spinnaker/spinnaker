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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.canary.CanaryExecutionStatusResponse;
import com.netflix.kayenta.canary.ExecutionMapper;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.kayenta.canaryanalysis.domain.MonitorKayentaCanaryContext;
import com.netflix.kayenta.canaryanalysis.domain.Stats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.netflix.spinnaker.orca.ExecutionStatus.CANCELED;
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING;
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;
import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL;
import static java.util.Collections.singletonMap;

/**
 * Java port of <a href="https://github.com/spinnaker/orca/blob/master/orca-kayenta/src/main/kotlin/com/netflix/spinnaker/orca/kayenta/tasks/MonitorKayentaCanaryTask.kt">MonitorCanaryTask</a>
 * with alterations to use ad-hoc endpoint rather that pre-defined canary-config endpoint.
 *
 * This tasks monitors a canary judgement execution waiting for it to complete and processing the results.
 */
@Component
@Slf4j
public class MonitorCanaryTask implements Task, OverridableTimeoutRetryableTask {

  public static final String CANARY_EXECUTION_STATUS_RESPONSE = "canaryExecutionStatusResponse";

  private final ExecutionRepository executionRepository;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final ExecutionMapper executionMapper;


  @Autowired
  public MonitorCanaryTask(ExecutionRepository executionRepository,
                           AccountCredentialsRepository accountCredentialsRepository,
                           ExecutionMapper executionMapper) {

    this.executionRepository = executionRepository;
    this.accountCredentialsRepository = accountCredentialsRepository;

    this.executionMapper = executionMapper;
  }

  @Override
  public long getBackoffPeriod() {
    return 1000L;
  }

  @Override
  public long getTimeout() {
    return Duration.ofHours(12).toMillis();
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    MonitorKayentaCanaryContext context = stage.mapTo(MonitorKayentaCanaryContext.class);
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(context.getStorageAccountName(),
        AccountCredentials.Type.OBJECT_STORE,
        accountCredentialsRepository);

    Execution pipeline = executionRepository.retrieve(Execution.ExecutionType.PIPELINE, context.getCanaryPipelineExecutionId());

    CanaryExecutionStatusResponse statusResponse =  executionMapper.fromExecution(resolvedStorageAccountName, pipeline);

    ExecutionStatus executionStatus = ExecutionStatus.valueOf(statusResponse.getStatus().toUpperCase());

    if (executionStatus == SUCCEEDED) {
      // Can throw an NPE, which is desired here?
      double canaryScore = statusResponse.getResult().getJudgeResult().getScore().getScore();
      List<String> warnings = getResultsWarnings(context, statusResponse);

      Map<String, Object> resultContext = new HashMap<>();
      ExecutionStatus resultStatus;
      if (canaryScore <= context.getScoreThresholds().getMarginal()) {
        resultStatus = TERMINAL;
        resultContext.put("canaryScoreMessage", "Canary score is not above the marginal score threshold.");
      } else {
        resultStatus = SUCCEEDED;
      }

      resultContext.put(CANARY_EXECUTION_STATUS_RESPONSE, statusResponse);
      resultContext.put("canaryScore", canaryScore);
      resultContext.put("warnings", warnings);

      return new TaskResult(resultStatus, resultContext);
    }

    if (executionStatus.isHalt()) {
      Map<String, Object> resultContext = new HashMap<>();
      resultContext.put("canaryPipelineStatus", executionStatus);

      if (executionStatus == CANCELED) {
        resultContext.put("exception", ImmutableMap.of("details", ImmutableMap.of("errors",
            ImmutableList.of("Canary execution was canceled."))));
      } else {
        Optional.ofNullable(statusResponse.getException())
            .ifPresent(exception -> resultContext.put("exception", exception));
      }
      resultContext.put(CANARY_EXECUTION_STATUS_RESPONSE, statusResponse);

      // Indicates a failure of some sort.
      return new TaskResult(TERMINAL, resultContext);
    }

    return new TaskResult(RUNNING, singletonMap("canaryPipelineStatus", executionStatus));
  }

  /**
   * Generates warnings that will be propigated in the aggregated results.
   */
  protected List<String> getResultsWarnings(MonitorKayentaCanaryContext context, CanaryExecutionStatusResponse statusResponse) {
    List<String> warnings = new LinkedList<>();

    String credentialType = "";
    if (context.getMetricsAccountName() != null) {
      Set<? extends AccountCredentials> allCredentials = accountCredentialsRepository.getAll();
      Optional<? extends AccountCredentials> credential = allCredentials.stream()
          .filter(cred -> cred.getName().equals(context.getMetricsAccountName()))
          .findAny();
      if (credential.isPresent()) {
        credentialType = credential.get().getType();
      }
    }

    // Datadog doesn't return data points in the same way as other metrics providers
    // and so are excluded here.  See this Github comment for more information:
    // https://github.com/spinnaker/kayenta/issues/283#issuecomment-397346975
    final ObjectMapper om = new ObjectMapper();
    if (! credentialType.equals("datadog") && statusResponse.getResult().getJudgeResult().getResults().stream()
        .anyMatch(canaryAnalysisResult ->
            om.convertValue(canaryAnalysisResult.getControlMetadata().get("stats"), Stats.class).getCount() < 50)) {

      warnings.add("One of the metrics returned fewer than 50 data points, which can reduce confidence in the final canary score.");
    }
    return warnings;
  }
}
