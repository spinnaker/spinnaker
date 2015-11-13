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

import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import groovy.transform.Canonical

@Canonical
class ApplicationSearchProvider implements SearchProvider {
  private final String APPLICATIONS_TYPE = "applications"

  Front50Service front50Service

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
  SearchResultSet search(String query, List<String> types, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    if (!types.contains(APPLICATIONS_TYPE)) {
      return new SearchResultSet(totalMatches: 0)
    }

    // TODO-AJ Front50 v2 APIs should not be account-specific
    def account = front50Service.credentials.find { it.global == true }?.name as String
    def results = front50Service.searchByName(account, query).collect {
      it.application = it.name.toString().toLowerCase()
      it.type = APPLICATIONS_TYPE
      it.url = "/applications/${it.application}".toString()

      return it
    }
    return new SearchResultSet(results.size(), pageNumber, pageSize, getPlatform(), query, results)
  }
}
