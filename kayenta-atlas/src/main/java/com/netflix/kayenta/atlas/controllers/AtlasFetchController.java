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

package com.netflix.kayenta.atlas.controllers;

import com.netflix.kayenta.atlas.query.AtlasQuery;
import com.netflix.kayenta.atlas.query.AtlasSynchronousQueryProcessor;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/fetch/atlas")
@Slf4j
public class AtlasFetchController {

  @Autowired
  AtlasSynchronousQueryProcessor atlasSynchronousQueryProcessor;

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public String queryMetrics(@RequestParam(required = false) final String metricsAccountName,
                             @RequestParam(required = false) final String storageAccountName,
                             @ApiParam(defaultValue = "name,CpuRawUser,:eq,:sum") @RequestParam String q,
                             @ApiParam(defaultValue = "cpu") @RequestParam String metricSetName,
                             @ApiParam(defaultValue = "cluster") @RequestParam String type,
                             @RequestParam String scope,
                             @ApiParam(defaultValue = "0") @RequestParam String start,
                             @ApiParam(defaultValue = "6000000") @RequestParam String end,
                             @ApiParam(defaultValue = "PT1M") @RequestParam String step) throws IOException {
    AtlasQuery atlasQuery =
      AtlasQuery
        .builder()
        .metricsAccountName(metricsAccountName)
        .storageAccountName(storageAccountName)
        .q(q)
        .metricSetName(metricSetName)
        .type(type)
        .scope(scope)
        .start(start)
        .end(end)
        .step(step)
        .build();

    return atlasSynchronousQueryProcessor.processQuery(atlasQuery);
  }
}
