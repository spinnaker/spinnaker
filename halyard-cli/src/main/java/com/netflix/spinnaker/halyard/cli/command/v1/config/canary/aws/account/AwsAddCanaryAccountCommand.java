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
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account.AbstractAddCanaryAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account.CanaryUtils;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.aws.AwsCanaryServiceIntegration;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryAccount;

@Parameters(separators = "=")
public class AwsAddCanaryAccountCommand extends AbstractAddCanaryAccountCommand {

  @Override
  protected String getServiceIntegration() {
    return "AWS";
  }

  @Parameter(
      names = "--bucket",
      description = CommonCanaryCommandProperties.BUCKET
  )
  private String bucket;

  @Parameter(
      names = "--root-folder",
      description = CommonCanaryCommandProperties.ROOT_FOLDER
  )
  private String rootFolder;

  @Override
  protected AbstractCanaryAccount buildAccount(Canary canary, String accountName) {
    AwsCanaryAccount account = (AwsCanaryAccount)new AwsCanaryAccount().setName(accountName);

    account.setBucket(bucket);
    account.setRootFolder(isSet(rootFolder) ? rootFolder : account.getRootFolder());

    AwsCanaryServiceIntegration awsCanaryServiceIntegration =
        (AwsCanaryServiceIntegration)CanaryUtils.getServiceIntegrationByClass(canary, AwsCanaryServiceIntegration.class);

    if (awsCanaryServiceIntegration.isS3Enabled()) {
      account.getSupportedTypes().add(AbstractCanaryServiceIntegration.SupportedTypes.CONFIGURATION_STORE);
      account.getSupportedTypes().add(AbstractCanaryServiceIntegration.SupportedTypes.OBJECT_STORE);
    }

    return account;
  }

  @Override
  protected AbstractCanaryAccount emptyAccount() {
    return new GoogleCanaryAccount();
  }
}
