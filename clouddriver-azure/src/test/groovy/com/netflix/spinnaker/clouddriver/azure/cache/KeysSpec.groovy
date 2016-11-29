/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.cache

import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import static com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys.Namespace.*

import spock.lang.Specification
import spock.lang.Unroll

class KeysSpec extends Specification {
  static final String PROVIDER = 'azure'
  static final String APP_NAME = 'app'
  static final String STACK_NAME = 'stack'
  static final String DETAIL = 'detail'
  static final String REGION = 'somewhere'
  static final String ACCOUNT = 'somebody'
  static final String SERVER_GROUP_NAME = [APP_NAME,STACK_NAME, DETAIL].join('-')
  static final String INSTANCE = [APP_NAME, STACK_NAME, DETAIL,].join('-') + '_0'
  static final String CLUSTER_NAME = [APP_NAME,STACK_NAME, DETAIL].join('-')
  static final String LOAD_BALANCER_NAME = [APP_NAME,STACK_NAME, DETAIL].join('-')
  static final String LOAD_BALANCER_ID = [APP_NAME,STACK_NAME, DETAIL].join('-') + 'ID'

  @Unroll
  def 'namespace string generation'(Keys.Namespace ns, String expected) {
    expect:
    ns.toString() == expected

    where:
    ns                   | expected
    AZURE_APPLICATIONS   | "azureApplications"
    AZURE_CLUSTERS       | "azureClusters"
    AZURE_LOAD_BALANCERS | "azureLoadBalancers"
    AZURE_SERVER_GROUPS  | "azureServerGroups"
    AZURE_INSTANCES      | "azureInstances"
    AZURE_VMIMAGES       | "azureVmimages"
    AZURE_CUSTOMVMIMAGES | "azureCustomvmimages"
    AZURE_NETWORKS       | "azureNetworks"
    AZURE_SUBNETS        | "azureSubnets"
    SECURITY_GROUPS      | "securityGroups"
  }

  def 'key parsing'() {
    expect:
    Keys.parse(AzureCloudProvider.ID, Keys.getApplicationKey(AzureCloudProvider.ID, APP_NAME)) == [provider: PROVIDER, type: AZURE_APPLICATIONS.ns, application: APP_NAME]
    Keys.parse(AzureCloudProvider.ID, Keys.getClusterKey(AzureCloudProvider.ID, APP_NAME, CLUSTER_NAME, ACCOUNT)) == [provider: PROVIDER, type: AZURE_CLUSTERS.ns, application: APP_NAME, name: CLUSTER_NAME, account: ACCOUNT, stack: STACK_NAME, detail: DETAIL]
    Keys.parse(AzureCloudProvider.ID, Keys.getServerGroupKey(AzureCloudProvider.ID, SERVER_GROUP_NAME, REGION, ACCOUNT)) == [provider: PROVIDER, type: AZURE_SERVER_GROUPS.ns, application: APP_NAME, serverGroup: SERVER_GROUP_NAME, account: ACCOUNT, region: REGION, detail: DETAIL, stack: STACK_NAME]
    Keys.parse(AzureCloudProvider.ID, Keys.getLoadBalancerKey(AzureCloudProvider.ID, LOAD_BALANCER_NAME , LOAD_BALANCER_ID, APP_NAME, CLUSTER_NAME, REGION, ACCOUNT )) == [provider: PROVIDER, type: AZURE_LOAD_BALANCERS.ns, application: APP_NAME, name: LOAD_BALANCER_NAME, id: LOAD_BALANCER_ID, cluster: CLUSTER_NAME, appname: APP_NAME, account: ACCOUNT, region: REGION]
    Keys.parse(AzureCloudProvider.ID, Keys.getInstanceKey(AzureCloudProvider.ID, SERVER_GROUP_NAME, INSTANCE, REGION, ACCOUNT)) == [provider: PROVIDER, type: AZURE_INSTANCES.ns, application: APP_NAME, serverGroup: SERVER_GROUP_NAME, name: INSTANCE, region: REGION, account: ACCOUNT]
  }
}
