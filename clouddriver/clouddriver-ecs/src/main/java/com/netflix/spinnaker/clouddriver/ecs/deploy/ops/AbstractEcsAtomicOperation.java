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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Deployment;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.EcsException;
import software.amazon.awssdk.services.ecs.model.ListServiceDeploymentsRequest;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.ServiceDeploymentBrief;

public abstract class AbstractEcsAtomicOperation<T extends AbstractECSDescription, K>
    implements AtomicOperation<K> {
  private final Logger log = LoggerFactory.getLogger(getClass());
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
   * ECS listed the service deployment of a freshly created service about 13s after CreateService,
   * so poll for up to 30s.
   */
  int serviceDeploymentPollAttempts = 30;

  /**
   * CreateService/UpdateService can return before ECS has attached the new service deployment, so
   * {@code currentServiceDeployment} may be blank. The deployment ARN pins every later wait and
   * lifecycle action, so poll briefly for it instead of leaving it unset.
   *
   * <p>For a service created with CreateService, ECS leaves {@code currentServiceDeployment} unset
   * even after the deployment has finished. The deployment is then found via
   * ListServiceDeployments: the one whose target service revision belongs to the service's PRIMARY
   * deployment.
   */
  protected String resolveCurrentServiceDeployment(Service service, String cluster) {
    if (StringUtils.isNotBlank(service.currentServiceDeployment())) {
      return service.currentServiceDeployment();
    }
    for (int attempt = 0; attempt < serviceDeploymentPollAttempts; attempt++) {
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
      String listed = findServiceDeploymentOfPrimary(described, cluster);
      if (listed != null) {
        return listed;
      }
      try {
        Thread.sleep(serviceDeploymentPollMillis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
    log.warn(
        "ECS reported no service deployment for {} in {} after {} attempts {}ms apart; waits and"
            + " lifecycle actions that need the pinned deployment ARN will fail",
        service.serviceName(),
        cluster,
        serviceDeploymentPollAttempts,
        serviceDeploymentPollMillis);
    return null;
  }

  /**
   * The service deployment whose target revision ARN ends with the id of the service's PRIMARY
   * deployment ({@code ecs-svc/<id>} pairs with {@code service-revision/<cluster>/<service>/<id>}),
   * or null when ECS has not listed it yet or listing is not permitted.
   */
  private String findServiceDeploymentOfPrimary(Service described, String cluster) {
    if (described == null || described.deployments() == null) {
      return null;
    }
    String revisionId =
        described.deployments().stream()
            .filter(d -> "PRIMARY".equals(d.status()))
            .map(Deployment::id)
            .filter(StringUtils::isNotBlank)
            .map(id -> id.substring(id.lastIndexOf('/') + 1))
            .findFirst()
            .orElse(null);
    if (revisionId == null) {
      return null;
    }
    try {
      return getAmazonEcsClient()
          .listServiceDeployments(
              ListServiceDeploymentsRequest.builder()
                  .cluster(cluster)
                  .service(described.serviceName())
                  .build())
          .serviceDeployments()
          .stream()
          .filter(
              d ->
                  d.targetServiceRevisionArn() != null
                      && d.targetServiceRevisionArn().endsWith("/" + revisionId))
          .map(ServiceDeploymentBrief::serviceDeploymentArn)
          .filter(StringUtils::isNotBlank)
          .findFirst()
          .orElse(null);
    } catch (EcsException e) {
      log.warn(
          "Could not list service deployments of {} in {}: {}",
          described.serviceName(),
          cluster,
          e.getMessage());
      return null;
    }
  }

  void updateTaskStatus(String status) {
    getTask().updateStatus(basePhase, status);
  }
}
