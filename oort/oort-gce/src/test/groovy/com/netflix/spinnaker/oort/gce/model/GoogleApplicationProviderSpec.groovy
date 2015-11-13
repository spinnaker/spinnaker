/*
 * Copyright 2015 Google, Inc.
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
import spock.lang.Specification

class GoogleApplicationProviderSpec extends Specification {
  private static final String ACCOUNT_NAME = "default"

  GoogleApplicationProvider applicationProvider

  Timer timer = Spectator.globalRegistry().timer('spec')

  def setup() {
    applicationProvider = new GoogleApplicationProvider()

    GoogleApplicationProvider.declaredFields.findAll { it.type == Timer }.each {
      applicationProvider.setProperty(it.name, timer)
    }
  }

  void "all applications are returned"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      applicationProvider.googleResourceRetriever = resourceRetrieverMock
      def appMap = new HashMap<String, GoogleApplication>()

      def appName1 = "rosco_app_1"
      def app1 = new GoogleApplication(name: appName1)
      app1.clusterNames[ACCOUNT_NAME] = new HashSet<String>()
      app1.clusters[ACCOUNT_NAME] = new HashMap<String, GoogleCluster>()
      appMap[appName1] = app1

      def appName2 = "rosco_app_2"
      def app2 = new GoogleApplication(name: appName2)
      app2.clusterNames[ACCOUNT_NAME] = new HashSet<String>()
      app2.clusters[ACCOUNT_NAME] = new HashMap<String, GoogleCluster>()
      appMap[appName2] = app2

    when:
      def resultSet = applicationProvider.getApplications()

    then:
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      resultSet.contains(app1)
      resultSet.contains(app2)
  }

  void "named application is returned"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      applicationProvider.googleResourceRetriever = resourceRetrieverMock
      def appMap = new HashMap<String, GoogleApplication>()

      def appName1 = "rosco_app_1"
      def app1 = new GoogleApplication(name: appName1)
      app1.clusterNames[ACCOUNT_NAME] = new HashSet<String>()
      app1.clusters[ACCOUNT_NAME] = new HashMap<String, GoogleCluster>()
      appMap[appName1] = app1

      def appName2 = "rosco_app_2"
      def app2 = new GoogleApplication(name: appName2)
      app2.clusterNames[ACCOUNT_NAME] = new HashSet<String>()
      app2.clusters[ACCOUNT_NAME] = new HashMap<String, GoogleCluster>()
      appMap[appName2] = app2

    when:
      def result1 = applicationProvider.getApplication("rosco_app_1")
      def result2 = applicationProvider.getApplication("rosco_app_2")

    then:
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      result1 == app1
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      result2 == app2
  }

  void "missing application returns null"() {
    setup:
      def resourceRetrieverMock = Mock(GoogleResourceRetriever)
      applicationProvider.googleResourceRetriever = resourceRetrieverMock
      def appMap = new HashMap<String, GoogleApplication>()

      def appName1 = "rosco_app_1"
      def app1 = new GoogleApplication(name: appName1)
      app1.clusterNames[ACCOUNT_NAME] = new HashSet<String>()
      app1.clusters[ACCOUNT_NAME] = new HashMap<String, GoogleCluster>()
      appMap[appName1] = app1

      def appName2 = "rosco_app_2"
      def app2 = new GoogleApplication(name: appName2)
      app2.clusterNames[ACCOUNT_NAME] = new HashSet<String>()
      app2.clusters[ACCOUNT_NAME] = new HashMap<String, GoogleCluster>()
      appMap[appName2] = app2

    when:
      def result = applicationProvider.getApplication("rosco_app_3")

    then:
      1 * resourceRetrieverMock.getApplicationsMap() >> appMap
      !result
  }
}
