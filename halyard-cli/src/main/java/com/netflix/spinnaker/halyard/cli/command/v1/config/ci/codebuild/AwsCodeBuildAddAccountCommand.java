/*
 * Copyright 2020 Amazon.com, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.ci.codebuild;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.ci.codebuild.AwsCodeBuildAccount;

@Parameters(separators = "=")
public class AwsCodeBuildAddAccountCommand extends AbstractAddAccountCommand {
  protected String getCiName() {
    return "codebuild";
  }

  @Override
  protected String getCiFullName() {
    return "AWS CodeBuild";
  }

  @Parameter(
      names = "--account-id",
      required = true,
      description = AwsCodeBuildCommandProperties.ACCOUNT_ID_DESCRIPTION)
  private String accountId;

  @Parameter(
      names = "--assume-role",
      required = true,
      description = AwsCodeBuildCommandProperties.ASSUME_ROLE_DESCRIPTION)
  private String assumeRole;

  @Parameter(
      names = "--region",
      required = true,
      description = AwsCodeBuildCommandProperties.REGION_DESCRIPTION)
  private String region;

  @Override
  protected AwsCodeBuildAccount buildAccount(String accountName) {
    return new AwsCodeBuildAccount()
        .setName(accountName)
        .setAccountId(accountId)
        .setAssumeRole(assumeRole)
        .setRegion(region);
  }
}
