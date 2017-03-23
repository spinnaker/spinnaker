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

package com.netflix.kayenta.atlas.metrics;

import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.atlas.security.AtlasNamedAccountCredentials;
import com.netflix.kayenta.atlas.service.AtlasRemoteService;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Builder
public class AtlasMetricsService implements MetricsService {

  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  // These are still placeholder arguments. Each metrics service will have its own set of required/optional arguments. The return type is a placeholder as well.
  public Optional<Map> queryMetrics(String accountName, String instanceNamePrefix, String intervalStartTime, String intervalEndTime) throws IOException {
    AtlasNamedAccountCredentials credentials = (AtlasNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
    AtlasRemoteService atlasRemoteService = credentials.getAtlasRemoteService();
    AtlasResults atlasResults = atlasRemoteService.fetch("name,randomValue,:eq,:sum,(,name,),:by", "std.json");

    System.out.println("** Got back from fetch: atlasResults=" + atlasResults);

    return Optional.of(Collections.singletonMap("some-key", "some-value"));
  }
}
