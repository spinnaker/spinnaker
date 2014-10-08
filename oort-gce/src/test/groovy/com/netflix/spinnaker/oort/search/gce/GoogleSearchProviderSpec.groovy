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

import com.netflix.spinnaker.oort.model.gce.GoogleApplication
import com.netflix.spinnaker.oort.model.gce.GoogleCluster
import com.netflix.spinnaker.oort.model.gce.GoogleResourceRetriever
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class GoogleSearchProviderSpec extends Specification {
  private static final String ACCOUNT_NAME = "default"

  void "return applications with no types specified, ignores case"() {
    setup:
      def searchProvider = new GoogleSearchProvider()
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      searchProvider.googleResourceRetriever = resourceRetrieverMock
      def appMap = buildTestAppMap1()

    when:
      def resultSet = searchProvider.search('ROSCO', 1, 10)

    then:
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      resultSet.totalMatches == 2
      resultSet.pageNumber == 1
      resultSet.pageSize == 10
      resultSet.results == [[type: "applications",
                             application: "rosco_app_1",
                             url: "/applications/rosco_app_1"],
                            [type: "applications",
                             application: "rosco_app_2",
                             url: "/applications/rosco_app_2"]]
  }

  void "return applications with applications type specified"() {
    setup:
      def searchProvider = new GoogleSearchProvider()
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      searchProvider.googleResourceRetriever = resourceRetrieverMock
      def appMap = buildTestAppMap1()

    when:
      def resultSetNoTypes = searchProvider.search('rosco', 1, 10)
      def resultSetApplicationsType = searchProvider.search('rosco', ["applications"], 1, 10)

    then:
      2 * resourceRetrieverMock.getApplicationsMap() >> appMap
      resultSetNoTypes.totalMatches == resultSetApplicationsType.totalMatches
      resultSetNoTypes.pageNumber == resultSetApplicationsType.pageNumber
      resultSetNoTypes.pageSize == resultSetApplicationsType.pageSize
      resultSetNoTypes.results == resultSetApplicationsType.results
  }

  void 'respect user-specified size limit'() {
    setup:
      def searchProvider = new GoogleSearchProvider()
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      searchProvider.googleResourceRetriever = resourceRetrieverMock
      def appMap = buildTestAppMap1()

    when:
      def resultSetPage1 = searchProvider.search('rosco', 1, 1)
      def resultSetPage2 = searchProvider.search('rosco', 2, 1)

    then:
      2 * resourceRetrieverMock.getApplicationsMap() >> appMap
      resultSetPage1.totalMatches == 2
      resultSetPage1.pageNumber == 1
      resultSetPage1.pageSize == 1
      resultSetPage1.results == [[type: "applications",
                                 application: "rosco_app_1",
                                 url: "/applications/rosco_app_1"]]
      resultSetPage2.totalMatches == 2
      resultSetPage2.pageNumber == 2
      resultSetPage2.pageSize == 1
      resultSetPage2.results == [[type: "applications",
                                  application: "rosco_app_2",
                                  url: "/applications/rosco_app_2"]]
  }

  void 'search multiple types'() {
    setup:
      def searchProvider = new GoogleSearchProvider()
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      searchProvider.googleResourceRetriever = resourceRetrieverMock
      def appMap = buildTestAppMap2()

    when:
      def resultSet = searchProvider.search('rosco', ["applications", "clusters"], 1, 5)

    then:
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      resultSet.totalMatches == 5
      resultSet.pageNumber == 1
      resultSet.pageSize == 5
      resultSet.results == [[type: "applications",
                             application: "rosco_app_1",
                             url: "/applications/rosco_app_1"],
                            [type: "clusters",
                             application: "rosco_app_1",
                             account: ACCOUNT_NAME,
                             cluster: "rosco_app_1-cluster1",
                             url: "/applications/rosco_app_1/clusters/default/rosco_app_1-cluster1"],
                            [type: "applications",
                             application: "rosco_app_2",
                             url: "/applications/rosco_app_2"],
                            [type: "clusters",
                             application: "rosco_app_2",
                             account: ACCOUNT_NAME,
                             cluster: "rosco_app_2-cluster2a",
                             url: "/applications/rosco_app_2/clusters/default/rosco_app_2-cluster2a"],
                            [type: "clusters",
                             application: "rosco_app_2",
                             account: ACCOUNT_NAME,
                             cluster: "rosco_app_2-cluster2b",
                             url: "/applications/rosco_app_2/clusters/default/rosco_app_2-cluster2b"]]
  }

  void 'return empty list when page requested does not exist'() {
    setup:
      def searchProvider = new GoogleSearchProvider()
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      searchProvider.googleResourceRetriever = resourceRetrieverMock
      def appMap = buildTestAppMap1()

    when:
      def resultSet = searchProvider.search('rosco', 2, 5)

    then:
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      resultSet.totalMatches == 2
      resultSet.results == []

  }

  private HashMap<String, GoogleApplication> buildTestAppMap1() {
    def appMap = new HashMap<String, GoogleApplication>()

    def appName1 = "rosco_app_1"
    def app1 = new GoogleApplication(name: appName1)
    app1.clusterNames[ACCOUNT_NAME] = new HashSet<String>()
    app1.clusters[ACCOUNT_NAME] = new HashMap<String, GoogleCluster>()
    def cluster1 = new GoogleCluster(name: "cluster1", accountName: ACCOUNT_NAME)
    app1.clusters[ACCOUNT_NAME]["cluster1"] = cluster1
    appMap[appName1] = app1

    def appName2 = "rosco_app_2"
    def app2 = new GoogleApplication(name: appName2)
    app2.clusterNames[ACCOUNT_NAME] = new HashSet<String>()
    app2.clusters[ACCOUNT_NAME] = new HashMap<String, GoogleCluster>()
    def cluster2a = new GoogleCluster(name: "cluster2a", accountName: ACCOUNT_NAME)
    app2.clusters[ACCOUNT_NAME]["cluster2a"] = cluster2a
    def cluster2b = new GoogleCluster(name: "cluster2b", accountName: ACCOUNT_NAME)
    app2.clusters[ACCOUNT_NAME]["cluster2b"] = cluster2b
    appMap[appName2] = app2
    appMap
  }

  private HashMap<String, GoogleApplication> buildTestAppMap2() {
    def appMap = new HashMap<String, GoogleApplication>()

    def appName1 = "rosco_app_1"
    def app1 = new GoogleApplication(name: appName1)
    app1.clusterNames[ACCOUNT_NAME] = new HashSet<String>()
    app1.clusters[ACCOUNT_NAME] = new HashMap<String, GoogleCluster>()
    def cluster1 = new GoogleCluster(name: "rosco_app_1-cluster1", accountName: ACCOUNT_NAME)
    app1.clusters[ACCOUNT_NAME]["rosco_app_1-cluster1"] = cluster1
    appMap[appName1] = app1

    def appName2 = "rosco_app_2"
    def app2 = new GoogleApplication(name: appName2)
    app2.clusterNames[ACCOUNT_NAME] = new HashSet<String>()
    app2.clusters[ACCOUNT_NAME] = new HashMap<String, GoogleCluster>()
    def cluster2a = new GoogleCluster(name: "rosco_app_2-cluster2a", accountName: ACCOUNT_NAME)
    app2.clusters[ACCOUNT_NAME]["rosco_app_2-cluster2a"] = cluster2a
    def cluster2b = new GoogleCluster(name: "rosco_app_2-cluster2b", accountName: ACCOUNT_NAME)
    app2.clusters[ACCOUNT_NAME]["rosco_app_2-cluster2b"] = cluster2b
    appMap[appName2] = app2
    appMap
  }
}
