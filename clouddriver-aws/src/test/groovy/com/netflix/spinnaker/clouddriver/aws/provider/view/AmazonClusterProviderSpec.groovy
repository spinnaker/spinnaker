/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import org.junit.jupiter.api.BeforeEach
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LAUNCH_CONFIGS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LAUNCH_TEMPLATES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS

class AmazonClusterProviderSpec extends Specification {
  def cacheView = Mock(Cache)
  def objectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def amazonCloudProvider = Mock(AmazonCloudProvider)
  def awsProvider = Mock(AwsProvider)

  @Subject
  def provider = new AmazonClusterProvider(amazonCloudProvider, cacheView, awsProvider)

  def app = "app"
  def account = "test"
  def clusterName = "app-main"
  String clusterId = Keys.getClusterKey(clusterName, app, account)
  def clusterAttributes = [name: clusterName, application: app]

  def serverGroupName = "app-main-v000"
  String serverGroupId = Keys.getServerGroupKey(clusterName, serverGroupName, account, "us-east-1")
  def serverGroup = [
    name: serverGroupName,
    instances: [],
    asg: [:]
  ]

  def launchTemplateName = "$serverGroupName-123"
  def launchConfigName = "$serverGroupName-123"

  @BeforeEach
  def setup() {
    serverGroup.asg.clear()
  }

  def "should resolve server group launch config"() {
    given:
    serverGroup.asg = [ launchConfigurationName: launchConfigName]
    def launchConfiguration = new DefaultCacheData(
      Keys.getLaunchConfigKey(launchConfigName, account, "us-east-1"), [ imageId: "ami-1"], [:])

    and:
    cacheView.getAll(LAUNCH_CONFIGS.ns, _ as Set) >> [launchConfiguration]
    cacheView.get(CLUSTERS.ns, clusterId) >> new DefaultCacheData(clusterId, clusterAttributes, [serverGroups: [serverGroupId]])
    cacheView.getAll(SERVER_GROUPS.ns, [ serverGroupId ], _ as CacheFilter) >> [
      new DefaultCacheData(serverGroupId, serverGroup, [launchConfigs: [launchConfiguration.id]])
    ]

    when:
    def result = provider.getCluster(app, account, clusterName)

    then:
    with(result) {
      def sg = serverGroups.first()
      type == "aws"
      name == clusterName
      accountName == account
      serverGroups.size() == 1
      sg.launchConfig == launchConfiguration.attributes
      sg.launchTemplate == null
    }
  }

  @Unroll
  def "should resolve server group launch template"() {
    given:
    serverGroup.asg = [
      launchTemplate: [
        launchTemplateName: launchTemplateName,
        version: asgLaunchTemplateVersion
      ]
    ]

    def defaultVersion = [
      launchTemplateName: launchTemplateName,
      versionNumber: 0,
      defaultVersion: true,
      launchTemplateData: [
        imageId: "ami-345"
      ]
    ]

    def latestVersion = [
      launchTemplateName: launchTemplateName,
      versionNumber: 1,
      defaultVersion: false,
      launchTemplateData: [
        imageId: "ami-123"
      ]
    ]

    def launchTemplate = new DefaultCacheData(
      Keys.getLaunchTemplateKey(launchTemplateName, account, "us-east-1"),
      [
        launchTemplateName: launchTemplateName,
        latestVersion: latestVersion,
        versions: [
          defaultVersion,
          latestVersion
        ]
      ], [:])

    and:
    cacheView.getAll(LAUNCH_TEMPLATES.ns, _ as Set) >> [launchTemplate]
    cacheView.get(CLUSTERS.ns, clusterId) >> new DefaultCacheData(clusterId, clusterAttributes, [serverGroups: [serverGroupId]])
    cacheView.getAll(SERVER_GROUPS.ns, [ serverGroupId ], _ as CacheFilter) >> [
      new DefaultCacheData(serverGroupId, serverGroup, [launchTemplates: [launchTemplate.id]])
    ]

    when:
    def result = provider.getCluster(app, account, clusterName)

    then:
    result.serverGroups.size() == 1
    result.serverGroups[0].launchConfig == null
    result.serverGroups[0].launchTemplate.versionNumber == resolvedVersion

    where:
    asgLaunchTemplateVersion | resolvedVersion
    '1'                      | 1
    '$Default'               | 0
    '$Latest'                | 1
  }
}
