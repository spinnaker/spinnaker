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
package com.netflix.spinnaker.clouddriver.aws.services

import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.*
import com.google.common.collect.Iterables
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.aws.model.AwsResultsRetriever
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import groovy.transform.Canonical

@Canonical
class AsgService {

  final AutoScalingClient amazonAutoScaling

  void suspendProcesses(String asgName, Collection<AutoScalingProcessType> processes) {
    def request = SuspendProcessesRequest.builder().scalingProcesses(processes*.name()).autoScalingGroupName(asgName).build()
    amazonAutoScaling.suspendProcesses(request)
  }

  void resumeProcesses(String asgName, Collection<AutoScalingProcessType> processes) {
    def request = ResumeProcessesRequest.builder().scalingProcesses(processes*.name()).autoScalingGroupName(asgName).build()
    amazonAutoScaling.resumeProcesses(request)
  }

  void putWarmPool(String asgName, Integer minSize, Integer maxGroupPreparedCapacity, String poolState, Boolean reuseOnScaleIn) {
    def request = PutWarmPoolRequest.builder()
      .autoScalingGroupName(asgName)
      .minSize(minSize)
      .maxGroupPreparedCapacity(maxGroupPreparedCapacity)
      .poolState(poolState)
      .instanceReusePolicy(InstanceReusePolicy.builder().reuseOnScaleIn(reuseOnScaleIn).build())
      .build()
    amazonAutoScaling.putWarmPool(request)
  }

  void deleteWarmPool(String asgName, Boolean forceDelete = false) {
    def request = DeleteWarmPoolRequest.builder().autoScalingGroupName(asgName).forceDelete(forceDelete).build()
    amazonAutoScaling.deleteWarmPool(request)
  }

  AutoScalingGroup getAutoScalingGroup(String asgName) {
    Iterables.getOnlyElement(getAutoScalingGroups([asgName]), null)
  }

  List<AutoScalingGroup> getAutoScalingGroups(Collection<String> asgNames) {
    def retriever = new AwsResultsRetriever<AutoScalingGroup, DescribeAutoScalingGroupsRequest, DescribeAutoScalingGroupsResponse>() {
      @Override
      protected DescribeAutoScalingGroupsResponse makeRequest(DescribeAutoScalingGroupsRequest request) {
        amazonAutoScaling.describeAutoScalingGroups(request)
      }

      @Override
      protected List<AutoScalingGroup> accessResult(DescribeAutoScalingGroupsResponse result) {
        result.autoScalingGroups()
      }

      @Override
      protected DescribeAutoScalingGroupsRequest setNextToken(DescribeAutoScalingGroupsRequest request, String nextToken) {
        request.toBuilder().nextToken(nextToken).build()
      }
    }
    def request = DescribeAutoScalingGroupsRequest.builder().autoScalingGroupNames(asgNames).build()
    retriever.retrieve(request)
  }

  LaunchConfiguration getLaunchConfiguration(String launchConfigurationName) {
    Iterables.getOnlyElement(getLaunchConfigurations([launchConfigurationName]), null)
  }

  List<LaunchConfiguration> getLaunchConfigurations(Collection<String> launchConfigurationNames) {
    def retriever = new AwsResultsRetriever<LaunchConfiguration, DescribeLaunchConfigurationsRequest, DescribeLaunchConfigurationsResponse>() {
      @Override
      protected DescribeLaunchConfigurationsResponse makeRequest(DescribeLaunchConfigurationsRequest request) {
        amazonAutoScaling.describeLaunchConfigurations(request)
      }

      @Override
      protected List<LaunchConfiguration> accessResult(DescribeLaunchConfigurationsResponse result) {
        result.launchConfigurations()
      }

      @Override
      protected DescribeLaunchConfigurationsRequest setNextToken(DescribeLaunchConfigurationsRequest request, String nextToken) {
        request.toBuilder().nextToken(nextToken).build()
      }
    }
    retriever.retrieve(DescribeLaunchConfigurationsRequest.builder().launchConfigurationNames(launchConfigurationNames).build())
  }
}
