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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.aws.account;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.CommonCanaryCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account.AbstractEditCanaryAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.aws.CommonCanaryAwsCommandProperties;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryAccount;

@Parameters(separators = "=")
public class AwsEditCanaryAccountCommand
    extends AbstractEditCanaryAccountCommand<AwsCanaryAccount> {

  @Override
  protected String getServiceIntegration() {
    return "AWS";
  }

  @Parameter(names = "--bucket", description = CommonCanaryCommandProperties.BUCKET)
  private String bucket;

  @Parameter(names = "--region", description = CommonCanaryAwsCommandProperties.REGION_DESCRIPTION)
  private String region;

  @Parameter(names = "--root-folder", description = CommonCanaryCommandProperties.ROOT_FOLDER)
  private String rootFolder;

  @Parameter(
      names = "--profile-name",
      description = CommonCanaryAwsCommandProperties.PROFILE_NAME_DESCRIPTION)
  private String profileName;

  @Parameter(
      names = "--endpoint",
      description = CommonCanaryAwsCommandProperties.ENDPOINT_DESCRIPTION)
  private String endpoint;

  @Parameter(
      names = "--access-key-id",
      description = CommonCanaryAwsCommandProperties.ACCESS_KEY_ID_DESCRIPTION)
  private String accessKeyId;

  @Parameter(
      names = "--secret-access-key",
      description = CommonCanaryAwsCommandProperties.SECRET_KEY_DESCRIPTION,
      password = true)
  private String secretAccessKey;

  @Override
  protected AbstractCanaryAccount editAccount(AwsCanaryAccount account) {
    account.setBucket(isSet(bucket) ? bucket : account.getBucket());
    account.setRegion(isSet(region) ? region : account.getRegion());
    account.setRootFolder(isSet(rootFolder) ? rootFolder : account.getRootFolder());
    account.setProfileName(isSet(profileName) ? profileName : account.getProfileName());
    account.setEndpoint(isSet(endpoint) ? endpoint : account.getEndpoint());
    account.setAccessKeyId(isSet(accessKeyId) ? accessKeyId : account.getAccessKeyId());
    account.setSecretAccessKey(
        isSet(secretAccessKey) ? secretAccessKey : account.getSecretAccessKey());

    return account;
  }
}
