/*
 * Copyright 2017 Schibsted ASA.
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
 *
 */

package com.netflix.spinnaker.halyard.config.validate.v1.providers.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.services.v1.ProviderService;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AwsAccountValidator extends Validator<AwsAccount> {

  @Autowired ProviderService providerService;

  @Override
  public void validate(ConfigProblemSetBuilder p, AwsAccount awsAccount) {
    awsAccount.getLifecycleHooks().stream()
        .flatMap(AwsLifecycleHookValidation::getValidationErrors)
        .forEach(error -> p.addProblem(Severity.FATAL, error));
  }

  public static AWSCredentialsProvider getAwsCredentialsProvider(
      String accessKeyId, String secretKey) {
    if (accessKeyId != null && secretKey != null) {
      return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKeyId, secretKey));
    } else {
      return DefaultAWSCredentialsProviderChain.getInstance();
    }
  }
}
