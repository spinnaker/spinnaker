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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.*
import com.google.common.collect.Iterables
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.model.AutoScalingProcessType
import com.netflix.spinnaker.clouddriver.aws.model.AwsResultsRetriever
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver
import groovy.transform.Canonical

@Canonical
class AsgService {

  final AmazonAutoScaling amazonAutoScaling

  void suspendProcesses(String asgName, Collection<AutoScalingProcessType> processes) {
    def request = new SuspendProcessesRequest(scalingProcesses: processes*.name(), autoScalingGroupName: asgName)
    amazonAutoScaling.suspendProcesses(request)
  }

  void resumeProcesses(String asgName, Collection<AutoScalingProcessType> processes) {
    def request = new ResumeProcessesRequest(scalingProcesses: processes*.name(), autoScalingGroupName: asgName)
    amazonAutoScaling.resumeProcesses(request)
  }

  AutoScalingGroup getAutoScalingGroup(String asgName) {
    Iterables.getOnlyElement(getAutoScalingGroups([asgName]), null)
  }

  List<AutoScalingGroup> getAutoScalingGroups(Collection<String> asgNames) {
    def retriever = new AwsResultsRetriever<AutoScalingGroup, DescribeAutoScalingGroupsRequest, DescribeAutoScalingGroupsResult>() {
      @Override
      protected DescribeAutoScalingGroupsResult makeRequest(DescribeAutoScalingGroupsRequest request) {
        amazonAutoScaling.describeAutoScalingGroups(request)
      }

      @Override
      protected List<AutoScalingGroup> accessResult(DescribeAutoScalingGroupsResult result) {
        result.autoScalingGroups
      }
    }
    def request = new DescribeAutoScalingGroupsRequest(autoScalingGroupNames: asgNames)
    retriever.retrieve(request)
  }

  LaunchConfiguration getLaunchConfiguration(String launchConfigurationName) {
    Iterables.getOnlyElement(getLaunchConfigurations([launchConfigurationName]), null)
  }

  List<LaunchConfiguration> getLaunchConfigurations(Collection<String> launchConfigurationNames) {
    def retriever = new AwsResultsRetriever<LaunchConfiguration, DescribeLaunchConfigurationsRequest, DescribeLaunchConfigurationsResult>() {
      @Override
      protected DescribeLaunchConfigurationsResult makeRequest(DescribeLaunchConfigurationsRequest request) {
        amazonAutoScaling.describeLaunchConfigurations(request)
      }

      @Override
      protected List<LaunchConfiguration> accessResult(DescribeLaunchConfigurationsResult result) {
        result.launchConfigurations
      }
    }
    retriever.retrieve(new DescribeLaunchConfigurationsRequest(launchConfigurationNames: launchConfigurationNames))
  }
}
