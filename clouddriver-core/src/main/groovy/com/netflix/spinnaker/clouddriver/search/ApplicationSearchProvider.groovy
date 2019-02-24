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

package com.netflix.spinnaker.clouddriver.search

import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

@Canonical
class ApplicationSearchProvider implements SearchProvider {
  private final String APPLICATIONS_TYPE = "applications"

  Front50Service front50Service

  @Autowired
  List<ClusterProvider> clusterProviders

  @Autowired(required = false)
  FiatPermissionEvaluator permissionEvaluator

  @Override
  String getPlatform() {
    return "front50"
  }

  @Override
  SearchResultSet search(String query, Integer pageNumber, Integer pageSize) {
    return search(query, [APPLICATIONS_TYPE], pageNumber, pageSize, Collections.emptyMap())
  }

  @Override
  SearchResultSet search(String query, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    return search(query, [APPLICATIONS_TYPE], pageNumber, pageSize, filters)
  }

  @Override
  SearchResultSet search(String query, List<String> types, Integer pageNumber, Integer pageSize) {
    return search(query, types, pageNumber, pageSize, Collections.emptyMap())
  }

  @Override
  SearchResultSet search(String query, List<String> types,
                         Integer pageNumber,
                         Integer pageSize,
                         Map<String, String> filters) {
    if (!types.contains(APPLICATIONS_TYPE)) {
      return new SearchResultSet(totalMatches: 0)
    }

    Authentication auth = SecurityContextHolder.context.authentication

    def results = front50Service.searchByName(query, pageSize, filters).findResults {
      def application = it.name.toString().toLowerCase()
      if (permissionEvaluator && !permissionEvaluator.hasPermission(auth, application, 'APPLICATION', 'READ')) {
        return null
      }
      it.application = application
      it.type = APPLICATIONS_TYPE
      it.url = "/applications/${it.application}".toString()
      it.accounts = getAccounts(application)

      return it
    }
    return new SearchResultSet(results.size(), pageNumber, pageSize, getPlatform(), query, results)
  }

  private List<String> getAccounts(String application) {
    clusterProviders.findResults { it.getClusterSummaries(application) }
      .findResults { it.keySet() }
      .flatten()
      .unique()
  }

  @Override
  List<String> excludedFilters() {
    return ImmutableList.of("cloudProvider")
  }
}
