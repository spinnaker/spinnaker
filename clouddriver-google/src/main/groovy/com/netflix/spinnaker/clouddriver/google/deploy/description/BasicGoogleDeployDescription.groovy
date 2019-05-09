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

package com.netflix.spinnaker.clouddriver.google.deploy.description

import com.google.api.services.compute.model.AcceleratorConfig
import com.netflix.frigga.NameBuilder
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.BaseGoogleInstanceDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoHealingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleDistributionPolicy
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupsNameable
import groovy.transform.AutoClone
import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@AutoClone
@Canonical
@EqualsAndHashCode(callSuper = true)
@ToString(includeSuper = true, includeNames = true)
class BasicGoogleDeployDescription extends BaseGoogleInstanceDescription implements DeployDescription, ApplicationNameable {
  String application
  String stack
  String freeFormDetails
  Integer targetSize
  String region
  Boolean regional
  Boolean selectZones
  String zone
  List<String> loadBalancers
  Boolean disableTraffic
  Set<String> securityGroups
  GoogleAutoscalingPolicy autoscalingPolicy
  GoogleHttpLoadBalancingPolicy loadBalancingPolicy
  GoogleAutoHealingPolicy autoHealingPolicy
  Boolean overwriteAncestorAutoHealingPolicy = false
  /**
   * Optional explicit specification of zones for an RMIG.
   */
  GoogleDistributionPolicy distributionPolicy
  // Capacity is optional. If it is specified, capacity.desired takes precedence over targetSize.
  // If autoscalingPolicy is also specified, capacity.min and capacity.max take precedence over
  // autoscalingPolicy.minNumReplicas and autoscalingPolicy.maxNumReplicas.
  Capacity capacity
  Source source = new Source()
  String userData
  List<AcceleratorConfig> acceleratorConfigs

  @Override
  String getName() {
    def nameBuilder = new NameBuilder() {
      @Override
      protected String combineAppStackDetail(String appName, String stack, String detail) {
        return super.combineAppStackDetail(appName, stack, detail)
      }
    }
    nameBuilder.combineAppStackDetail(application, stack, freeFormDetails)
  }

  @Canonical
  @ToString(includeNames = true)
  static class AutoHealingPolicy {
    String healthCheck
    int initialDelaySec = 300
    FixedOrPercent maxUnavailable
  }

  @Canonical
  @ToString(includeNames = true)
  static class FixedOrPercent {
    Double fixed
    Double percent
    // This is only here so casting from the real FixedOrPercent model class works.
    Integer calculated
  }

  @Canonical
  static class Capacity {
    Integer min
    Integer max
    Integer desired
  }

  Collection<String> getApplications() {
    if (application) {
      return Collections.singletonList(application)
    }
    if (source && source.serverGroupName) {
      return Collections.singletonList(Names.parseName(source.serverGroupName).app)
    }
  }

  @Canonical
  static class Source implements ServerGroupsNameable {
    // TODO(duftler): Add accountName/credentials to support cloning from one account to another.
    String region
    String serverGroupName
    Boolean useSourceCapacity

    @Override
    Collection<String> getServerGroupNames() {
      return Collections.singletonList(serverGroupName)
    }
  }
}
