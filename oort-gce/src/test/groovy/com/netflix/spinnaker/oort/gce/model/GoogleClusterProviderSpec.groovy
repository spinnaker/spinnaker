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

package com.netflix.spinnaker.oort.gce.model

import com.netflix.spectator.api.Spectator
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.oort.gce.model.GoogleApplication
import com.netflix.spinnaker.oort.gce.model.GoogleCluster
import com.netflix.spinnaker.oort.gce.model.GoogleClusterProvider
import com.netflix.spinnaker.oort.gce.model.GoogleResourceRetriever
import spock.lang.Specification

class GoogleClusterProviderSpec extends Specification {
  private static final String ACCOUNT_NAME = "default"

  GoogleClusterProvider clusterProvider

  Timer timer = Spectator.registry().timer('spec')

  def setup() {
    clusterProvider = new GoogleClusterProvider()

    GoogleClusterProvider.declaredFields.findAll { it.type == Timer }.each {
      clusterProvider.setProperty(it.name, timer)
    }
  }

  void "cluster is returned by specifying application, account and cluster"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      clusterProvider.googleResourceRetriever = resourceRetrieverMock
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

    when:
      def result1 = clusterProvider.getCluster("rosco_app_1", ACCOUNT_NAME, "cluster1")
      def result2a = clusterProvider.getCluster("rosco_app_2", ACCOUNT_NAME, "cluster2a")
      def result2b = clusterProvider.getCluster("rosco_app_2", ACCOUNT_NAME, "cluster2b")

    then:
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      result1 == cluster1
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      result2a == cluster2a
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      result2b == cluster2b
  }

  void "clusters are returned by specifying application and account"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      clusterProvider.googleResourceRetriever = resourceRetrieverMock
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

    when:
      def resultSet1 = clusterProvider.getClusters("rosco_app_1", ACCOUNT_NAME)
      def resultSet2 = clusterProvider.getClusters("rosco_app_2", ACCOUNT_NAME)

    then:
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      resultSet1 == [cluster1] as Set
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      resultSet2 == [cluster2a, cluster2b] as Set
  }

  void "clusters are returned by specifying application, and are keyed by account"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      clusterProvider.googleResourceRetriever = resourceRetrieverMock
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

    when:
      def resultMap1 = clusterProvider.getClusterDetails("rosco_app_1")
      def resultMap2 = clusterProvider.getClusterDetails("rosco_app_2")

    then:
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      resultMap1.containsKey(ACCOUNT_NAME)
      resultMap1[ACCOUNT_NAME] == [cluster1] as Set
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      resultMap2.containsKey(ACCOUNT_NAME)
      resultMap2[ACCOUNT_NAME] == [cluster2a, cluster2b] as Set
  }

  void "all clusters are returned, and are keyed by account"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      clusterProvider.googleResourceRetriever = resourceRetrieverMock
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

    when:
      def resultMap = clusterProvider.getClusters()

    then:
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      resultMap.containsKey(ACCOUNT_NAME)
      resultMap[ACCOUNT_NAME] == [cluster1, cluster2a, cluster2b] as Set
  }
}
