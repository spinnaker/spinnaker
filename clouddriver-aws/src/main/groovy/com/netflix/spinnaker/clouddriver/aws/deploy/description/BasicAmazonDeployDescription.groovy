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

package com.netflix.spinnaker.clouddriver.aws.deploy.description

import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.orchestration.events.OperationEvent
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class BasicAmazonDeployDescription extends AbstractAmazonCredentialsDescription implements DeployDescription, ApplicationNameable {
  String application
  String amiName
  String stack
  String freeFormDetails
  String instanceType
  String subnetType
  List<String> subnetIds
  String iamRole
  String keyPair
  Boolean associatePublicIpAddress
  Integer cooldown
  Collection<String> enabledMetrics
  Integer healthCheckGracePeriod
  String healthCheckType
  String spotPrice
  Set<String> suspendedProcesses = []
  Collection<String> terminationPolicies
  String kernelId
  String ramdiskId
  Boolean instanceMonitoring
  Boolean ebsOptimized
  String base64UserData
  Boolean legacyUdf

  Collection<OperationEvent> events = []


  //Default behaviour (legacy reasons) is to carry forward some settings (even on a deploy vs a cloneServerGroup) from an ancestor ASG
  // the following flags disable copying of those attributes:

  /**
   * If false, the newly created server group will not pick up scaling policies and actions from an ancestor group
   */
  boolean copySourceScalingPoliciesAndActions = true

  /**
   * If false, the newly created server group will not pick up block device mapping customizations from an ancestor group
   */
  boolean copySourceCustomBlockDeviceMappings = true

  /**
   * If false, the newly created server group will not pick up overridden subnet ids from an ancestor group
   */
  boolean copySourceSubnetIdOverrides = true

  String classicLinkVpcId
  List<String> classicLinkVpcSecurityGroups

  /**
   * If specified, this sequence number will be used when generating the server group name.
   *
   * Expectation is on the caller to ensure that an explicitly provided sequence number is not already in use.
   */
  Integer sequence

  boolean ignoreSequence
  boolean startDisabled
  boolean includeAccountLifecycleHooks = true

  List<AmazonBlockDevice> blockDevices
  Boolean useAmiBlockDeviceMappings
  List<String> loadBalancers
  List<String> targetGroups
  List<String> securityGroups
  List<AmazonAsgLifecycleHook> lifecycleHooks = []
  Map<String, List<String>> availabilityZones = [:]
  Capacity capacity = new Capacity()
  Source source = new Source()
  Map<String, String> tags

  @Override
  Collection<String> getApplications() {
    return [application]
  }

  @Canonical
  static class Capacity {
    Integer min
    Integer max
    Integer desired
  }

  @Canonical
  static class Source {
    String account
    String region
    String asgName
    Boolean useSourceCapacity
  }
}
