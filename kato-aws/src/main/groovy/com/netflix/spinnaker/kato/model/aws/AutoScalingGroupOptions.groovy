/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kato.model.aws

import com.amazonaws.services.autoscaling.model.*
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import groovy.transform.Canonical

/**
 * Attributes specified when manipulating auto scaling groups.
 */
@Canonical class AutoScalingGroupOptions {

  /** @see AutoScalingGroup#autoScalingGroupName */
  String autoScalingGroupName

  /** @see AutoScalingGroup#launchConfigurationName */
  String launchConfigurationName

  /** @see AutoScalingGroup#minSize */
  Integer minSize

  /** @see AutoScalingGroup#maxSize */
  Integer maxSize

  /** @see AutoScalingGroup#desiredCapacity */
  Integer desiredCapacity

  /** @see AutoScalingGroup#defaultCooldown */
  Integer defaultCooldown

  /** @see AutoScalingGroup#availabilityZones */
  Set<String> availabilityZones

  /** @see AutoScalingGroup#loadBalancerNames */
  Set<String> loadBalancerNames

  /** @see AutoScalingGroup#healthCheckType */
  AutoScalingGroupHealthCheckType healthCheckType

  /** @see AutoScalingGroup#healthCheckGracePeriod */
  Integer healthCheckGracePeriod

  /** @see AutoScalingGroup#placementGroup */
  String placementGroup

  /** Subnet purpose is used to identify the corresponding subnet and generate the VPC zone identifier. */
  String subnetPurpose

  /** @see AutoScalingGroup#terminationPolicies */
  List<String> terminationPolicies

  /** @see AutoScalingGroup#tags */
  Set<Tag> tags

  /** @see AutoScalingGroup#suspendedProcesses */
  Set<AutoScalingProcessType> suspendedProcesses

  private static <T> Set<T> copyNonNullToSet(Collection<T> source) {
    source == null ? null : Sets.newHashSet(source)
  }

  private static <T> List<T> copyNonNullToList(Collection<T> source) {
    source == null ? null : Lists.newArrayList(source)
  }

  void setAvailabilityZones(Collection<String> availabilityZones) {
    this.availabilityZones = copyNonNullToSet(availabilityZones)
  }

  void setLoadBalancerNames(Collection<String> loadBalancerNames) {
    this.loadBalancerNames = copyNonNullToSet(loadBalancerNames)
  }

  void setTerminationPolicies(List<String> terminationPolicies) {
    this.terminationPolicies = copyNonNullToList(terminationPolicies)
  }

  void setTags(Collection<Tag> tags) {
    this.tags = copyTags(tags)
  }

  void setSuspendedProcesses(Collection<AutoScalingProcessType> suspendedProcesses) {
    this.suspendedProcesses = copyNonNullToSet(suspendedProcesses)
  }

  @SuppressWarnings('ReturnsNullInsteadOfEmptyCollection')
  private static Set<Tag> copyTags(Collection tags) {
    if (tags == null) { return null }
    tags.collect {
      new Tag(resourceId: it.resourceId, resourceType: it.resourceType, key: it.key, value: it.value)
    } as Set
  }

  private String constructVpcZoneIdentifier(SubnetAnalyzer subnets) {
    subnets.constructNewVpcZoneIdentifierForPurposeAndZones(subnetPurpose, availabilityZones)
  }

  /**
   * Clone options.
   *
   * @param source state
   * @return a deep copy of the source state
   */
  static AutoScalingGroupOptions from(AutoScalingGroupOptions source) {
    new AutoScalingGroupOptions(
      autoScalingGroupName: source.autoScalingGroupName,
      launchConfigurationName: source.launchConfigurationName,
      minSize: source.minSize,
      maxSize: source.maxSize,
      desiredCapacity: source.desiredCapacity,
      defaultCooldown: source.defaultCooldown,
      availabilityZones: copyNonNullToSet(source.availabilityZones),
      loadBalancerNames: copyNonNullToSet(source.loadBalancerNames),
      healthCheckType: source.healthCheckType,
      healthCheckGracePeriod: source.healthCheckGracePeriod,
      placementGroup: source.placementGroup,
      subnetPurpose: source.subnetPurpose,
      terminationPolicies: copyNonNullToList(source.terminationPolicies),
      tags: copyTags(source.tags),
      suspendedProcesses: copyNonNullToSet(source.suspendedProcesses)
    )
  }

  /**
   * Copy options from an AutoScalingGroup.
   *
   * @param group state to copy
   * @param subnets for VPC zone identifier identification
   * @return a deep copy of the group
   */
  static AutoScalingGroupOptions from(AutoScalingGroup group, SubnetAnalyzer subnets) {
    String subnetPurpose = null
    if (group.getVPCZoneIdentifier()) {
      subnetPurpose = subnets.getPurposeFromVpcZoneIdentifier(group.getVPCZoneIdentifier())
    }
    Set<AutoScalingProcessType> suspendedProcesses = group.suspendedProcesses.collect {
      AutoScalingProcessType.parse(it.processName)
    }
    group.with {
      new AutoScalingGroupOptions(
        autoScalingGroupName: autoScalingGroupName,
        launchConfigurationName: launchConfigurationName,
        minSize: minSize,
        maxSize: maxSize,
        desiredCapacity: desiredCapacity,
        defaultCooldown: defaultCooldown,
        availabilityZones: copyNonNullToSet(availabilityZones),
        loadBalancerNames: copyNonNullToSet(loadBalancerNames),
        healthCheckType: AutoScalingGroupHealthCheckType.by(healthCheckType),
        healthCheckGracePeriod: healthCheckGracePeriod,
        placementGroup: placementGroup,
        subnetPurpose: subnetPurpose,
        terminationPolicies: copyNonNullToList(terminationPolicies),
        tags: copyTags(tags),
        suspendedProcesses: copyNonNullToSet(suspendedProcesses)
      )
    }
  }

  /**
   * Construct CreateAutoScalingGroupRequest.
   *
   * @param subnets for VPC zone identifier generation
   * @return a CreateAutoScalingGroupRequest based on these options
   */
  CreateAutoScalingGroupRequest getCreateAutoScalingGroupRequest(SubnetAnalyzer subnets) {
    String vpcZoneIdentifier = constructVpcZoneIdentifier(subnets)
    new CreateAutoScalingGroupRequest(
      autoScalingGroupName: autoScalingGroupName,
      launchConfigurationName: launchConfigurationName,
      minSize: minSize,
      maxSize: maxSize,
      desiredCapacity: desiredCapacity,
      defaultCooldown: defaultCooldown,
      availabilityZones: copyNonNullToSet(availabilityZones),
      loadBalancerNames: copyNonNullToSet(loadBalancerNames),
      healthCheckType: healthCheckType?.name(),
      healthCheckGracePeriod: healthCheckGracePeriod,
      placementGroup: placementGroup,
      vPCZoneIdentifier: vpcZoneIdentifier,
      terminationPolicies: copyNonNullToList(terminationPolicies),
      tags: copyTags(tags)
    )
  }

  /**
   * Construct UpdateAutoScalingGroupRequest.
   *
   * @param subnets for VPC zone identifier generation
   * @return a UpdateAutoScalingGroupRequest based on these options
   */
  UpdateAutoScalingGroupRequest getUpdateAutoScalingGroupRequest(SubnetAnalyzer subnets) {
    String vpcZoneIdentifier = null
    if (subnetPurpose != null) {
      vpcZoneIdentifier = constructVpcZoneIdentifier(subnets)
    }
    new UpdateAutoScalingGroupRequest(
      autoScalingGroupName: autoScalingGroupName,
      launchConfigurationName: launchConfigurationName,
      minSize: minSize,
      maxSize: maxSize,
      desiredCapacity: desiredCapacity,
      defaultCooldown: defaultCooldown,
      availabilityZones: copyNonNullToSet(availabilityZones),
      healthCheckType: healthCheckType?.name(),
      healthCheckGracePeriod: healthCheckGracePeriod,
      placementGroup: placementGroup,
      vPCZoneIdentifier: vpcZoneIdentifier,
      terminationPolicies: copyNonNullToList(terminationPolicies)
    )
  }

  private Collection<String> convertToProcessNames(Collection<AutoScalingProcessType> processes) {
    processes.collect { it.name() }
  }

  private SuspendProcessesRequest constructSuspendProcessesRequest(Collection<AutoScalingProcessType> processes) {
    new SuspendProcessesRequest(
      autoScalingGroupName: autoScalingGroupName,
      scalingProcesses: convertToProcessNames(processes)
    )
  }

  private ResumeProcessesRequest constructResumeProcessesRequest(Collection<AutoScalingProcessType> processes) {
    new ResumeProcessesRequest(
      autoScalingGroupName: autoScalingGroupName,
      scalingProcesses: convertToProcessNames(processes)
    )
  }

  /**
   * Construct SuspendProcessesRequest.
   *
   * @param newSuspendedProcesses used to compare to existing suspended processes
   * @return a SuspendProcessesRequest based on these options
   */
  SuspendProcessesRequest getSuspendProcessesRequestForUpdate(
    Collection<AutoScalingProcessType> newSuspendedProcesses) {
    Collection<AutoScalingProcessType> processes = (newSuspendedProcesses ?: []) - (suspendedProcesses ?: [])
    if (!processes) { return null }
    constructSuspendProcessesRequest(processes)
  }

  /**
   * Construct ResumeProcessesRequest.
   *
   * @param newSuspendedProcesses used to compare to existing suspended processes
   * @return a ResumeProcessesRequest based on these options
   */
  ResumeProcessesRequest getResumeProcessesRequestForUpdate(
    Collection<AutoScalingProcessType> newSuspendedProcesses) {
    Collection<AutoScalingProcessType> processes = (suspendedProcesses ?: []) - (newSuspendedProcesses ?: [])
    if (!processes) { return null }
    constructResumeProcessesRequest(processes)
  }

}
