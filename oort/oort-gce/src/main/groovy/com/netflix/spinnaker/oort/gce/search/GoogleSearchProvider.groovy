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

package com.netflix.spinnaker.oort.gce.search

import com.netflix.frigga.Names
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.search.SearchProvider
import com.netflix.spinnaker.clouddriver.search.SearchResultSet
import com.netflix.spinnaker.oort.gce.model.GoogleInstance
import com.netflix.spinnaker.oort.gce.model.GoogleLoadBalancer
import com.netflix.spinnaker.oort.gce.model.GoogleResourceRetriever
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class GoogleSearchProvider implements SearchProvider {
  protected static final Logger log = Logger.getLogger(this)

  private static final String APPLICATIONS_TYPE = "applications"
  private static final String LOAD_BALANCERS_TYPE = "loadBalancers"
  private static final String CLUSTERS_TYPE = "clusters"
  private static final String SERVER_GROUPS_TYPE = "serverGroups"
  private static final String INSTANCES_TYPE = "instances"

  private static final List<String> DEFAULT_TYPES = [
    APPLICATIONS_TYPE,
    LOAD_BALANCERS_TYPE,
    CLUSTERS_TYPE,
    SERVER_GROUPS_TYPE,
    INSTANCES_TYPE
  ]

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  GoogleResourceRetriever googleResourceRetriever

  static SimpleTemplateEngine urlMappingTemplateEngine = new SimpleTemplateEngine()

  static Map<String, Template> urlMappings = [
    (SERVER_GROUPS_TYPE):
      urlMappingTemplateEngine.createTemplate('/applications/${application.toLowerCase()}/clusters/$account/$cluster/aws/serverGroups/$serverGroup?region=$region'),
    (CLUSTERS_TYPE):
      urlMappingTemplateEngine.createTemplate('/applications/${application.toLowerCase()}/clusters/$account/$cluster'),
    (APPLICATIONS_TYPE):
      urlMappingTemplateEngine.createTemplate('/applications/${application.toLowerCase()}'),
    (LOAD_BALANCERS_TYPE):
      urlMappingTemplateEngine.createTemplate('/gce/loadBalancers/$loadBalancer')
  ]

  String platform = 'gce'

  @Override
  SearchResultSet search(String query, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    // TODO(duftler): Take into account |filters|.
    search(query, DEFAULT_TYPES, pageNumber, pageSize)
  }

  @Override
  SearchResultSet search(String query, List<String> types, Integer pageNumber, Integer pageSize, Map<String, String> filters) {
    // TODO(duftler): Take into account |filters|.
    search(query, types, pageNumber, pageSize)
  }

  @Override
  SearchResultSet search(String query, Integer pageNumber, Integer pageSize) {
    search(query, DEFAULT_TYPES, pageNumber, pageSize)
  }

  private void findStandaloneInstanceMatches(String normalizedSearchTerm, List<Map<String, String>> matches) {
    Map<String, List<GoogleInstance>> standaloneInstanceMap = googleResourceRetriever.getStandaloneInstanceMap()
    List<Map<String, String>> standaloneInstanceMatches = []

    standaloneInstanceMap?.each { account, instanceList ->
      instanceList.each { instance ->
        if (instance.name.indexOf(normalizedSearchTerm) >= 0) {
          def localZoneName = instance.placement.availabilityZone
          def region = localZoneName.substring(0, localZoneName.lastIndexOf("-"))

          standaloneInstanceMatches << [type: INSTANCES_TYPE,
                                        account: account,
                                        region: region,
                                        instanceId: instance.name]
        }
      }
    }

    standaloneInstanceMatches = standaloneInstanceMatches.sort{ it.instanceId }

    matches.addAll(standaloneInstanceMatches)
  }

  private void findLoadBalancerMatches(String normalizedSearchTerm, List<Map<String, String>> matches) {
    Map<String, Map<String, List<String>>> networkLoadBalancerMap =
      googleResourceRetriever.getNetworkLoadBalancerMap()
    List<Map<String, String>> loadBalancerMatches = []

    networkLoadBalancerMap?.each() { account, regionMap ->
      regionMap.each() { region, loadBalancerList ->
        loadBalancerList.each { GoogleLoadBalancer loadBalancer ->
          if (loadBalancer.name.indexOf(normalizedSearchTerm) >= 0) {
            Names names = Names.parseName(loadBalancer.name)
            loadBalancerMatches << [type: LOAD_BALANCERS_TYPE,
                                    region: region,
                                    loadBalancer: loadBalancer.name,
                                    account: account,
                                    application: names.app,
                                    stack: names.stack,
                                    detail: names.detail]
          }
        }
      }
    }

    loadBalancerMatches = loadBalancerMatches.sort{ it.loadBalancer }

    matches.addAll(loadBalancerMatches)
  }

  @Override
  SearchResultSet search(String query, List<String> types, Integer pageNumber, Integer pageSize) {
    def matches = []
    def appMap = googleResourceRetriever.getApplicationsMap()

    for (def applicationName : appMap.keySet().sort()) {
      def googleApplication = appMap.get(applicationName)

      String normalizedSearchTerm = query.toLowerCase()

      if (types.contains(APPLICATIONS_TYPE) && applicationName.indexOf(normalizedSearchTerm) >= 0) {
        matches << [type: APPLICATIONS_TYPE, application: applicationName]
      }

      googleApplication.clusters.entrySet().each() {
        def clusterMap = it.value

        for (def clusterName : clusterMap.keySet().sort()) {
          def cluster = clusterMap.get(clusterName)

          if (types.contains(CLUSTERS_TYPE) && clusterName.indexOf(normalizedSearchTerm) >= 0) {
            matches << [type: CLUSTERS_TYPE,
                        application: applicationName,
                        account: cluster.accountName,
                        cluster: clusterName]
          }

          for (def serverGroup : cluster.serverGroups) {
            if (serverGroup.name.indexOf(normalizedSearchTerm) >= 0) {
              if (types.contains(SERVER_GROUPS_TYPE)) {
                def names = Names.parseName(serverGroup.name)
                matches << [type: SERVER_GROUPS_TYPE,
                            application: applicationName,
                            account: cluster.accountName,
                            cluster: clusterName,
                            region: serverGroup.region,
                            serverGroup: serverGroup.name,
                            stack: names.stack,
                            detail: names.detail,
                            sequence: names.sequence?.toString()]
              }

              if (types.contains(INSTANCES_TYPE)) {
                for (def instance : serverGroup.instances.sort { it.name }) {
                  matches << [type: INSTANCES_TYPE,
                              application: applicationName,
                              account: cluster.accountName,
                              cluster: clusterName,
                              region: serverGroup.region,
                              serverGroup: serverGroup.name,
                              instanceId: instance.name]
                }
              }
            } else if (types.contains(INSTANCES_TYPE)) {
              for (def instance : serverGroup.instances.sort { it.name }) {
                if (instance.name.indexOf(normalizedSearchTerm) >= 0) {
                  matches << [type: INSTANCES_TYPE,
                              application: applicationName,
                              account: cluster.accountName,
                              cluster: clusterName,
                              region: serverGroup.region,
                              serverGroup: serverGroup.name,
                              instanceId: instance.name]
                }
              }
            }
          }
        }
      }
    }

    if (types.contains(INSTANCES_TYPE)) {
      findStandaloneInstanceMatches(query, matches)
    }

    if (types.contains(LOAD_BALANCERS_TYPE)) {
      findLoadBalancerMatches(query, matches)
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
      result.provider = "gce"

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
