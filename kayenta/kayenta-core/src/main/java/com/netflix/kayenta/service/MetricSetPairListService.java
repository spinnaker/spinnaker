/*
 * Copyright 2020 Playtika, Inc.
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

package com.netflix.kayenta.service;

import com.netflix.kayenta.metrics.MetricSetPair;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MetricSetPairListService {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;

  public List<MetricSetPair> loadMetricSetPairList(String accountName, String metricSetPairListId) {
    var resolvedAccountName = getAccount(accountName);
    StorageService storageService = storageServiceRepository.getRequiredOne(resolvedAccountName);

    return storageService.loadObject(
        resolvedAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId);
  }

  public Optional<MetricSetPair> loadMetricSetPair(
      String accountName, String metricSetPairListId, String metricSetPairId) {
    String resolvedAccountName = getAccount(accountName);
    StorageService storageService = storageServiceRepository.getRequiredOne(resolvedAccountName);

    List<MetricSetPair> metricSetPairList =
        storageService.loadObject(
            resolvedAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId);
    return metricSetPairList.stream()
        .filter(metricSetPair -> metricSetPair.getId().equals(metricSetPairId))
        .findFirst();
  }

  public String storeMetricSetPairList(String accountName, List<MetricSetPair> metricSetPairList) {
    String resolvedAccountName = getAccount(accountName);
    StorageService storageService = storageServiceRepository.getRequiredOne(resolvedAccountName);
    String metricSetPairListId = UUID.randomUUID() + "";

    storageService.storeObject(
        resolvedAccountName,
        ObjectType.METRIC_SET_PAIR_LIST,
        metricSetPairListId,
        metricSetPairList);
    return metricSetPairListId;
  }

  public void deleteMetricSetPairList(String accountName, String metricSetPairListId) {
    String resolvedAccountName = getAccount(accountName);
    StorageService storageService = storageServiceRepository.getRequiredOne(resolvedAccountName);

    storageService.deleteObject(
        resolvedAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId);
  }

  public List<Map<String, Object>> listAllMetricSetPairLists(String accountName) {
    String resolvedAccountName = getAccount(accountName);
    StorageService storageService = storageServiceRepository.getRequiredOne(resolvedAccountName);

    return storageService.listObjectKeys(resolvedAccountName, ObjectType.METRIC_SET_PAIR_LIST);
  }

  private String getAccount(String accountName) {
    return accountCredentialsRepository
        .getRequiredOneBy(accountName, AccountCredentials.Type.OBJECT_STORE)
        .getName();
  }
}
