/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.ecs.EcsNativeServiceTag;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.AbstractECSDescription;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.Service;

public abstract class AbstractEcsAtomicOperation<T extends AbstractECSDescription, K>
    implements AtomicOperation<K> {
  private final String basePhase;
  private final boolean requireNativeServiceOwnership;
  @Autowired AmazonClientProvider amazonClientProvider;
  @Autowired CredentialsRepository<NetflixECSCredentials> credentialsRepository;
  @Autowired ContainerInformationService containerInformationService;
  T description;

  AbstractEcsAtomicOperation(T description, String basePhase) {
    this(description, basePhase, false);
  }

  AbstractEcsAtomicOperation(
      T description, String basePhase, boolean requireNativeServiceOwnership) {
    this.description = description;
    this.basePhase = basePhase;
    this.requireNativeServiceOwnership = requireNativeServiceOwnership;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  String getCluster(String service, String account) {
    String region = getRegion();
    return containerInformationService.getClusterName(service, account, region);
  }

  EcsClient getAmazonEcsClient() {
    String region = getRegion();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonEcsV2(credentialAccount, region);
  }

  ApplicationAutoScalingClient getAmazonApplicationAutoScalingClient() {
    String region = getRegion();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonApplicationAutoScalingV2(credentialAccount, region);
  }

  protected String getRegion() {
    return description.getRegion();
  }

  AmazonCredentials getCredentials() {
    return credentialsRepository.getOne(description.getAccount());
  }

  protected Service requireNativeServiceOwnership(String cluster, String serviceName) {
    if (!requireNativeServiceOwnership) {
      return null;
    }

    var response =
        getAmazonEcsClient()
            .describeServices(
                DescribeServicesRequest.builder()
                    .cluster(cluster)
                    .services(serviceName)
                    .includeWithStrings("TAGS")
                    .build());
    Service service =
        response == null ? null : response.services().stream().findFirst().orElse(null);
    if (service == null || !EcsNativeServiceTag.isNative(service.tags())) {
      throw new IllegalStateException(
          "ECS service "
              + serviceName
              + " is not marked as owned by ecs-native; refusing to write it.");
    }
    return service;
  }

  /** Attempts to look up the ARN of the deployment ECS just started; see the method below. */
  long serviceDeploymentPollMillis = 1000;

  /**
   * CreateService/UpdateService can return before ECS has attached the new service deployment, so
   * {@code currentServiceDeployment} may be blank. The deployment ARN pins every later wait and
   * lifecycle action, so poll DescribeServices briefly for it instead of leaving it unset.
   */
  protected String resolveCurrentServiceDeployment(Service service, String cluster) {
    if (StringUtils.isNotBlank(service.currentServiceDeployment())) {
      return service.currentServiceDeployment();
    }
    for (int attempt = 0; attempt < 10; attempt++) {
      Service described =
          getAmazonEcsClient()
              .describeServices(
                  DescribeServicesRequest.builder()
                      .cluster(cluster)
                      .services(service.serviceName())
                      .build())
              .services()
              .stream()
              .findFirst()
              .orElse(null);
      if (described != null && StringUtils.isNotBlank(described.currentServiceDeployment())) {
        return described.currentServiceDeployment();
      }
      try {
        Thread.sleep(serviceDeploymentPollMillis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
    return null;
  }

  void updateTaskStatus(String status) {
    getTask().updateStatus(basePhase, status);
  }
}
