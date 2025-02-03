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
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProjectSearchProvider implements SearchProvider {

  private final String PROJECTS_TYPE = "projects";
  private final Front50Service front50Service;

  @Override
  public String getPlatform() {
    return "front50";
  }

  @Override
  public SearchResultSet search(String query, Integer pageNumber, Integer pageSize) {
    return search(query, List.of(PROJECTS_TYPE), pageNumber, pageSize, Map.of());
  }

  @Override
  public SearchResultSet search(
      String query, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    return search(query, List.of(PROJECTS_TYPE), pageNumber, pageSize, filters);
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
    if (!types.contains(PROJECTS_TYPE)) {
      return SearchResultSet.builder().totalMatches(0).build();
    }

    Map<String, String> allFilters = new HashMap<>(Map.of("name", query, "applications", query));
    allFilters.putAll(filters);

    List<Map<String, Object>> projects =
        Retrofit2SyncCall.execute(front50Service.searchForProjects(allFilters, pageSize));
    projects.forEach(
        project -> {
          project.put("type", PROJECTS_TYPE);
          project.put("url", String.format("/projects/%s", project.get("id")));
        });

    return new SearchResultSet(
        projects.size(), pageNumber, pageSize, getPlatform(), query, projects);
  }

  @Override
  public List<String> excludedFilters() {
    return List.of("cloudProvider");
  }
}
