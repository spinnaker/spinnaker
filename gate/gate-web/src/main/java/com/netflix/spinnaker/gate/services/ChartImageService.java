/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChartImageService {

  @Autowired private ClouddriverServiceSelector clouddriverServiceSelector;

  @Autowired private ProviderLookupService providerLookupService;

  public List<Map> getForAccountAndRegion(
      String provider, String account, String region, String imageId, String selectorKey) {
    return Retrofit2SyncCall.execute(
        clouddriverServiceSelector
            .select()
            .getChartImageDetails(provider, account, region, imageId));
  }

  public List<Map> search(
      String provider,
      String query,
      String region,
      String account,
      Integer count,
      Map<String, String> additionalFilters,
      String selectorKey) {
    return Retrofit2SyncCall.execute(
        clouddriverServiceSelector
            .select()
            .findChartImages(provider, query, region, account, count, additionalFilters));
  }

  public List<String> findTags(
      String provider, String account, String repository, String selectorKey) {
    return Retrofit2SyncCall.execute(
        clouddriverServiceSelector.select().findChartTags(provider, account, repository));
  }
}
