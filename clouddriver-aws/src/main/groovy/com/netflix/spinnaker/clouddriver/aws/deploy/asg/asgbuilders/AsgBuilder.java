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

package com.netflix.spinnaker.clouddriver.aws.deploy.asg.asgbuilders;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.AlreadyExistsException;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.EnableMetricsCollectionRequest;
import com.amazonaws.services.autoscaling.model.SuspendProcessesRequest;
import com.amazonaws.services.autoscaling.model.Tag;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeSubnetsResult;
import com.amazonaws.services.ec2.model.Subnet;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgLifecycleHookWorker;
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AutoScalingWorker.AsgConfiguration;
import com.netflix.spinnaker.clouddriver.aws.model.SubnetData;
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.kork.core.RetrySupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/** A builder used to build an AWS Autoscaling group. */
@Slf4j
public abstract class AsgBuilder {
  private final RetrySupport retrySupport = new RetrySupport();

  private AmazonAutoScaling autoScaling;
  private AmazonEC2 ec2;
  private AsgLifecycleHookWorker asgLifecycleHookWorker;

  AsgBuilder(
      AmazonAutoScaling autoScaling, AmazonEC2 ec2, AsgLifecycleHookWorker asgLifecycleHookWorker) {
    this.autoScaling = autoScaling;
    this.ec2 = ec2;
    this.asgLifecycleHookWorker = asgLifecycleHookWorker;
  }

  /**
   * Abstract method to build a CreateAutoScalingGroupRequest given the input parameters in form of
   * AsgConfiguration.
   *
   * @return the CreateAutoScalingGroupRequest built
   */
  protected abstract CreateAutoScalingGroupRequest buildRequest(
      Task task, String taskPhase, String asgName, AsgConfiguration cfg);

  /**
   * Build and launch an ASG.
   *
   * @return the ASG name
   */
  public String build(Task task, String taskPhase, String asgName, AsgConfiguration cfg) {
    return createAsg(task, taskPhase, buildRequest(task, taskPhase, asgName, cfg), cfg);
  }

  /**
   * Build partial CreateAutoScalingGroupRequest. All parameters except launchConfiguration /
   * launchTemplate are configured.
   *
   * @return CreateAutoScalingGroupRequest with all but 1 parameter configured
   */
  protected CreateAutoScalingGroupRequest buildPartialRequest(
      Task task, String taskPhase, String name, AsgConfiguration cfg) {
    CreateAutoScalingGroupRequest request =
        new CreateAutoScalingGroupRequest()
            .withAutoScalingGroupName(name)
            .withMinSize(cfg.getMinInstances())
            .withMaxSize(cfg.getMaxInstances())
            .withDesiredCapacity(cfg.getDesiredInstances())
            .withLoadBalancerNames(cfg.getClassicLoadBalancers())
            .withTargetGroupARNs(cfg.getTargetGroupArns())
            .withDefaultCooldown(cfg.getCooldown())
            .withHealthCheckGracePeriod(cfg.getHealthCheckGracePeriod())
            .withHealthCheckType(cfg.getHealthCheckType())
            .withTerminationPolicies(cfg.getTerminationPolicies());

    if (cfg.getTags() != null && !cfg.getTags().isEmpty()) {
      task.updateStatus(taskPhase, "Adding tags for " + name);
      cfg.getTags().entrySet().stream()
          .forEach(
              e ->
                  request.withTags(
                      new Tag()
                          .withKey(e.getKey())
                          .withValue(e.getValue())
                          .withPropagateAtLaunch(true)));
    }

    // if we have explicitly specified subnetIds, don't require that they are tagged with a
    // subnetType/purpose
    boolean filterForSubnetPurposeTags = cfg.getSubnetIds() == null || cfg.getSubnetIds().isEmpty();

    // favor subnetIds over availability zones
    final String subnetIds =
        String.join(
            ",",
            getSubnetIds(
                getSubnets(
                    filterForSubnetPurposeTags, cfg.getSubnetType(), cfg.getAvailabilityZones()),
                cfg.getSubnetIds(),
                cfg.getAvailabilityZones()));

    List<Subnet> subnets = getSubnets(true, cfg.getSubnetType(), cfg.getAvailabilityZones());
    if (StringUtils.isNotEmpty(subnetIds)) {
      task.updateStatus(taskPhase, " > Deploying to subnetIds: " + subnetIds);
      request.withVPCZoneIdentifier(subnetIds);
    } else if (StringUtils.isNotEmpty(cfg.getSubnetType())
        && (subnets == null || subnets.isEmpty())) {
      throw new RuntimeException(
          String.format(
              "No suitable subnet was found for internal subnet purpose '%s'!",
              cfg.getSubnetType()));
    } else {
      task.updateStatus(taskPhase, "Deploying to availabilityZones: " + cfg.getAvailabilityZones());
      request.withAvailabilityZones(cfg.getAvailabilityZones());
    }

    return request;
  }

  private String createAsg(
      Task task, String taskPhase, CreateAutoScalingGroupRequest request, AsgConfiguration cfg) {
    final String asgName = request.getAutoScalingGroupName();

    // create ASG
    final RuntimeException ex =
        retrySupport.retry(
            () -> {
              try {
                autoScaling.createAutoScalingGroup(request);
                return null;
              } catch (AlreadyExistsException e) {
                if (!shouldProceedWithExistingState(
                    autoScaling, asgName, request, task, taskPhase)) {
                  return e;
                }
                log.debug("Determined pre-existing ASG is desired state, continuing...", e);
                return null;
              }
            },
            10,
            1000,
            false);
    if (ex != null) {
      throw ex;
    }

    // configure lifecycle hooks
    if (cfg.getLifecycleHooks() != null && !cfg.getLifecycleHooks().isEmpty()) {
      final Exception e =
          retrySupport.retry(
              () -> {
                task.updateStatus(taskPhase, "Creating lifecycle hooks for: " + asgName);
                asgLifecycleHookWorker.attach(task, cfg.getLifecycleHooks(), asgName);
                return null;
              },
              10,
              1000,
              false);
      if (e != null) {
        task.updateStatus(
            taskPhase,
            "Unable to attach lifecycle hooks to ASG (" + asgName + "): " + e.getMessage());
      }
    }

    // suspend auto scaling processes
    if (cfg.getSuspendedProcesses() != null && !cfg.getSuspendedProcesses().isEmpty()) {
      task.updateStatus(taskPhase, "Suspending processes for: " + asgName);
      retrySupport.retry(
          () ->
              autoScaling.suspendProcesses(
                  new SuspendProcessesRequest()
                      .withAutoScalingGroupName(asgName)
                      .withScalingProcesses(cfg.getSuspendedProcesses())),
          10,
          1000,
          false);
    }

    // enable metrics and monitoring
    if (cfg.getEnabledMetrics() != null
        && !cfg.getEnabledMetrics().isEmpty()
        && cfg.getInstanceMonitoring() != null
        && cfg.getInstanceMonitoring()) {
      task.updateStatus(taskPhase, "Enabling metrics collection for: " + asgName);
      retrySupport.retry(
          () ->
              autoScaling.enableMetricsCollection(
                  new EnableMetricsCollectionRequest()
                      .withAutoScalingGroupName(asgName)
                      .withGranularity("1Minute")
                      .withMetrics(cfg.getEnabledMetrics())),
          10,
          1000,
          false);
    }

    // udpate ASG
    retrySupport.retry(
        () -> {
          task.updateStatus(
              taskPhase,
              String.format(
                  "Setting size of %s in %s/%s to [min=%s, max=%s, desired=%s]",
                  asgName,
                  cfg.getCredentials().getName(),
                  cfg.getRegion(),
                  cfg.getMinInstances(),
                  cfg.getMaxInstances(),
                  cfg.getDesiredInstances()));
          autoScaling.updateAutoScalingGroup(
              new UpdateAutoScalingGroupRequest()
                  .withAutoScalingGroupName(asgName)
                  .withMinSize(cfg.getMinInstances())
                  .withMaxSize(cfg.getMaxInstances())
                  .withDesiredCapacity(cfg.getDesiredInstances()));
          return true;
        },
        10,
        1000,
        false);

    task.updateStatus(taskPhase, "Deployed EC2 server group named " + asgName);
    return asgName;
  }

  private boolean shouldProceedWithExistingState(
      AmazonAutoScaling autoScaling,
      String asgName,
      CreateAutoScalingGroupRequest request,
      Task task,
      String taskPhase) {
    final DescribeAutoScalingGroupsResult result =
        autoScaling.describeAutoScalingGroups(
            new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName));
    if (result.getAutoScalingGroups().isEmpty()) {
      // This will only happen if we get an AlreadyExistsException from AWS, then immediately after
      // describing it, we
      // don't get a result back. We'll continue with trying to create because who knows may as well
      // try.
      log.error("Attempted to find pre-existing ASG but none was found: " + asgName);
      return true;
    }
    final AutoScalingGroup existingAsg = result.getAutoScalingGroups().get(0);

    // build predicates and identify failed ones
    List<String> existingAsgSubnetIds = null;
    if (StringUtils.isNotEmpty(existingAsg.getVPCZoneIdentifier())) {
      existingAsgSubnetIds = sortList(Arrays.asList(existingAsg.getVPCZoneIdentifier().split(",")));
    }
    List<String> requestedSubnetIds = null;
    if (StringUtils.isNotEmpty(request.getVPCZoneIdentifier())) {
      requestedSubnetIds = sortList(Arrays.asList(request.getVPCZoneIdentifier().split(",")));
    }
    Map<String, Boolean> predicates =
        ImmutableMap.<String, Boolean>builder()
            .put(
                "launch configuration",
                Objects.equals(
                    existingAsg.getLaunchConfigurationName(), request.getLaunchConfigurationName()))
            .put(
                "launch template",
                Objects.equals(existingAsg.getLaunchTemplate(), request.getLaunchTemplate()))
            .put(
                "availability zones",
                Objects.equals(
                    sortList(existingAsg.getAvailabilityZones()),
                    sortList(request.getAvailabilityZones())))
            .put("subnets", Objects.equals(existingAsgSubnetIds, requestedSubnetIds))
            .put(
                "load balancers",
                Objects.equals(
                    sortList(existingAsg.getLoadBalancerNames()),
                    sortList(request.getLoadBalancerNames())))
            .put(
                "target groups",
                Objects.equals(
                    sortList(existingAsg.getTargetGroupARNs()),
                    sortList(request.getTargetGroupARNs())))
            .put("cooldown", existingAsg.getDefaultCooldown() == request.getDefaultCooldown())
            .put(
                "health check grace period",
                existingAsg.getHealthCheckGracePeriod() == request.getHealthCheckGracePeriod())
            .put(
                "health check type",
                existingAsg.getHealthCheckType() == request.getHealthCheckType())
            .put(
                "termination policies",
                Objects.equals(
                    sortList(existingAsg.getTerminationPolicies()),
                    sortList(request.getTerminationPolicies())))
            .build();
    final Set<String> failedPredicates =
        predicates.entrySet().stream()
            .filter(p -> !p.getValue())
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    if (!failedPredicates.isEmpty()) {
      task.updateStatus(
          taskPhase,
          String.format(
              "%s already exists and does not seem to match desired state on: %s",
              asgName, String.join(",", failedPredicates)));
      return false;
    }

    if (existingAsg
        .getCreatedTime()
        .toInstant()
        .isBefore(Instant.now().minus(1, ChronoUnit.HOURS))) {
      task.updateStatus(
          taskPhase,
          asgName
              + " already exists and appears to be valid, but falls outside of safety window for idempotent deploy (1 hour)");
      return false;
    }

    return true;
  }

  /**
   * This is an obscure rule that Subnets are tagged at Amazon with a data structure, which defines
   * their purpose and what type of resources (elb or ec2) are able to make use of them. We also
   * need to ensure that the Subnet IDs that we provide back are able to be deployed to based off of
   * the supplied availability zones.
   *
   * @return list of subnet ids applicable to this deployment.
   */
  private List<String> getSubnetIds(
      List<Subnet> allSubnetsForTypeAndAvailabilityZone,
      List<String> subnetIds,
      List<String> availabilityZones) {
    final List<String> allSubnetIds =
        allSubnetsForTypeAndAvailabilityZone.stream()
            .map(s -> s.getSubnetId())
            .collect(Collectors.toList());

    List<String> invalidSubnetIds = null;
    if (subnetIds != null && !subnetIds.isEmpty()) {
      invalidSubnetIds =
          subnetIds.stream().filter(it -> !allSubnetIds.contains(it)).collect(Collectors.toList());
    }

    if (invalidSubnetIds != null && !invalidSubnetIds.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "One or more subnet ids are not valid (invalidSubnetIds: %s, availabilityZones: %s)",
              String.join(",", invalidSubnetIds), String.join(",", availabilityZones)));
    }

    return (subnetIds != null && !subnetIds.isEmpty()) ? subnetIds : allSubnetIds;
  }

  private List<Subnet> getSubnets(
      boolean filterForSubnetPurposeTags, String subnetType, List<String> availabilityZones) {
    if (StringUtils.isEmpty(subnetType)) {
      return Collections.emptyList();
    }

    final DescribeSubnetsResult result = ec2.describeSubnets();
    List<Subnet> mySubnets = new ArrayList<>();
    for (Subnet subnet : result.getSubnets()) {
      if (availabilityZones != null
          && !availabilityZones.isEmpty()
          && !availabilityZones.contains(subnet.getAvailabilityZone())) {
        continue;
      }
      if (filterForSubnetPurposeTags) {
        final SubnetData sd = SubnetData.from(subnet);
        if ((sd.getPurpose() != null && sd.getPurpose().equals(subnetType))
            && (sd.getTarget() == null || sd.getTarget() == SubnetTarget.EC2)) {
          mySubnets.add(subnet);
        }
      } else {
        mySubnets.add(subnet);
      }
    }
    return mySubnets;
  }

  private List<String> sortList(List<String> list) {
    return list.stream().sorted(Comparator.naturalOrder()).collect(Collectors.toList());
  }
}
