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
import com.netflix.kayenta.service.MetricSetPairListService;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metricSetPairList")
@Slf4j
public class MetricSetPairListController {

  private final MetricSetPairListService metricSetPairListService;

  @Autowired
  public MetricSetPairListController(MetricSetPairListService metricSetPairListService) {
    this.metricSetPairListService = metricSetPairListService;
  }

  @Operation(summary = "Retrieve a metric set pair list from object storage")
  @RequestMapping(value = "/{metricSetPairListId:.+}", method = RequestMethod.GET)
  public List<MetricSetPair> loadMetricSetPairList(
      @RequestParam(required = false) final String accountName,
      @PathVariable final String metricSetPairListId) {
    return metricSetPairListService.loadMetricSetPairList(accountName, metricSetPairListId);
  }

  @Operation(
      summary = "Retrieve a single metric set pair from a metricSetPairList from object storage")
  @RequestMapping(
      value = "/{metricSetPairListId:.+}/{metricSetPairId:.+}",
      method = RequestMethod.GET)
  public ResponseEntity<MetricSetPair> loadMetricSetPair(
      @RequestParam(required = false) final String accountName,
      @PathVariable final String metricSetPairListId,
      @PathVariable final String metricSetPairId) {
    return metricSetPairListService
        .loadMetricSetPair(accountName, metricSetPairListId, metricSetPairId)
        .map(metricSetPair -> new ResponseEntity<>(metricSetPair, HttpStatus.OK))
        .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }

  @Operation(summary = "Write a metric set pair list to object storage")
  @RequestMapping(consumes = "application/json", method = RequestMethod.POST)
  public Map storeMetricSetPairList(
      @RequestParam(required = false) final String accountName,
      @RequestBody final List<MetricSetPair> metricSetPairList)
      throws IOException {
    String metricSetPairListId =
        metricSetPairListService.storeMetricSetPairList(accountName, metricSetPairList);

    return Collections.singletonMap("metricSetPairListId", metricSetPairListId);
  }

  @Operation(summary = "Delete a metric set pair list")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequestMapping(value = "/{metricSetPairListId:.+}", method = RequestMethod.DELETE)
  public void deleteMetricSetPairList(
      @RequestParam(required = false) final String accountName,
      @PathVariable final String metricSetPairListId) {
    metricSetPairListService.deleteMetricSetPairList(accountName, metricSetPairListId);
  }

  @Operation(summary = "Retrieve a list of metric set pair list ids and timestamps")
  @RequestMapping(method = RequestMethod.GET)
  public List<Map<String, Object>> listAllMetricSetPairLists(
      @RequestParam(required = false) final String accountName) {
    return metricSetPairListService.listAllMetricSetPairLists(accountName);
  }
}
