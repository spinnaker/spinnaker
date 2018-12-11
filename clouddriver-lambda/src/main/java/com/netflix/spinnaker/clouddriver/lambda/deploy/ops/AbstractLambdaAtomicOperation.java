/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.lambda.deploy.ops;

import com.amazonaws.services.lambda.AWSLambda;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.AbstractLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.LambdaFunctionProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractLambdaAtomicOperation<T extends AbstractLambdaFunctionDescription, K> implements AtomicOperation<K> {
  @Autowired
  AmazonClientProvider amazonClientProvider;

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  LambdaFunctionProvider lambdaFunctionProvider;

  private final String basePhase;

  T description;

  AbstractLambdaAtomicOperation(T description, String basePhase) {
    this.description = description;
    this.basePhase = basePhase;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  AWSLambda getLambdaClient() {
    String region = getRegion();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonLambda(credentialAccount,region);
  }

  protected String getRegion() {
    return description.getRegion();
  }

  protected AmazonCredentials getCredentials() {
    return (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
  }

  void updateTaskStatus(String status) {
    getTask().updateStatus(basePhase, status);
  }
}
