/*
 * Copyright 2018 Netflix, Inc.
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

import static com.netflix.kayenta.security.AccountCredentials.Type.METRICS_STORE;

import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metricsServices")
public class MetricsServicesController {

  private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  public MetricsServicesController(AccountCredentialsRepository accountCredentialsRepository) {
    this.accountCredentialsRepository = accountCredentialsRepository;
  }

  @Operation(summary = "Retrieve a list of all configured metrics services")
  @RequestMapping(method = RequestMethod.GET)
  List<MetricsServiceDetail> list() {
    Set<AccountCredentials> metricAccountCredentials =
        accountCredentialsRepository.getAllOf(METRICS_STORE);

    return metricAccountCredentials.stream()
        .map(
            account ->
                MetricsServiceDetail.builder()
                    .name(account.getName())
                    .type(account.getType())
                    .locations(account.getLocations())
                    .recommendedLocations(account.getRecommendedLocations())
                    .build())
        .collect(Collectors.toList());
  }
}
