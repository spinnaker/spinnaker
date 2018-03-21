/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.kayenta.controllers;

import com.netflix.kayenta.metrics.MetricSetPair;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/metricSetPairList")
@Slf4j
public class MetricSetPairListController {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;

  @Autowired
  public MetricSetPairListController(AccountCredentialsRepository accountCredentialsRepository, StorageServiceRepository storageServiceRepository) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.storageServiceRepository = storageServiceRepository;
  }

  @ApiOperation(value = "Retrieve a metric set pair list from object storage")
  @RequestMapping(value = "/{metricSetPairListId:.+}", method = RequestMethod.GET)
  public List<MetricSetPair> loadMetricSetPairList(@RequestParam(required = false) final String accountName,
                                                   @PathVariable final String metricSetPairListId) {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to read metric set pair list from bucket."));

    return storageService.loadObject(resolvedAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId);
  }

  @ApiOperation(value = "Retrieve a single metric set pair from a metricSetPairList from object storage")
  @RequestMapping(value = "/{metricSetPairListId:.+}/{metricSetPairId:.+}", method = RequestMethod.GET)
  public ResponseEntity<MetricSetPair> loadMetricSetPair(@RequestParam(required = false) final String accountName,
                                                         @PathVariable final String metricSetPairListId,
                                                         @PathVariable final String metricSetPairId) {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to read metric set pair list from bucket."));

    List<MetricSetPair> metricSetPairList = storageService.loadObject(resolvedAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId);
    Optional<MetricSetPair> foundPair = metricSetPairList.stream()
      .filter(metricSetPair -> metricSetPair.getId().equals(metricSetPairId))
      .findFirst();
    return foundPair.map(metricSetPair -> new ResponseEntity<>(metricSetPair, HttpStatus.OK))
      .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  @ApiOperation(value = "Write a metric set pair list to object storage")
  @RequestMapping(consumes = "application/json", method = RequestMethod.POST)
  public Map storeMetricSetPairList(@RequestParam(required = false) final String accountName,
                                    @RequestBody final List<MetricSetPair> metricSetPairList) throws IOException {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to write metric set pair list to bucket."));
    String metricSetPairListId = UUID.randomUUID() + "";

    storageService.storeObject(resolvedAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId, metricSetPairList);

    return Collections.singletonMap("metricSetPairListId", metricSetPairListId);
  }

  @ApiOperation(value = "Delete a metric set pair list")
  @RequestMapping(value = "/{metricSetPairListId:.+}", method = RequestMethod.DELETE)
  public void deleteMetricSetPairList(@RequestParam(required = false) final String accountName,
                                      @PathVariable final String metricSetPairListId,
                                      HttpServletResponse response) {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to delete metric set pair list."));

    storageService.deleteObject(resolvedAccountName, ObjectType.METRIC_SET_PAIR_LIST, metricSetPairListId);

    response.setStatus(HttpStatus.NO_CONTENT.value());
  }

  @ApiOperation(value = "Retrieve a list of metric set pair list ids and timestamps")
  @RequestMapping(method = RequestMethod.GET)
  public List<Map<String, Object>> listAllMetricSetPairLists(@RequestParam(required = false) final String accountName) {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to list all metric set pair lists."));

      return storageService.listObjectKeys(resolvedAccountName, ObjectType.METRIC_SET_PAIR_LIST);
  }
}
