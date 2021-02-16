/*
 * Copyright 2021 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.asg

import com.netflix.frigga.NameConstants
import com.netflix.frigga.NameValidation
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import groovy.transform.CompileStatic

@CompileStatic
class AWSServerGroupNameResolver extends AbstractServerGroupNameResolver {

  private static final String AWS_PHASE = "AWS_DEPLOY"
  private static final int DEFAULT_NEXT_SERVER_GROUP_ATTEMPTS = 5

  private final String accountName
  private final String region
  private final AsgService asgService
  private final Collection<ClusterProvider> clusterProviders
  private final int maxNextServerGroupAttempts

  AWSServerGroupNameResolver(String accountName,
                             String region,
                             AsgService asgService,
                             Collection<ClusterProvider> clusterProviders,
                             int maxNextServerGroupAttempts = DEFAULT_NEXT_SERVER_GROUP_ATTEMPTS) {
    this.accountName = accountName
    this.region = region
    this.asgService = asgService
    this.clusterProviders = clusterProviders
    this.maxNextServerGroupAttempts = maxNextServerGroupAttempts
  }

  @Override
  String getPhase() {
    return AWS_PHASE
  }

  @Override
  String getRegion() {
    return region
  }

  @Override
  List<AbstractServerGroupNameResolver.TakenSlot> getTakenSlots(String clusterName) {
    def cluster = awsCluster(clusterProviders, accountName, clusterName)
    if (!cluster) {
      return []
    }

    def serverGroupsInRegion = cluster.serverGroups.findAll { it.region == region && asgService.getAutoScalingGroup(it.name) }
    return serverGroupsInRegion.collect {
      new AbstractServerGroupNameResolver.TakenSlot(
        serverGroupName: it.name,
        sequence       : Names.parseName(it.name).sequence,
        createdTime    : new Date(it.createdTime)
      )
    }
  }

  @Override
  String resolveNextServerGroupName(String application, String stack, String details, Boolean ignoreSequence) {
    def clusterName = combineAppStackDetail(application, stack, details)

    if (!NameValidation.checkNameWithHyphen(clusterName)) {
      throw new IllegalArgumentException("Invalid cluster name: '${clusterName}'. Cluster names can only contain " +
        "characters in the following range: ${NameConstants.NAME_HYPHEN_CHARS}")
    }

    def originalNextServerGroupName = super.resolveNextServerGroupName(application, stack, details, ignoreSequence)
    def nextServerGroupName = originalNextServerGroupName

    if (nextServerGroupName) {
      def hasNextServerGroup = false
      def attempts = 0
      while (!hasNextServerGroup && attempts++ <= DEFAULT_NEXT_SERVER_GROUP_ATTEMPTS) {
        // this resolver uses cached data to determine the next server group name so we should verify it does not already
        // exist before blindly using it
        def nextServerGroup = asgService.getAutoScalingGroup(nextServerGroupName)
        if (!nextServerGroup) {
          hasNextServerGroup = true
          break
        }

        def nextSequence = generateNextSequence(nextServerGroupName)
        nextServerGroupName = generateServerGroupName(application, stack, details, nextSequence, false)
      }

      if (!hasNextServerGroup) {
        throw new IllegalArgumentException("All server group names for cluster ${clusterName} in ${region} are taken.")
      }
    }

    return nextServerGroupName
  }

  private static Cluster awsCluster(Collection<ClusterProvider> clusterProviders, String accountName, String clusterName) {
    Collection<Cluster> clusters = clusterProviders.collect {
      def application = Names.parseName(clusterName).app
      it.getCluster(application, accountName, clusterName)
    }
    clusters.removeAll([null])

    return clusters.find { it.type.equalsIgnoreCase("aws") }
  }
}
