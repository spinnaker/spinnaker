/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.s3;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.AbstractAddArtifactAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.aws.AwsCommandProperties;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.s3.S3ArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;

@Parameters(separators = "=")
public class S3AddArtifactAccountCommand extends AbstractAddArtifactAccountCommand {
  @Parameter(
      names = "--api-endpoint",
      description = S3ArtifactCommandProperties.API_ENDPOINT_DESCRIPTION)
  private String apiEndpoint;

  @Parameter(
      names = "--api-region",
      description = S3ArtifactCommandProperties.API_REGION_DESCRIPTION)
  private String apiRegion;

  @Parameter(names = "--region", description = S3ArtifactCommandProperties.REGION_DESCRIPTION)
  private String region;

  @Parameter(
      names = "--aws-access-key-id",
      description = AwsCommandProperties.ACCESS_KEY_ID_DESCRIPTION)
  private String awsAccessKeyId;

  @Parameter(
      names = "--aws-secret-access-key",
      description = AwsCommandProperties.SECRET_KEY_DESCRIPTION,
      password = true)
  private String awsSecretAccessKey;

  @Override
  protected ArtifactAccount buildArtifactAccount(String accountName) {
    S3ArtifactAccount artifactAccount = new S3ArtifactAccount().setName(accountName);
    artifactAccount
        .setApiEndpoint(apiEndpoint)
        .setApiRegion(apiRegion)
        .setRegion(region)
        .setAwsAccessKeyId(awsAccessKeyId)
        .setAwsSecretAccessKey(awsSecretAccessKey);
    return artifactAccount;
  }

  @Override
  protected ArtifactAccount emptyArtifactAccount() {
    return new S3ArtifactAccount();
  }

  @Override
  protected String getArtifactProviderName() {
    return "s3";
  }
}
