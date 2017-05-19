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
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import org.springframework.stereotype.Component;

@Component
public class AwsAccountValidator extends Validator<AwsAccount> {
  @Override
  public void validate(ConfigProblemSetBuilder p, AwsAccount n) {
    DaemonTaskHandler.message(String.format("Validating %s with %s", n.getNodeName(), getClass().getSimpleName()));

    AWSCredentialsProvider credentialsProvider = DefaultAWSCredentialsProviderChain.getInstance();

    n.getRegions().forEach(awsRegion -> {
      AmazonEC2 ec2Client = AmazonEC2ClientBuilder.standard()
        .withCredentials(credentialsProvider)
        .withRegion(awsRegion.getName())
        .build();
      try {
        ec2Client.describeRegions();
      } catch (Exception e) {
        p.addProblem(Severity.ERROR, "Failed to validate AWS account in region \"" + awsRegion.getName() +
          "\". Error message: " + e.getMessage());
      }
    });
  }
}
