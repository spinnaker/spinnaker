/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.services;

import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.applicationautoscaling.model.*;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.*;
import com.google.common.collect.Iterables;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsCloudWatchAlarmCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsCloudMetricService {
  @Autowired EcsCloudWatchAlarmCacheClient metricAlarmCacheClient;
  @Autowired AccountCredentialsProvider accountCredentialsProvider;
  @Autowired AmazonClientProvider amazonClientProvider;

  private final Logger log = LoggerFactory.getLogger(getClass());

  public void deleteMetrics(String serviceName, String account, String region) {
    List<EcsMetricAlarm> metricAlarms =
        metricAlarmCacheClient.getMetricAlarms(serviceName, account, region);

    if (metricAlarms.isEmpty()) {
      return;
    }

    NetflixAmazonCredentials credentials =
        (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(account);
    AmazonCloudWatch amazonCloudWatch =
        amazonClientProvider.getAmazonCloudWatch(credentials, region, false);

    amazonCloudWatch.deleteAlarms(
        new DeleteAlarmsRequest()
            .withAlarmNames(
                metricAlarms.stream().map(MetricAlarm::getAlarmName).collect(Collectors.toSet())));

    Set<String> resources = new HashSet<>();
    // Stream and flatMap it? Couldn't figure out how.
    for (MetricAlarm metricAlarm : metricAlarms) {
      resources.addAll(buildResourceList(metricAlarm.getOKActions(), serviceName));
      resources.addAll(buildResourceList(metricAlarm.getAlarmActions(), serviceName));
      resources.addAll(buildResourceList(metricAlarm.getInsufficientDataActions(), serviceName));
    }

    deregisterScalableTargets(resources, account, region);
  }

  private Set<String> buildResourceList(List<String> metricAlarmArn, String serviceName) {
    return metricAlarmArn.stream()
        .filter(arn -> arn.contains(serviceName))
        .map(
            arn -> {
              String resource = StringUtils.substringAfterLast(arn, ":resource/");
              resource = StringUtils.substringBeforeLast(resource, ":policyName");
              return resource;
            })
        .collect(Collectors.toSet());
  }

  private void deregisterScalableTargets(Set<String> resources, String account, String region) {
    NetflixAmazonCredentials credentials =
        (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(account);
    AWSApplicationAutoScaling autoScaling =
        amazonClientProvider.getAmazonApplicationAutoScaling(credentials, region, false);

    Map<String, Set<String>> resourceMap = new HashMap<>();
    for (String resource : resources) {
      String namespace = StringUtils.substringBefore(resource, "/");
      String service = StringUtils.substringAfter(resource, "/");
      if (resourceMap.containsKey(namespace)) {
        resourceMap.get(namespace).add(service);
      } else {
        Set<String> serviceSet = new HashSet<>();
        serviceSet.add(service);
        resourceMap.put(namespace, serviceSet);
      }
    }

    Set<DeregisterScalableTargetRequest> deregisterRequests = new HashSet<>();
    for (String namespace : resourceMap.keySet()) {
      String nextToken = null;
      do {
        DescribeScalableTargetsRequest request =
            new DescribeScalableTargetsRequest()
                .withServiceNamespace(namespace)
                .withResourceIds(resourceMap.get(namespace));

        if (nextToken != null) {
          request.setNextToken(nextToken);
        }

        DescribeScalableTargetsResult result = autoScaling.describeScalableTargets(request);

        deregisterRequests.addAll(
            result.getScalableTargets().stream()
                .map(
                    scalableTarget ->
                        new DeregisterScalableTargetRequest()
                            .withResourceId(scalableTarget.getResourceId())
                            .withScalableDimension(scalableTarget.getScalableDimension())
                            .withServiceNamespace(scalableTarget.getServiceNamespace()))
                .collect(Collectors.toSet()));

        nextToken = result.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }

    for (DeregisterScalableTargetRequest request : deregisterRequests) {
      autoScaling.deregisterScalableTarget(request);
    }
  }

  private PutMetricAlarmRequest buildPutMetricAlarmRequest(
      MetricAlarm metricAlarm,
      String alarmName,
      String dstServiceName,
      String clusterName,
      String srcRegion,
      String dstRegion,
      String srcAccountId,
      String dstAccountId,
      Map<String, String> policyArnReplacements) {
    return new PutMetricAlarmRequest()
        .withAlarmName(alarmName)
        .withEvaluationPeriods(metricAlarm.getEvaluationPeriods())
        .withThreshold(metricAlarm.getThreshold())
        .withActionsEnabled(metricAlarm.getActionsEnabled())
        .withAlarmDescription(metricAlarm.getAlarmDescription())
        .withComparisonOperator(metricAlarm.getComparisonOperator())
        .withDimensions(
            metricAlarm.getDimensions().stream()
                .map(
                    dimension ->
                        buildNewServiceAlarmDimension(
                            dimension, metricAlarm.getNamespace(), dstServiceName, clusterName))
                .collect(Collectors.toSet()))
        .withMetricName(metricAlarm.getMetricName())
        .withUnit(metricAlarm.getUnit())
        .withPeriod(metricAlarm.getPeriod())
        .withNamespace(metricAlarm.getNamespace())
        .withStatistic(metricAlarm.getStatistic())
        .withEvaluateLowSampleCountPercentile(metricAlarm.getEvaluateLowSampleCountPercentile())
        .withTreatMissingData(metricAlarm.getTreatMissingData())
        .withExtendedStatistic(metricAlarm.getExtendedStatistic())
        .withInsufficientDataActions(
            replacePolicyArnActions(
                srcRegion,
                dstRegion,
                srcAccountId,
                dstAccountId,
                policyArnReplacements,
                metricAlarm.getInsufficientDataActions()))
        .withOKActions(
            replacePolicyArnActions(
                srcRegion,
                dstRegion,
                srcAccountId,
                dstAccountId,
                policyArnReplacements,
                metricAlarm.getOKActions()))
        .withAlarmActions(
            replacePolicyArnActions(
                srcRegion,
                dstRegion,
                srcAccountId,
                dstAccountId,
                policyArnReplacements,
                metricAlarm.getAlarmActions()));
  }

  protected Collection<String> replacePolicyArnActions(
      String srcRegion,
      String dstRegion,
      String srcAccountId,
      String dstAccountId,
      Map<String, String> replacements,
      Collection<String> actions) {
    return actions.stream()
        // Replace src scaling policy ARNs with dst scaling policy ARNs
        .map(action -> replacements.keySet().contains(action) ? replacements.get(action) : action)
        // If we are copying across accounts or regions, do not copy over unrelated actions like SNS
        // topics
        .filter(action -> srcRegion.equals(dstRegion) || !action.contains(srcRegion))
        .filter(action -> srcAccountId.equals(dstAccountId) || !action.contains(srcAccountId))
        .collect(Collectors.toSet());
  }

  private Dimension buildNewServiceAlarmDimension(
      Dimension oldDimension, String namespace, String serviceName, String clusterName) {
    String value = oldDimension.getValue();
    if (namespace.equals("AWS/ECS")) {
      if (oldDimension.getName().equals("ClusterName")) {
        value = clusterName;
      } else if (oldDimension.getName().equals("ServiceName")) {
        value = serviceName;
      }
    }
    return new Dimension().withName(oldDimension.getName()).withValue(value);
  }

  private MetricDimension buildNewServiceTargetTrackingDimension(
      MetricDimension oldDimension, String namespace, String serviceName, String clusterName) {
    String value = oldDimension.getValue();
    if (namespace.equals("AWS/ECS")) {
      if (oldDimension.getName().equals("ClusterName")) {
        value = clusterName;
      } else if (oldDimension.getName().equals("ServiceName")) {
        value = serviceName;
      }
    }
    return new MetricDimension().withName(oldDimension.getName()).withValue(value);
  }

  private PutScalingPolicyRequest buildPutScalingPolicyRequest(ScalingPolicy policy) {
    return new PutScalingPolicyRequest()
        .withPolicyName(policy.getPolicyName())
        .withServiceNamespace(policy.getServiceNamespace())
        .withPolicyType(policy.getPolicyType())
        .withResourceId(policy.getResourceId())
        .withScalableDimension(policy.getScalableDimension())
        .withStepScalingPolicyConfiguration(policy.getStepScalingPolicyConfiguration())
        .withTargetTrackingScalingPolicyConfiguration(
            policy.getTargetTrackingScalingPolicyConfiguration());
  }

  public void copyScalingPolicies(
      String dstAccount,
      String dstRegion,
      String dstServiceName,
      String dstResourceId,
      String srcAccount,
      String srcRegion,
      String srcServiceName,
      String srcResourceId,
      String clusterName) {
    NetflixAmazonCredentials dstCredentials =
        (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(dstAccount);
    NetflixAmazonCredentials srcCredentials =
        (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(srcAccount);

    AWSApplicationAutoScaling dstAutoScalingClient =
        amazonClientProvider.getAmazonApplicationAutoScaling(dstCredentials, dstRegion, false);
    AWSApplicationAutoScaling srcAutoScalingClient =
        amazonClientProvider.getAmazonApplicationAutoScaling(srcCredentials, srcRegion, false);
    AmazonCloudWatch dstCloudWatchClient =
        amazonClientProvider.getAmazonCloudWatch(dstCredentials, dstRegion, false);
    AmazonCloudWatch srcCloudWatchClient =
        amazonClientProvider.getAmazonCloudWatch(srcCredentials, srcRegion, false);

    // Copy the scaling policies
    Set<ScalingPolicy> sourceScalingPolicies =
        getScalingPolicies(srcAutoScalingClient, srcResourceId);

    Map<String, String> srcPolicyArnToDstPolicyArn =
        putScalingPolicies(
            dstAutoScalingClient,
            srcServiceName,
            dstServiceName,
            dstResourceId,
            clusterName,
            sourceScalingPolicies);

    // Copy the alarms that target the scaling policies
    Set<String> allSourceAlarmNames =
        sourceScalingPolicies.stream()
            .flatMap(policy -> policy.getAlarms().stream())
            .map(alarm -> alarm.getAlarmName())
            .collect(Collectors.toSet());
    copyAlarmsForAsg(
        srcCloudWatchClient,
        dstCloudWatchClient,
        srcRegion,
        dstRegion,
        srcCredentials.getAccountId(),
        dstCredentials.getAccountId(),
        srcServiceName,
        dstServiceName,
        clusterName,
        allSourceAlarmNames,
        srcPolicyArnToDstPolicyArn);
  }

  private Set<ScalingPolicy> getScalingPolicies(
      AWSApplicationAutoScaling autoScalingClient, String resourceId) {
    Set<ScalingPolicy> scalingPolicies = new HashSet<>();

    String nextToken = null;
    do {
      DescribeScalingPoliciesRequest request =
          new DescribeScalingPoliciesRequest()
              .withServiceNamespace(ServiceNamespace.Ecs)
              .withResourceId(resourceId);
      if (nextToken != null) {
        request.setNextToken(nextToken);
      }

      DescribeScalingPoliciesResult result = autoScalingClient.describeScalingPolicies(request);
      scalingPolicies.addAll(result.getScalingPolicies());

      nextToken = result.getNextToken();
    } while (nextToken != null && nextToken.length() != 0);

    return scalingPolicies;
  }

  // Return map of src policy ARN -> dst policy ARN
  private Map<String, String> putScalingPolicies(
      AWSApplicationAutoScaling dstAutoScalingClient,
      String srcServiceName,
      String dstServiceName,
      String dstResourceId,
      String clusterName,
      Set<ScalingPolicy> srcScalingPolicies) {
    Map<String, String> srcPolicyArnToDstPolicyArn = new HashMap<>();

    for (ScalingPolicy scalingPolicy : srcScalingPolicies) {
      String newPolicyName =
          scalingPolicy.getPolicyName().replaceAll(srcServiceName, dstServiceName);
      if (!newPolicyName.contains(dstServiceName)) {
        newPolicyName = newPolicyName + "-" + dstServiceName;
      }

      ScalingPolicy clone = scalingPolicy.clone();
      clone.setPolicyName(newPolicyName);
      clone.setResourceId(dstResourceId);

      if (clone.getTargetTrackingScalingPolicyConfiguration() != null
          && clone.getTargetTrackingScalingPolicyConfiguration().getCustomizedMetricSpecification()
              != null) {
        CustomizedMetricSpecification spec =
            clone.getTargetTrackingScalingPolicyConfiguration().getCustomizedMetricSpecification();
        spec.setDimensions(
            spec.getDimensions().stream()
                .map(
                    dimension ->
                        buildNewServiceTargetTrackingDimension(
                            dimension, spec.getNamespace(), dstServiceName, clusterName))
                .collect(Collectors.toSet()));
      }

      PutScalingPolicyResult result =
          dstAutoScalingClient.putScalingPolicy(buildPutScalingPolicyRequest(clone));

      srcPolicyArnToDstPolicyArn.put(scalingPolicy.getPolicyARN(), result.getPolicyARN());
    }

    return srcPolicyArnToDstPolicyArn;
  }

  private void copyAlarmsForAsg(
      AmazonCloudWatch srcCloudWatchClient,
      AmazonCloudWatch dstCloudWatchClient,
      String srcRegion,
      String dstRegion,
      String srcAccountId,
      String dstAccountId,
      String srcServiceName,
      String dstServiceName,
      String clusterName,
      Set<String> srcAlarmNames,
      Map<String, String> srcPolicyArnToDstPolicyArn) {

    for (List<String> srcAlarmsPartition : Iterables.partition(srcAlarmNames, 100)) {
      DescribeAlarmsResult describeAlarmsResult =
          srcCloudWatchClient.describeAlarms(
              new DescribeAlarmsRequest().withAlarmNames(srcAlarmsPartition));

      for (MetricAlarm srcMetricAlarm : describeAlarmsResult.getMetricAlarms()) {
        if (srcMetricAlarm.getAlarmName().startsWith("TargetTracking-")) {
          // Target Tracking policies auto-create their alarms, so we don't need to copy them
          continue;
        }

        String dstAlarmName =
            srcMetricAlarm.getAlarmName().replaceAll(srcServiceName, dstServiceName);
        if (!dstAlarmName.contains(dstServiceName)) {
          dstAlarmName = dstAlarmName + "-" + dstServiceName;
        }

        dstCloudWatchClient.putMetricAlarm(
            buildPutMetricAlarmRequest(
                srcMetricAlarm,
                dstAlarmName,
                dstServiceName,
                clusterName,
                srcRegion,
                dstRegion,
                srcAccountId,
                dstAccountId,
                srcPolicyArnToDstPolicyArn));
      }
    }
  }
}
