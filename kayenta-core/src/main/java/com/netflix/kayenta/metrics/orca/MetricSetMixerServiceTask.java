/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.metrics.orca;

import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricSetMixerService;
import com.netflix.kayenta.metrics.MetricSetPair;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class MetricSetMixerServiceTask implements RetryableTask {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final MetricSetMixerService metricSetMixerService;

  @Autowired
  public MetricSetMixerServiceTask(AccountCredentialsRepository accountCredentialsRepository,
                                   StorageServiceRepository storageServiceRepository,
                                   MetricSetMixerService metricSetMixerService) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.storageServiceRepository = storageServiceRepository;
    this.metricSetMixerService = metricSetMixerService;
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
    String storageAccountName = (String)context.get("storageAccountName");
    List<String> controlMetricSetListIds = getMetricSetListIds(stage.getExecution(), (String)context.get("controlRefidPrefix"));
    List<String> experimentMetricSetListIds = getMetricSetListIds(stage.getExecution(), (String)context.get("experimentRefidPrefix"));
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to load metric set lists."));

    int controlMetricSetListIdsSize = controlMetricSetListIds.size();
    int experimentMetricSetListIdsSize = experimentMetricSetListIds.size();

    if (controlMetricSetListIdsSize != experimentMetricSetListIdsSize) {
      throw new IllegalArgumentException("Size of controlMetricSetListIds (" + controlMetricSetListIdsSize + ") does not " +
                                         "match size of experimentMetricSetListIds (" + experimentMetricSetListIdsSize + ").");
    }

    List<MetricSetPair> aggregatedMetricSetPairList = new ArrayList<>();

    for (int i = 0; i < controlMetricSetListIdsSize; i++) {
      List<MetricSet> controlMetricSetList =
        storageService.loadObject(resolvedAccountName, ObjectType.METRIC_SET_LIST, controlMetricSetListIds.get(i));
      List<MetricSet> experimentMetricSetList =
        storageService.loadObject(resolvedAccountName, ObjectType.METRIC_SET_LIST, experimentMetricSetListIds.get(i));
      List<MetricSetPair> metricSetPairList =
        metricSetMixerService.mixAll(controlMetricSetList, experimentMetricSetList);

      aggregatedMetricSetPairList.addAll(metricSetPairList);
    }

    String aggregatedMetricSetPairListId = UUID.randomUUID() + "";

    storageService.storeObject(resolvedAccountName, ObjectType.METRIC_SET_PAIR_LIST, aggregatedMetricSetPairListId, aggregatedMetricSetPairList);

    Map outputs = Collections.singletonMap("metricSetPairListId", aggregatedMetricSetPairListId);

    return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.emptyMap(), outputs);
  }

  private List<String> getMetricSetListIds(Execution execution, String stagePrefix) {
    List<Stage> stages = execution.getStages();
    return stages.stream()
      .filter(stage -> {
        String refId = stage.getRefId();
        return refId != null && refId.startsWith(stagePrefix);
      })
      .map(stage -> (String)stage.getOutputs().get("metricSetId"))
      .collect(Collectors.toList());
  }
}
