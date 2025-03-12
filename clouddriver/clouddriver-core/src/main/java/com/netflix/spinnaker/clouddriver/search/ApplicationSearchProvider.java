/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.search;

import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class ApplicationSearchProvider implements SearchProvider {

  private final String APPLICATIONS_TYPE = "applications";

  private final Front50Service front50Service;
  private final List<ClusterProvider> clusterProviders;
  private final FiatPermissionEvaluator permissionEvaluator;

  public ApplicationSearchProvider(
      Front50Service front50Service,
      List<ClusterProvider> clusterProviders,
      FiatPermissionEvaluator permissionEvaluator) {
    this.front50Service = front50Service;
    this.clusterProviders = clusterProviders;
    this.permissionEvaluator = permissionEvaluator;
  }

  public ApplicationSearchProvider(
      Front50Service front50Service, List<ClusterProvider> clusterProviders) {
    this(front50Service, clusterProviders, null);
  }

  public ApplicationSearchProvider(Front50Service front50Service) {
    this(front50Service, List.of(), null);
  }

  @Override
  public String getPlatform() {
    return "front50";
  }

  @Override
  public SearchResultSet search(String query, Integer pageNumber, Integer pageSize) {
    return search(query, List.of(APPLICATIONS_TYPE), pageNumber, pageSize, Map.of());
  }

  @Override
  public SearchResultSet search(
      String query, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    return search(query, List.of(APPLICATIONS_TYPE), pageNumber, pageSize, filters);
  }

  @Override
  public SearchResultSet search(
      String query, List<String> types, Integer pageNumber, Integer pageSize) {
    return search(query, types, pageNumber, pageSize, Map.of());
  }

  @Override
  public SearchResultSet search(
      String query,
      List<String> types,
      Integer pageNumber,
      Integer pageSize,
      Map<String, String> filters) {
    if (!types.contains(APPLICATIONS_TYPE)) {
      return SearchResultSet.builder().totalMatches(0).build();
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    List<Map<String, Object>> rawResults =
        Retrofit2SyncCall.execute(front50Service.searchByName(query, pageSize, filters));
    List<Map<String, Object>> results = new ArrayList<>();
    rawResults.forEach(
        application -> {
          String appName = application.get("name").toString().toLowerCase();
          if (permissionEvaluator != null
              && permissionEvaluator.hasPermission(auth, appName, "APPLICATION", "READ")) {
            application.put("application", appName);
            application.put("type", APPLICATIONS_TYPE);
            application.put("url", String.format("/applications/%s", appName));
            application.put("accounts", getAccounts(appName));

            results.add(application);
          }
        });

    return new SearchResultSet(results.size(), pageNumber, pageSize, getPlatform(), query, results);
  }

  private List<String> getAccounts(String application) {
    return clusterProviders.stream()
        .map(provider -> provider.getClusterSummaries(application))
        .map(Map::keySet)
        .map(Object::toString)
        .distinct()
        .collect(Collectors.toList());
  }

  @Override
  public List<String> excludedFilters() {
    return List.of("cloudProvider");
  }
}
