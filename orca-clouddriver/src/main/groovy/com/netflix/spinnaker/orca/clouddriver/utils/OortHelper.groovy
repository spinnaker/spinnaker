/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.utils

import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.model.Cluster
import com.netflix.spinnaker.orca.clouddriver.model.Instance
import com.netflix.spinnaker.orca.clouddriver.model.Instance.InstanceInfo
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.stream.Collectors

/**
 * Helper methods for filtering Cluster/ASG/Instance information from Oort
 */
@Component
class OortHelper {

  private final CloudDriverService cloudDriverService

  @Autowired
  OortHelper(CloudDriverService cloudDriverService) {
    this.cloudDriverService = cloudDriverService
  }

  // TODO: failIfAnyInstancesUnhealthy seems to only be false in tasks that call this
  Map<String, Instance.InstanceInfo> getInstancesForCluster(Map<String, Object> context, String expectedAsgName, boolean expectOneAsg) {
    String region = context.region ?: context.source.region
    if (region == null) {
      throw new RuntimeException("unable to determine region")
    }

    // infer the app from the cluster prefix since this is used by quip and we want to be able to quick patch different apps from the same pipeline
    String app
    String clusterName
    if (expectedAsgName) {
      app = expectedAsgName.substring(0, expectedAsgName.indexOf("-"))
      clusterName = expectedAsgName.substring(0, expectedAsgName.lastIndexOf("-"))
    } else if (context.clusterName?.indexOf("-") > 0) {
      app = context.clusterName.substring(0, context.clusterName.indexOf("-"))
      clusterName = context.clusterName
    } else {
      app = context.clusterName
      clusterName = context.clusterName
    }

    String account = context.account
    String cloudProvider = context.cloudProvider ?: context.providerType ?: "aws"

    Cluster cluster = cloudDriverService.getCluster(app, account, clusterName, cloudProvider)

    if (!cluster || !cluster.serverGroups) {
      throw new RuntimeException("unable to find any server groups")
    }

    List<ServerGroup> asgsForCluster = cluster.serverGroups.findAll {
      it.region == region
    }

    ServerGroup searchAsg
    if (expectOneAsg) {
      if (asgsForCluster.size() != 1) {
        throw new RuntimeException("there is more than one server group in the cluster : ${clusterName}:${region}")
      }
      searchAsg = asgsForCluster.get(0)
    } else if (expectedAsgName) {
      searchAsg = asgsForCluster.findResult {
        if (it.name == expectedAsgName) {
          return it
        }
      }
      if (!searchAsg) {
        throw new RuntimeException("did not find the expected asg name : ${expectedAsgName}")
      }
    }

    Map<String, InstanceInfo> instanceMap = searchAsg.getInstances().stream()
        .collect(Collectors.toMap(
            { Instance it -> it.getInstanceId() },
            { Instance it -> it.instanceInfo() }))

    return instanceMap
  }
}
