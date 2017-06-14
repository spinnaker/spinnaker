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

import com.netflix.kayenta.metrics.MetricSet;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/metricSetList")
@Slf4j
public class MetricSetListController {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  StorageServiceRepository storageServiceRepository;

  @ApiOperation(value = "Retrieve a metric set list from object storage")
  @RequestMapping(value = "/{metricSetListId:.+}", method = RequestMethod.GET)
  public List<MetricSet> loadMetricSetList(@RequestParam(required = false) final String accountName,
                                           @PathVariable String metricSetListId) {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to read metric set list from bucket."));

    return storageService.loadObject(resolvedAccountName, ObjectType.METRIC_SET_LIST, metricSetListId);
  }

  @ApiOperation(value = "Write a metric set list to object storage")
  @RequestMapping(consumes = "application/context+json", method = RequestMethod.POST)
  public String storeMetricSetList(@RequestParam(required = false) final String accountName,
                                   @RequestBody List<MetricSet> metricSetList) throws IOException {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to write metric set list to bucket."));
    String metricSetListId = UUID.randomUUID() + "";

    storageService.storeObject(resolvedAccountName, ObjectType.METRIC_SET_LIST, metricSetListId, metricSetList);

    return metricSetListId;
  }

  @ApiOperation(value = "Delete a metric set list")
  @RequestMapping(value = "/{metricSetListId:.+}", method = RequestMethod.DELETE)
  public void deleteMetricSetList(@RequestParam(required = false) final String accountName,
                                  @PathVariable String metricSetListId,
                                  HttpServletResponse response) {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to delete metric set list."));

    storageService.deleteObject(resolvedAccountName, ObjectType.METRIC_SET_LIST, metricSetListId);

    response.setStatus(HttpStatus.NO_CONTENT.value());
  }

  @ApiOperation(value = "Retrieve a list of metric set list ids and timestamps")
  @RequestMapping(method = RequestMethod.GET)
  public List<Map<String, Object>> listAllMetricSetLists(@RequestParam(required = false) final String accountName) {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to list all metric set lists."));

    return storageService.listObjectKeys(resolvedAccountName, ObjectType.METRIC_SET_LIST);
  }
}
