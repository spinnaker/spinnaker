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
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.AbstractEditCiCommand;
import com.netflix.spinnaker.halyard.config.model.v1.ci.codebuild.AwsCodeBuild;
import com.netflix.spinnaker.halyard.config.model.v1.ci.codebuild.AwsCodeBuildAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.Ci;
import lombok.Getter;

@Parameters(separators = "=")
public class AwsCodeBuildEditCiCommand
    extends AbstractEditCiCommand<AwsCodeBuildAccount, AwsCodeBuild> {
  protected String getCiName() {
    return "codebuild";
  }

  @Override
  protected String getCiFullName() {
    return "AWS CodeBuild";
  }

  @Getter String shortDescription = "Set CI provider-wide properties for " + getCiFullName();

  @Parameter(
      names = "--access-key-id",
      description = AwsCodeBuildCommandProperties.ACCESS_KEY_ID_DESCRIPTION)
  private String accessKeyId;

  @Parameter(
      names = "--secret-access-key",
      description = AwsCodeBuildCommandProperties.SECRET_KEY_DESCRIPTION,
      password = true)
  private String secretAccessKey;

  @Override
  protected Ci editCi(AwsCodeBuild ci) {
    ci.setAccessKeyId(isSet(accessKeyId) ? accessKeyId : ci.getAccessKeyId());
    ci.setSecretAccessKey(isSet(secretAccessKey) ? secretAccessKey : ci.getSecretAccessKey());
    return ci;
  }
}
