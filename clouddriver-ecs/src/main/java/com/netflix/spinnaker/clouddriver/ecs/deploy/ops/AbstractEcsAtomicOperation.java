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

import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.ecs.AmazonECS;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.AbstractECSDescription;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractEcsAtomicOperation<T extends AbstractECSDescription, K>
    implements AtomicOperation<K> {
  private final String basePhase;
  @Autowired AmazonClientProvider amazonClientProvider;
  @Autowired CredentialsRepository<NetflixECSCredentials> credentialsRepository;
  @Autowired ContainerInformationService containerInformationService;
  T description;

  AbstractEcsAtomicOperation(T description, String basePhase) {
    this.description = description;
    this.basePhase = basePhase;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  String getCluster(String service, String account) {
    String region = getRegion();
    return containerInformationService.getClusterName(service, account, region);
  }

  AmazonECS getAmazonEcsClient() {
    String region = getRegion();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonEcs(credentialAccount, region, false);
  }

  AWSApplicationAutoScaling getAmazonApplicationAutoScalingClient() {
    String region = getRegion();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonApplicationAutoScaling(credentialAccount, region, false);
  }

  protected String getRegion() {
    return description.getRegion();
  }

  AmazonCredentials getCredentials() {
    return credentialsRepository.getOne(description.getAccount());
  }

  void updateTaskStatus(String status) {
    getTask().updateStatus(basePhase, status);
  }
}
