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
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MetricSetMixerServiceTask implements RetryableTask {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  StorageServiceRepository storageServiceRepository;

  @Autowired
  MetricSetMixerService metricSetMixerService;

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

  @Override
  public TaskResult execute(Stage stage) {
    Map<String, Object> context = stage.getContext();
    String storageAccountName = (String)context.get("storageAccountName");
    List<String> controlMetricSetListIds = (List<String>)context.get("controlMetricSetListIds");
    List<String> experimentMetricSetListIds = (List<String>)context.get("experimentMetricSetListIds");
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

    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
  }
}
