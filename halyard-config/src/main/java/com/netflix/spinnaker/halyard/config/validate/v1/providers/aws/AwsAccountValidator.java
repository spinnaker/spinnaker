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
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.AssumeRoleAmazonCredentials;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AwsAccountValidator extends Validator<AwsAccount> {
  @Override
  public void validate(ConfigProblemSetBuilder p, AwsAccount n) {
    DaemonTaskHandler.message(String.format("Validating AWS account named %s with %s", n.getNodeName(), getClass().getSimpleName()));

    AWSCredentialsProvider credentialsProvider = getAwsCredentialsProvider(n.getAccessKeyId(), n.getSecretKey());
    AmazonClientProvider amazonClientProvider = new AmazonClientProvider();
    AmazonCredentials amazonCredentials;

    // TODO The required (third) constructor in *AmazonCredentials is package protected.
    // We're abusing the second constructor here until the required constructor is changed to public.
    if (StringUtils.isNotBlank(n.getAssumeRole())) {
      amazonCredentials = new AssumeRoleAmazonCredentials(new AssumeRoleAmazonCredentials(n.getName(), "", "", n.getAccountId(), null,
        convert(n.getRegions()), null, n.getRequiredGroupMembership(), null, n.getAssumeRole(), null), credentialsProvider);
    } else {
      amazonCredentials = new AmazonCredentials(new AmazonCredentials(n.getName(), "", "", n.getAccountId(), null,
        convert(n.getRegions()), null, n.getRequiredGroupMembership(), null), credentialsProvider);
    }

    getRegionsOrDefault(n.getRegions()).forEach(awsRegion -> {
      AmazonEC2 ec2Client = amazonClientProvider.getAmazonEC2(amazonCredentials.getCredentialsProvider(), awsRegion.getName());
      try {
        ec2Client.describeRegions();
      } catch (Exception e) {
        if (StringUtils.isNotBlank(n.getAssumeRole())) {
          p.addProblem(Severity.WARNING, "Failed to validate AWS account in region \"" + awsRegion.getName() +
            "\", assuming role \"" + n.getAssumeRole() + "\". This might indicate an error. Error message: " + e.getMessage());
        } else {
          p.addProblem(Severity.ERROR, "Failed to validate AWS account in region \"" + awsRegion.getName() +
            "\". Error message: " + e.getMessage());
        }
      }
    });
  }

  public static AWSCredentialsProvider getAwsCredentialsProvider(String accessKeyId, String secretKey) {
    if (accessKeyId != null && secretKey != null) {
      return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKeyId, secretKey));
    } else {
      return DefaultAWSCredentialsProviderChain.getInstance();
    }
  }

  private List<AmazonCredentials.AWSRegion> convert(List<AwsAccount.AwsRegion> regions) {
    if (regions == null) {
      return null;
    }
    return regions.stream()
      .map(region -> new AmazonCredentials.AWSRegion(region.getName(), null))
      .collect(Collectors.toList());
  }

  private List<AwsAccount.AwsRegion> getRegionsOrDefault(List<AwsAccount.AwsRegion> regions) {
    return regions.isEmpty() ? Collections.singletonList(new AwsAccount.AwsRegion().setName(Regions.DEFAULT_REGION.getName())) : regions;
  }
}
