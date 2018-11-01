/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.*
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ApplicationControllerSpec extends Specification {

  @Shared
  ApplicationsController applicationsController

  def setup() {
    applicationsController = new ApplicationsController(requestQueue: RequestQueue.noop())
  }

  def "call all application providers on listing"() {
    setup:
    def appProvider1 = Mock(ApplicationProvider)
    def appProvider2 = Mock(ApplicationProvider)
    applicationsController.applicationProviders = [appProvider1, appProvider2]

    when:
    applicationsController.list(false /*expand*/, true /*restricted*/)

    then:
    1 * appProvider1.getApplications(false)
    1 * appProvider2.getApplications(false)
  }

  def "merge clusterNames and attributes when multiple apps are found"() {
    setup:
    def appProvider1 = Mock(ApplicationProvider)
    def appProvider2 = Mock(ApplicationProvider)
    def cluProvider1 = Mock(ClusterProvider)
    applicationsController.applicationProviders = [appProvider1, appProvider2]
    applicationsController.clusterProviders = [cluProvider1]
    def app1 = [getName: { "foo" }, getClusterNames: { [test: ["bar"] as Set] }, getAttributes: { [tag: "val"] }] as Application
    def app2 = [getName: { "foo" }, getClusterNames: { [test: ["baz"] as Set] }, getAttributes: { [:] }] as Application
    def cluster = Mock(Cluster)
    cluster.getAccountName() >> "test"
    cluster.getName() >> "foo"
    cluster.getLoadBalancers() >> []
    cluster.getType() >> "aws"
    def sg1 = Mock(ServerGroup)
    sg1.getName() >> "bar"
    def sg2 = Mock(ServerGroup)
    sg2.getName() >> "baz"
    cluster.getServerGroups() >> [sg1, sg2]

    when:
    def result = applicationsController.get("foo")

    then:
    2 * cluProvider1.getClusterSummaries("foo") >> [test: cluster]
    1 * appProvider1.getApplication("foo") >> app1
    1 * appProvider2.getApplication("foo") >> app2
    result.name == "foo"
    result.clusters.test*.serverGroups.flatten() == ["bar", "baz"]
    result.attributes == [tag: "val", cloudProviders: "aws"]
  }

  def "prune nulls when subset of application providers find app"() {
    setup:
    def appProvider1 = Mock(ApplicationProvider)
    def appProvider2 = Mock(ApplicationProvider)
    def cluProvider1 = Mock(ClusterProvider)
    applicationsController.applicationProviders = [appProvider1, appProvider2]
    applicationsController.clusterProviders = [cluProvider1]
    def app1 = [getName: { "foo" }, getClusterNames: { [test: ["bar"] as Set] }, getAttributes: { [tag: "val"] }] as Application
    def cluster = Mock(Cluster)
    cluster.getAccountName() >> "test"
    cluster.getName() >> "foo"
    cluster.getType() >> "aws"
    cluster.getLoadBalancers() >> []
    def sg1 = Mock(ServerGroup)
    sg1.getName() >> "bar"
    cluster.getServerGroups() >> [sg1]

    when:
    def result = applicationsController.get("foo")

    then:
    1 * cluProvider1.getClusterSummaries("foo") >> [test: cluster]
    1 * appProvider1.getApplication("foo") >> app1
    1 * appProvider2.getApplication("foo") >> null
    result.name == "foo"
    result.clusters.test*.serverGroups.flatten() == ["bar"]
    result.attributes == [tag: "val", cloudProviders: "aws"]
  }

  def "throw NotFoundException when no apps are found"() {
    setup:
    def appProvider1 = Mock(ApplicationProvider)
    def appProvider2 = Mock(ApplicationProvider)
    applicationsController.applicationProviders = [appProvider1, appProvider2]

    when:
    def result = applicationsController.get("foo")

    then:
    1 * appProvider1.getApplication("foo") >> null
    1 * appProvider2.getApplication("foo") >> null
    NotFoundException e = thrown()
    e.message == "Application does not exist (name: foo)"
  }

  @Unroll
  def "provide cloudProviders field correctly based on clusters"() {
    setup:
    def appProvider = Mock(ApplicationProvider)
    def cluProvider = Mock(ClusterProvider)
    applicationsController.applicationProviders = [appProvider]
    applicationsController.clusterProviders = [cluProvider]
    def app1 = [getName: { "foo" }, getClusterNames: { [test: ["bar", "baz"] as Set] }, getAttributes: { [tag: "val"] }] as Application
    def cluster = Mock(Cluster)
    cluster.getAccountName() >> "test"
    cluster.getName() >> "bar"
    cluster.getType() >> cloudProvider1
    cluster.getLoadBalancers() >> []
    cluster.getServerGroups() >> []
    def cluster1 = Mock(Cluster)
    cluster1.getAccountName() >> "test"
    cluster1.getName() >> "baz"
    cluster1.getType() >> cloudProvider2
    cluster1.getLoadBalancers() >> []
    cluster1.getServerGroups() >> []

    when:
    def result = applicationsController.get("foo")

    then:
    1 * cluProvider.getClusterSummaries("foo") >> [test: [cluster, cluster1]]
    1 * appProvider.getApplication("foo") >> app1
    result.attributes.cloudProviders == expectedCloudProviders

    where:
    cloudProvider1 | cloudProvider2 || expectedCloudProviders
    "aws"          | "titus"        || "aws,titus"
    "aws"          | "aws"          || "aws"
  }
}
