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
import com.amazonaws.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalingPoliciesResult;
import com.amazonaws.services.applicationautoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.applicationautoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.applicationautoscaling.model.ScalingPolicy;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsCloudWatchAlarmCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EcsCloudMetricService {
  @Autowired
  EcsCloudWatchAlarmCacheClient metricAlarmCacheClient;
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;
  @Autowired
  AmazonClientProvider amazonClientProvider;

  public void deleteMetrics(String serviceName, String account, String region) {
    List<EcsMetricAlarm> metricAlarms = metricAlarmCacheClient.getMetricAlarms(serviceName, account, region);

    if (metricAlarms.isEmpty()) {
      return;
    }

    AmazonCredentials credentials = (AmazonCredentials) accountCredentialsProvider.getCredentials(account);
    AmazonCloudWatch amazonCloudWatch = amazonClientProvider.getAmazonCloudWatch(account, credentials.getCredentialsProvider(), region);

    amazonCloudWatch.deleteAlarms(new DeleteAlarmsRequest().withAlarmNames(metricAlarms.stream()
      .map(MetricAlarm::getAlarmName)
      .collect(Collectors.toSet())));

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
      .map(arn -> {
        String resource = StringUtils.substringAfterLast(arn, ":resource/");
        resource = StringUtils.substringBeforeLast(resource, ":policyName");
        return resource;
      })
      .collect(Collectors.toSet());
  }

  private void deregisterScalableTargets(Set<String> resources, String account, String region) {
    AmazonCredentials credentials = (AmazonCredentials) accountCredentialsProvider.getCredentials(account);
    AWSApplicationAutoScaling autoScaling = amazonClientProvider.getAmazonApplicationAutoScaling(account, credentials.getCredentialsProvider(), region);

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
        DescribeScalableTargetsRequest request = new DescribeScalableTargetsRequest()
          .withServiceNamespace(namespace)
          .withResourceIds(resourceMap.get(namespace));

        if (nextToken != null) {
          request.setNextToken(nextToken);
        }

        DescribeScalableTargetsResult result = autoScaling.describeScalableTargets(request);

        deregisterRequests.addAll(result.getScalableTargets().stream()
          .map(scalableTarget -> new DeregisterScalableTargetRequest()
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

  private PutMetricAlarmRequest buildPutMetricAlarmRequest(MetricAlarm metricAlarm,
                                                           String serviceName,
                                                           Set<String> insufficientActionPolicyArns,
                                                           Set<String> okActionPolicyArns,
                                                           Set<String> alarmActionPolicyArns) {
    return new PutMetricAlarmRequest()
      .withAlarmName(metricAlarm.getAlarmName() + "-" + serviceName)
      .withEvaluationPeriods(metricAlarm.getEvaluationPeriods())
      .withThreshold(metricAlarm.getThreshold())
      .withActionsEnabled(metricAlarm.getActionsEnabled())
      .withAlarmDescription(metricAlarm.getAlarmDescription())
      .withComparisonOperator(metricAlarm.getComparisonOperator())
      .withDimensions(metricAlarm.getDimensions())
      .withMetricName(metricAlarm.getMetricName())
      .withUnit(metricAlarm.getUnit())
      .withPeriod(metricAlarm.getPeriod())
      .withNamespace(metricAlarm.getNamespace())
      .withStatistic(metricAlarm.getStatistic())
      .withEvaluateLowSampleCountPercentile(metricAlarm.getEvaluateLowSampleCountPercentile())
      .withTreatMissingData(metricAlarm.getTreatMissingData())
      .withExtendedStatistic(metricAlarm.getExtendedStatistic())
      .withInsufficientDataActions(insufficientActionPolicyArns)
      .withOKActions(okActionPolicyArns)
      .withAlarmActions(alarmActionPolicyArns);
  }

  private PutScalingPolicyRequest buildPutScalingPolicyRequest(ScalingPolicy policy) {
    return new PutScalingPolicyRequest()
      .withPolicyName(policy.getPolicyName())
      .withServiceNamespace(policy.getServiceNamespace())
      .withPolicyType(policy.getPolicyType())
      .withResourceId(policy.getResourceId())
      .withScalableDimension(policy.getScalableDimension())
      .withStepScalingPolicyConfiguration(policy.getStepScalingPolicyConfiguration())
      .withTargetTrackingScalingPolicyConfiguration(policy.getTargetTrackingScalingPolicyConfiguration());
  }

  public void associateAsgWithMetrics(String account,
                                      String region,
                                      List<String> alarmNames,
                                      String serviceName,
                                      String resourceId) {

    AmazonCredentials credentials = (AmazonCredentials) accountCredentialsProvider.getCredentials(account);

    AmazonCloudWatch cloudWatch = amazonClientProvider.getAmazonCloudWatch(account, credentials.getCredentialsProvider(), region);
    AWSApplicationAutoScaling autoScalingClient = amazonClientProvider.getAmazonApplicationAutoScaling(account, credentials.getCredentialsProvider(), region);

    DescribeAlarmsResult describeAlarmsResult = cloudWatch.describeAlarms(new DescribeAlarmsRequest()
      .withAlarmNames(alarmNames));

    for (MetricAlarm metricAlarm : describeAlarmsResult.getMetricAlarms()) {
      Set<String> okScalingPolicyArns = putScalingPolicies(autoScalingClient, metricAlarm.getOKActions(),
        serviceName, resourceId, "ok", "scaling-policy-" + metricAlarm.getAlarmName());
      Set<String> alarmScalingPolicyArns = putScalingPolicies(autoScalingClient, metricAlarm.getAlarmActions(),
        serviceName, resourceId, "alarm", "scaling-policy-" + metricAlarm.getAlarmName());
      Set<String> insufficientActionPolicyArns = putScalingPolicies(autoScalingClient, metricAlarm.getInsufficientDataActions(),
        serviceName, resourceId, "insuffiicient", "scaling-policy-" + metricAlarm.getAlarmName());

      cloudWatch.putMetricAlarm(buildPutMetricAlarmRequest(metricAlarm, serviceName,
        insufficientActionPolicyArns, okScalingPolicyArns, alarmScalingPolicyArns));
    }
  }

  private Set<String> putScalingPolicies(AWSApplicationAutoScaling autoScalingClient,
                                         List<String> actionArns,
                                         String serviceName,
                                         String resourceId,
                                         String type,
                                         String suffix) {
    if (actionArns.isEmpty()) {
      return Collections.emptySet();
    }

    Set<ScalingPolicy> scalingPolicies = new HashSet<>();

    String nextToken = null;
    do {
      DescribeScalingPoliciesRequest request = new DescribeScalingPoliciesRequest().withPolicyNames(actionArns.stream()
        .map(arn -> StringUtils.substringAfterLast(arn, ":policyName/"))
        .collect(Collectors.toSet()))
        .withServiceNamespace(ServiceNamespace.Ecs);
      if (nextToken != null) {
        request.setNextToken(nextToken);
      }

      DescribeScalingPoliciesResult result = autoScalingClient.describeScalingPolicies(request);
      scalingPolicies.addAll(result.getScalingPolicies());

      nextToken = result.getNextToken();
    } while (nextToken != null && nextToken.length() != 0);

    Set<String> policyArns = new HashSet<>();
    for (ScalingPolicy scalingPolicy : scalingPolicies) {
      String newPolicyName = serviceName + "-" + type + "-" + suffix;
      ScalingPolicy clone = scalingPolicy.clone();
      clone.setPolicyName(newPolicyName);
      clone.setResourceId(resourceId);

      PutScalingPolicyResult result = autoScalingClient.putScalingPolicy(buildPutScalingPolicyRequest(clone));
      policyArns.add(result.getPolicyARN());
    }

    return policyArns;
  }
}
