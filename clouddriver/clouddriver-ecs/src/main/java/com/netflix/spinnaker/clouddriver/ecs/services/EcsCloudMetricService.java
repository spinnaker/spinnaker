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

import com.google.common.collect.Iterables;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsCloudWatchAlarmCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.applicationautoscaling.model.CustomizedMetricSpecification;
import software.amazon.awssdk.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.MetricDimension;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalingPolicy;
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace;
import software.amazon.awssdk.services.applicationautoscaling.model.TargetTrackingScalingPolicyConfiguration;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.DeleteAlarmsRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricAlarmRequest;

@Component
public class EcsCloudMetricService {
  @Autowired EcsCloudWatchAlarmCacheClient metricAlarmCacheClient;
  @Autowired CredentialsRepository<NetflixECSCredentials> credentialsRepository;
  @Autowired AmazonClientProvider amazonClientProvider;

  private final Logger log = LoggerFactory.getLogger(getClass());

  public void deleteMetrics(
      String serviceName, String account, String region, String ecsClusterName) {
    List<EcsMetricAlarm> metricAlarms =
        metricAlarmCacheClient.getMetricAlarms(serviceName, account, region, ecsClusterName);

    if (metricAlarms.isEmpty()) {
      return;
    }

    NetflixAmazonCredentials credentials = credentialsRepository.getOne(account);
    CloudWatchClient amazonCloudWatch =
        amazonClientProvider.getAmazonCloudWatchV2(credentials, region);

    amazonCloudWatch.deleteAlarms(
        DeleteAlarmsRequest.builder()
            .alarmNames(
                metricAlarms.stream().map(EcsMetricAlarm::getAlarmName).collect(Collectors.toSet()))
            .build());

    Set<String> resources = new HashSet<>();
    for (EcsMetricAlarm metricAlarm : metricAlarms) {
      resources.addAll(buildResourceList(new ArrayList<>(metricAlarm.getOKActions()), serviceName));
      resources.addAll(
          buildResourceList(new ArrayList<>(metricAlarm.getAlarmActions()), serviceName));
      resources.addAll(
          buildResourceList(
              new ArrayList<>(metricAlarm.getInsufficientDataActions()), serviceName));
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
    NetflixAmazonCredentials credentials = credentialsRepository.getOne(account);
    ApplicationAutoScalingClient autoScaling =
        amazonClientProvider.getAmazonApplicationAutoScalingV2(credentials, region);

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
        DescribeScalableTargetsRequest.Builder requestBuilder =
            DescribeScalableTargetsRequest.builder()
                .serviceNamespace(namespace)
                .resourceIds(resourceMap.get(namespace));

        if (nextToken != null) {
          requestBuilder.nextToken(nextToken);
        }

        DescribeScalableTargetsResponse result =
            autoScaling.describeScalableTargets(requestBuilder.build());

        deregisterRequests.addAll(
            result.scalableTargets().stream()
                .map(
                    scalableTarget ->
                        DeregisterScalableTargetRequest.builder()
                            .resourceId(scalableTarget.resourceId())
                            .scalableDimension(scalableTarget.scalableDimension())
                            .serviceNamespace(scalableTarget.serviceNamespace())
                            .build())
                .collect(Collectors.toSet()));

        nextToken = result.nextToken();
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
    return PutMetricAlarmRequest.builder()
        .alarmName(alarmName)
        .evaluationPeriods(metricAlarm.evaluationPeriods())
        .threshold(metricAlarm.threshold())
        .actionsEnabled(metricAlarm.actionsEnabled())
        .alarmDescription(metricAlarm.alarmDescription())
        .comparisonOperator(metricAlarm.comparisonOperator())
        .dimensions(
            metricAlarm.dimensions().stream()
                .map(
                    dimension ->
                        buildNewServiceAlarmDimension(
                            dimension, metricAlarm.namespace(), dstServiceName, clusterName))
                .collect(Collectors.toSet()))
        .metricName(metricAlarm.metricName())
        .unit(metricAlarm.unit())
        .period(metricAlarm.period())
        .namespace(metricAlarm.namespace())
        .statistic(metricAlarm.statistic())
        .evaluateLowSampleCountPercentile(metricAlarm.evaluateLowSampleCountPercentile())
        .treatMissingData(metricAlarm.treatMissingData())
        .extendedStatistic(metricAlarm.extendedStatistic())
        .insufficientDataActions(
            replacePolicyArnActions(
                srcRegion,
                dstRegion,
                srcAccountId,
                dstAccountId,
                policyArnReplacements,
                metricAlarm.insufficientDataActions()))
        .okActions(
            replacePolicyArnActions(
                srcRegion,
                dstRegion,
                srcAccountId,
                dstAccountId,
                policyArnReplacements,
                metricAlarm.okActions()))
        .alarmActions(
            replacePolicyArnActions(
                srcRegion,
                dstRegion,
                srcAccountId,
                dstAccountId,
                policyArnReplacements,
                metricAlarm.alarmActions()))
        .build();
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
    String value = oldDimension.value();
    if (namespace.equals("AWS/ECS")) {
      if (oldDimension.name().equals("ClusterName")) {
        value = clusterName;
      } else if (oldDimension.name().equals("ServiceName")) {
        value = serviceName;
      }
    }
    return Dimension.builder().name(oldDimension.name()).value(value).build();
  }

  private MetricDimension buildNewServiceTargetTrackingDimension(
      MetricDimension oldDimension, String namespace, String serviceName, String clusterName) {
    String value = oldDimension.value();
    if (namespace.equals("AWS/ECS")) {
      if (oldDimension.name().equals("ClusterName")) {
        value = clusterName;
      } else if (oldDimension.name().equals("ServiceName")) {
        value = serviceName;
      }
    }
    return MetricDimension.builder().name(oldDimension.name()).value(value).build();
  }

  private PutScalingPolicyRequest buildPutScalingPolicyRequest(ScalingPolicy policy) {
    return PutScalingPolicyRequest.builder()
        .policyName(policy.policyName())
        .serviceNamespace(policy.serviceNamespace())
        .policyType(policy.policyType())
        .resourceId(policy.resourceId())
        .scalableDimension(policy.scalableDimension())
        .stepScalingPolicyConfiguration(policy.stepScalingPolicyConfiguration())
        .targetTrackingScalingPolicyConfiguration(policy.targetTrackingScalingPolicyConfiguration())
        .build();
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
    NetflixAmazonCredentials dstCredentials = credentialsRepository.getOne(dstAccount);
    NetflixAmazonCredentials srcCredentials = credentialsRepository.getOne(srcAccount);

    ApplicationAutoScalingClient dstAutoScalingClient =
        amazonClientProvider.getAmazonApplicationAutoScalingV2(dstCredentials, dstRegion);
    ApplicationAutoScalingClient srcAutoScalingClient =
        amazonClientProvider.getAmazonApplicationAutoScalingV2(srcCredentials, srcRegion);
    CloudWatchClient dstCloudWatchClient =
        amazonClientProvider.getAmazonCloudWatchV2(dstCredentials, dstRegion);
    CloudWatchClient srcCloudWatchClient =
        amazonClientProvider.getAmazonCloudWatchV2(srcCredentials, srcRegion);

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
            .flatMap(policy -> policy.alarms().stream())
            .map(alarm -> alarm.alarmName())
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
      ApplicationAutoScalingClient autoScalingClient, String resourceId) {
    Set<ScalingPolicy> scalingPolicies = new HashSet<>();

    String nextToken = null;
    do {
      DescribeScalingPoliciesRequest.Builder requestBuilder =
          DescribeScalingPoliciesRequest.builder()
              .serviceNamespace(ServiceNamespace.ECS)
              .resourceId(resourceId);
      if (nextToken != null) {
        requestBuilder.nextToken(nextToken);
      }

      DescribeScalingPoliciesResponse result =
          autoScalingClient.describeScalingPolicies(requestBuilder.build());
      scalingPolicies.addAll(result.scalingPolicies());

      nextToken = result.nextToken();
    } while (nextToken != null && nextToken.length() != 0);

    return scalingPolicies;
  }

  // Return map of src policy ARN -> dst policy ARN
  private Map<String, String> putScalingPolicies(
      ApplicationAutoScalingClient dstAutoScalingClient,
      String srcServiceName,
      String dstServiceName,
      String dstResourceId,
      String clusterName,
      Set<ScalingPolicy> srcScalingPolicies) {
    Map<String, String> srcPolicyArnToDstPolicyArn = new HashMap<>();

    for (ScalingPolicy scalingPolicy : srcScalingPolicies) {
      String newPolicyName = scalingPolicy.policyName().replaceAll(srcServiceName, dstServiceName);
      if (!newPolicyName.contains(dstServiceName)) {
        newPolicyName = newPolicyName + "-" + dstServiceName;
      }

      ScalingPolicy.Builder cloneBuilder =
          scalingPolicy.toBuilder().policyName(newPolicyName).resourceId(dstResourceId);

      TargetTrackingScalingPolicyConfiguration ttConfig =
          scalingPolicy.targetTrackingScalingPolicyConfiguration();
      if (ttConfig != null && ttConfig.customizedMetricSpecification() != null) {
        CustomizedMetricSpecification spec = ttConfig.customizedMetricSpecification();
        CustomizedMetricSpecification newSpec =
            spec.toBuilder()
                .dimensions(
                    spec.dimensions().stream()
                        .map(
                            dimension ->
                                buildNewServiceTargetTrackingDimension(
                                    dimension, spec.namespace(), dstServiceName, clusterName))
                        .collect(Collectors.toSet()))
                .build();
        cloneBuilder.targetTrackingScalingPolicyConfiguration(
            ttConfig.toBuilder().customizedMetricSpecification(newSpec).build());
      }

      ScalingPolicy clone = cloneBuilder.build();

      PutScalingPolicyResponse result =
          dstAutoScalingClient.putScalingPolicy(buildPutScalingPolicyRequest(clone));

      srcPolicyArnToDstPolicyArn.put(scalingPolicy.policyARN(), result.policyARN());
    }

    return srcPolicyArnToDstPolicyArn;
  }

  private void copyAlarmsForAsg(
      CloudWatchClient srcCloudWatchClient,
      CloudWatchClient dstCloudWatchClient,
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
      DescribeAlarmsResponse describeAlarmsResult =
          srcCloudWatchClient.describeAlarms(
              DescribeAlarmsRequest.builder().alarmNames(srcAlarmsPartition).build());

      for (MetricAlarm srcMetricAlarm : describeAlarmsResult.metricAlarms()) {
        if (srcMetricAlarm.alarmName().startsWith("TargetTracking-")) {
          // Target Tracking policies auto-create their alarms, so we don't need to copy them
          continue;
        }

        String dstAlarmName = srcMetricAlarm.alarmName().replaceAll(srcServiceName, dstServiceName);
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
