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

package com.netflix.spinnaker.kayenta.controllers;

import com.netflix.spinnaker.kayenta.metrics.MetricsService;
import com.netflix.spinnaker.kayenta.metrics.MetricsServiceRepository;
import com.netflix.spinnaker.kayenta.security.AccountCredentials;
import com.netflix.spinnaker.kayenta.security.AccountCredentialsRepository;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/fetch")
public class FetchController {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  MetricsServiceRepository metricsServiceRepository;

  @RequestMapping(method = RequestMethod.GET)
  public Map queryMetrics(@RequestParam(required = false) String accountName,
                          @ApiParam(defaultValue = "myapp-v010-") @RequestParam String instanceNamePrefix,
                          @ApiParam(defaultValue = "2017-01-24T15:13:00Z") @RequestParam String intervalStartTime,
                          @ApiParam(defaultValue = "2017-01-24T15:27:00Z") @RequestParam String intervalEndTime) throws IOException {
    AccountCredentials accountCredentials;

    if (StringUtils.hasLength(accountName)) {
      accountCredentials = accountCredentialsRepository.getOne(accountName);
    } else {
      accountCredentials = accountCredentialsRepository.getOne(AccountCredentials.Type.METRICS_STORE);

      if (accountCredentials != null) {
        accountName = accountCredentials.getName();
      }
    }

    if (accountCredentials != null) {
      MetricsService metricsService = metricsServiceRepository.getOne(accountName);

      if (metricsService != null) {
        return metricsService.queryMetrics(accountName, instanceNamePrefix, intervalStartTime, intervalEndTime);
      }
    }

    return null;
  }
}
