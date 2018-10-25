/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.kayenta.canary.orca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.canary.CanaryJudge;
import com.netflix.kayenta.canary.ExecutionMapper;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CompareJudgeResultsTask implements RetryableTask {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final List<CanaryJudge> canaryJudges;
  private final ObjectMapper objectMapper;
  private final ExecutionMapper executionMapper;

  @Autowired
  public CompareJudgeResultsTask(AccountCredentialsRepository accountCredentialsRepository,
                                 StorageServiceRepository storageServiceRepository,
                                 List<CanaryJudge> canaryJudges,
                                 ObjectMapper kayentaObjectMapper,
                                 ExecutionMapper executionMapper) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.storageServiceRepository = storageServiceRepository;
    this.canaryJudges = canaryJudges;
    this.objectMapper = kayentaObjectMapper;
    this.executionMapper = executionMapper;
  }

  @Override
  public long getBackoffPeriod() {
    // TODO(duftler): Externalize this configuration.
    return Duration.ofSeconds(2).toMillis();
  }

  @Override
  public long getTimeout() {
    // TODO(duftler): Externalize this configuration.
    return Duration.ofMinutes(2).toMillis();
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    Map<String, Object> context = stage.getContext();
    Map judge1Result = (Map)context.get("judge1Result");
    Map judge2Result = (Map)context.get("judge2Result");

    // TODO: Now that the plumbing works, perform some kind of actual comparison.
    Map<String, Map> comparisonResult =
      ImmutableMap.<String, Map>builder()
        .put("judge1Result", judge1Result)
        .put("judge2Result", judge2Result)
        .build();
    Map<String, Map> outputs = Collections.singletonMap("comparisonResult", comparisonResult);

    return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.emptyMap(), outputs);
  }
}
