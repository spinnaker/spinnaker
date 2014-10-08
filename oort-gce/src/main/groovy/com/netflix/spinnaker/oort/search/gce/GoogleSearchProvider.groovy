/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.oort.search.gce

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.oort.model.gce.GoogleResourceRetriever
import com.netflix.spinnaker.oort.search.SearchProvider
import com.netflix.spinnaker.oort.search.SearchResultSet
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

@Component
class GoogleSearchProvider implements SearchProvider {
  protected static final Logger log = Logger.getLogger(this)

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  static SimpleTemplateEngine urlMappingTemplateEngine = new SimpleTemplateEngine()

  static Map<String, Template> urlMappings = [
    ("serverGroups"):
      urlMappingTemplateEngine.createTemplate('/applications/${application.toLowerCase()}/clusters/$account/$cluster/aws/serverGroups/$serverGroup?region=$region'),
    ("clusters"):
      urlMappingTemplateEngine.createTemplate('/applications/${application.toLowerCase()}/clusters/$account/$cluster'),
    ("applications"):
      urlMappingTemplateEngine.createTemplate('/applications/${application.toLowerCase()}')
  ]

  String platform = 'gce'

  GoogleResourceRetriever googleResourceRetriever

  @PostConstruct
  void init() {
    googleResourceRetriever = new GoogleResourceRetriever()
    googleResourceRetriever.init(accountCredentialsProvider)
  }

  @Override
  SearchResultSet search(String query, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    // TODO(duftler): Take into account |filters|.
    search(query, ["applications", "clusters", "serverGroupInstances", "serverGroups"], pageNumber, pageSize)
  }

  @Override
  SearchResultSet search(String query, List<String> types, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    // TODO(duftler): Take into account |filters|.
    search(query, types, pageNumber, pageSize)
  }

  @Override
  SearchResultSet search(String query, Integer pageNumber, Integer pageSize) {
    search(query, ["applications", "clusters", "serverGroupInstances", "serverGroups"], pageNumber, pageSize)
  }

  @Override
  SearchResultSet search(String query, List<String> types, Integer pageNumber, Integer pageSize) {
    def matches = []
    def appMap = googleResourceRetriever.getApplicationsMap()

    for (def applicationName : appMap.keySet().sort()) {
      def googleApplication = appMap.get(applicationName)

      String normalizedSearchTerm = query.toLowerCase()

      if (types.contains("applications") && applicationName.indexOf(normalizedSearchTerm) >= 0) {
        matches << [type: "applications", application: applicationName]
      }

      googleApplication.clusters.entrySet().each() {
        def clusterMap = it.value

        for (def clusterName : clusterMap.keySet().sort()) {
          def cluster = clusterMap.get(clusterName)

          if (types.contains("clusters") && clusterName.indexOf(normalizedSearchTerm) >= 0) {
            matches << [type: "clusters", application: applicationName, account: cluster.accountName, cluster: clusterName]
          }

          for (def serverGroup : cluster.serverGroups) {
            if (serverGroup.name.indexOf(normalizedSearchTerm) >= 0) {
              if (types.contains("serverGroups")) {
                // TODO(duftler): What about stack/detail/sequence?
                matches << [type: "serverGroups", application: applicationName, account: cluster.accountName, cluster: clusterName, region: serverGroup.region, serverGroup: serverGroup.name]
              }

              if (types.contains("serverGroupInstances")) {
                for (def instance : serverGroup.instances.sort { it.name }) {
                  matches << [type: "serverGroupInstances", application: applicationName, account: cluster.accountName, cluster: clusterName, region: serverGroup.region, serverGroup: serverGroup.name, instanceId: instance.name]
                }
              }
            } else if (types.contains("serverGroupInstances")) {
              for (def instance : serverGroup.instances.sort { it.name }) {
                if (instance.name.indexOf(normalizedSearchTerm) >= 0) {
                  matches << [type: "serverGroupInstances", application: applicationName, account: cluster.accountName, cluster: clusterName, region: serverGroup.region, serverGroup: serverGroup.name, instanceId: instance.name]
                }
              }
            }
          }
        }
      }
    }

    def paginatedResults = paginateResults(matches, pageSize, pageNumber)

    SearchResultSet resultSet = new SearchResultSet(
      totalMatches: matches.size(),
      platform: 'gce',
      query: query,
      pageNumber: pageNumber,
      pageSize: pageSize,
      results: paginatedResults
    )

    resultSet.results.each { Map<String, String> result ->
      if (urlMappings.containsKey(result.type)) {
        def binding = [:]
        binding.putAll(result)
        result.url = urlMappings[result.type].make(binding).toString()
      }
    }

    resultSet
  }

  private static List<Map<String, String>> paginateResults(List<Map<String, String>> matches, Integer pageSize, Integer pageNumber) {
    log.info("Paginating ${matches.size()} results; page number: ${pageNumber}, items per page: ${pageSize}")
    Integer startingIndex = pageSize * (pageNumber - 1)
    Integer endIndex = Math.min(pageSize * pageNumber, matches.size())
    boolean hasResults = startingIndex < endIndex
    List<Map<String, String>> toReturn = hasResults ? matches[startingIndex..endIndex - 1] : new ArrayList<Map<String, String>>()
    toReturn
  }
}
