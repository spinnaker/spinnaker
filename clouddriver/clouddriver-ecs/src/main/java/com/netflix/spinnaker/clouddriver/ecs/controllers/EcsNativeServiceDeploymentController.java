/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.controllers;

import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServiceDeploymentStatus;
import com.netflix.spinnaker.clouddriver.ecs.security.EcsCredentialsResolver;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.time.Instant;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeServiceDeploymentsRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServiceDeploymentsResponse;
import software.amazon.awssdk.services.ecs.model.DescribeServiceRevisionsRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServiceRevisionsResponse;
import software.amazon.awssdk.services.ecs.model.ServiceDeployment;
import software.amazon.awssdk.services.ecs.model.ServiceRevision;

@RestController
@RequestMapping("/ecs-native/serverGroups/{account}/{region}/{serverGroupName}")
public class EcsNativeServiceDeploymentController {

  private final CredentialsRepository<NetflixECSCredentials> credentialsRepository;
  private final AmazonClientProvider amazonClientProvider;
  private final ServiceCacheClient serviceCacheClient;

  @Autowired
  public EcsNativeServiceDeploymentController(
      CredentialsRepository<NetflixECSCredentials> credentialsRepository,
      AmazonClientProvider amazonClientProvider,
      ServiceCacheClient serviceCacheClient) {
    this.credentialsRepository = credentialsRepository;
    this.amazonClientProvider = amazonClientProvider;
    this.serviceCacheClient = serviceCacheClient;
  }

  /**
   * Resolves an ECS account by name, tolerating case differences. Callers of this ecs-native-only
   * endpoint may send the account as displayed (e.g. upper-case) while the credential is registered
   * lower-case, and {@code CredentialsRepository#getOne} is case-sensitive. The tolerance is
   * intentionally limited to this endpoint; classic {@code ecs} read paths keep exact-match account
   * resolution.
   */
  private NetflixECSCredentials resolveCredentials(String account) {
    return EcsCredentialsResolver.resolve(credentialsRepository, account);
  }

  /**
   * Returns the ECS service deployment identified by the ARN produced by the write operation. The
   * expected identity is mandatory: selecting the current PRIMARY deployment would allow an ECS
   * rollback deployment to appear successful for the failed deployment that preceded it.
   */
  @RequestMapping(value = "/deploymentStatus", method = RequestMethod.GET)
  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  public ResponseEntity<?> getDeploymentStatus(
      @PathVariable String account,
      @PathVariable String region,
      @PathVariable String serverGroupName,
      @RequestParam(name = "expectedServiceDeploymentArn", required = false)
          String expectedServiceDeploymentArn) {
    if (expectedServiceDeploymentArn == null || expectedServiceDeploymentArn.isBlank()) {
      return new ResponseEntity<>(
          "expectedServiceDeploymentArn is required to identify the ecs-native deployment",
          HttpStatus.BAD_REQUEST);
    }

    NetflixECSCredentials credentials = resolveCredentials(account);
    if (credentials == null) {
      return new ResponseEntity<>(
          String.format("Account %s is not an ECS account", account), HttpStatus.BAD_REQUEST);
    }
    String resolvedAccount = credentials.getName();

    Optional<Service> cachedService =
        Optional.ofNullable(
            serviceCacheClient.get(Keys.getServiceKey(resolvedAccount, region, serverGroupName)));
    if (cachedService.isEmpty()) {
      return new ResponseEntity<>(
          String.format(
              "Server group %s was not found in account %s / region %s",
              serverGroupName, account, region),
          HttpStatus.NOT_FOUND);
    }

    // The expected ARN is already known, so describe exactly that deployment and its target
    // revision. Listing the service's deployment history and describing every entry would exceed
    // the DescribeServiceDeployments / DescribeServiceRevisions limit of 20 ARNs per request once a
    // durable service accumulates history, and the expected deployment could fall off the first
    // page anyway.
    EcsClient ecs = amazonClientProvider.getAmazonEcsV2(credentials, region);
    DescribeServiceDeploymentsResponse described =
        ecs.describeServiceDeployments(
            DescribeServiceDeploymentsRequest.builder()
                .serviceDeploymentArns(expectedServiceDeploymentArn)
                .build());
    Optional<ServiceDeployment> deployment =
        described.serviceDeployments().stream()
            .filter(
                candidate -> expectedServiceDeploymentArn.equals(candidate.serviceDeploymentArn()))
            .findFirst();
    if (deployment.isEmpty()) {
      // Not visible yet right after create/update; Orca retries on 404.
      return notFound(
          serverGroupName,
          "ECS has not exposed service deployment " + expectedServiceDeploymentArn);
    }

    ServiceDeployment selectedDeployment = deployment.get();
    if (!belongsTo(selectedDeployment, cachedService.get())) {
      // A permanent mismatch, not a visibility delay: fail rather than let the caller retry.
      return new ResponseEntity<>(
          String.format(
              "Service deployment %s does not belong to server group %s",
              expectedServiceDeploymentArn, serverGroupName),
          HttpStatus.CONFLICT);
    }
    if (selectedDeployment.targetServiceRevision() == null
        || selectedDeployment.targetServiceRevision().arn() == null) {
      return notFound(
          serverGroupName,
          "ECS service deployment " + expectedServiceDeploymentArn + " has no target revision");
    }
    String targetRevisionArn = selectedDeployment.targetServiceRevision().arn();
    DescribeServiceRevisionsResponse revisions =
        ecs.describeServiceRevisions(
            DescribeServiceRevisionsRequest.builder()
                .serviceRevisionArns(targetRevisionArn)
                .build());
    ServiceRevision selectedRevision =
        revisions.serviceRevisions().stream()
            .filter(revision -> targetRevisionArn.equals(revision.serviceRevisionArn()))
            .findFirst()
            .orElse(null);
    return new ResponseEntity<>(
        toStatus(
            serverGroupName,
            cachedService.get().getClusterArn(),
            selectedDeployment,
            selectedRevision),
        HttpStatus.OK);
  }

  /**
   * Binds the caller-supplied deployment ARN to the requested server group. The service ARN is
   * authoritative when the cache has it; the cluster ARN must match whenever both sides carry one.
   */
  private static boolean belongsTo(ServiceDeployment deployment, Service cachedService) {
    String cachedServiceArn = cachedService.getServiceArn();
    String cachedClusterArn = cachedService.getClusterArn();
    if (StringUtils.isNotBlank(cachedClusterArn)
        && StringUtils.isNotBlank(deployment.clusterArn())
        && !cachedClusterArn.equals(deployment.clusterArn())) {
      return false;
    }
    if (StringUtils.isNotBlank(cachedServiceArn)) {
      return cachedServiceArn.equals(deployment.serviceArn());
    }
    // Without a cached service ARN, only the cluster can be compared; require it to be present.
    return StringUtils.isNotBlank(cachedClusterArn)
        && cachedClusterArn.equals(deployment.clusterArn());
  }

  private static ResponseEntity<String> notFound(String serverGroupName, String reason) {
    return new ResponseEntity<>(
        String.format("Server group %s: %s", serverGroupName, reason), HttpStatus.NOT_FOUND);
  }

  private static EcsServiceDeploymentStatus toStatus(
      String serviceName,
      String clusterArn,
      ServiceDeployment deployment,
      ServiceRevision targetRevision) {
    EcsServiceDeploymentStatus status = new EcsServiceDeploymentStatus();
    status.setServiceName(serviceName);
    status.setClusterArn(clusterArn);
    status.setServiceDeploymentArn(deployment.serviceDeploymentArn());
    status.setDeploymentId(deployment.serviceDeploymentArn());
    status.setStatus(deployment.statusAsString());
    status.setStatusReason(deployment.statusReason());
    status.setRolloutState(deployment.statusAsString());
    status.setRolloutStateReason(deployment.statusReason());
    status.setLifecycleStage(deployment.lifecycleStageAsString());
    status.setLifecycleHookDetails(
        deployment.lifecycleHookDetails().stream()
            .map(
                hook -> {
                  EcsServiceDeploymentStatus.LifecycleHook detail =
                      new EcsServiceDeploymentStatus.LifecycleHook();
                  detail.setHookId(hook.hookId());
                  detail.setStatus(hook.statusAsString());
                  detail.setTargetType(hook.targetTypeAsString());
                  detail.setExpiresAt(toEpochMillis(hook.expiresAt()));
                  detail.setTimeoutAction(hook.timeoutActionAsString());
                  return detail;
                })
            .toList());
    if (targetRevision != null) {
      status.setTargetServiceRevisionArn(targetRevision.serviceRevisionArn());
      status.setTargetTaskDefinition(targetRevision.taskDefinition());
    }
    status.setCreatedAt(toEpochMillis(deployment.createdAt()));
    status.setStartedAt(toEpochMillis(deployment.startedAt()));
    status.setFinishedAt(toEpochMillis(deployment.finishedAt()));
    status.setUpdatedAt(toEpochMillis(deployment.updatedAt()));
    return status;
  }

  private static Long toEpochMillis(Instant instant) {
    return instant == null ? null : instant.toEpochMilli();
  }
}
