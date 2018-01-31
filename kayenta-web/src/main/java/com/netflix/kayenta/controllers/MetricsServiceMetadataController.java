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

package com.netflix.kayenta.controllers;

import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.metrics.MetricsServiceRepository;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/metadata/metricsService")
@Slf4j
public class MetricsServiceMetadataController {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final MetricsServiceRepository metricsServiceRepository;

  @Autowired
  public MetricsServiceMetadataController(AccountCredentialsRepository accountCredentialsRepository,
                                          MetricsServiceRepository metricsServiceRepository) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.metricsServiceRepository = metricsServiceRepository;
  }

  @ApiOperation(value = "Retrieve a list of descriptors for use in populating the canary config ui")
  @RequestMapping(method = RequestMethod.GET)
  public List<Map> listMetadata(@RequestParam(required = false) final String metricsAccountName,
                                @RequestParam(required = false) final String filter) throws IOException {
    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
                                                                                     AccountCredentials.Type.METRICS_STORE,
                                                                                     accountCredentialsRepository);
    MetricsService metricsService =
      metricsServiceRepository
        .getOne(resolvedMetricsAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No metrics service was configured; unable to read from metrics store."));
    List<Map> matchingDescriptors = metricsService.getMetadata(resolvedMetricsAccountName, filter);

    if (StringUtils.isEmpty(filter)) {
      log.debug("Returned all {} descriptors via account {}.", matchingDescriptors.size(), resolvedMetricsAccountName, filter);
    } else {
      log.debug("Matched {} descriptors via account {} using filter '{}'.", matchingDescriptors.size(), resolvedMetricsAccountName, filter);
    }

    return metricsService.getMetadata(resolvedMetricsAccountName, filter);
  }
}
