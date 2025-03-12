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

package com.netflix.spinnaker.halyard.cli.command.v1.config.canary.google.account;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.CommonCanaryCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account.AbstractAddCanaryAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.google.CommonCanaryGoogleCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google.CommonGoogleCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryAccount;

@Parameters(separators = "=")
public class GoogleAddCanaryAccountCommand extends AbstractAddCanaryAccountCommand {

  @Override
  protected String getServiceIntegration() {
    return "Google";
  }

  @Parameter(
      names = "--project",
      required = true,
      description = CommonCanaryGoogleCommandProperties.PROJECT_DESCRIPTION)
  private String project;

  @Parameter(
      names = "--json-path",
      converter = LocalFileConverter.class,
      description = CommonGoogleCommandProperties.JSON_PATH_DESCRIPTION)
  private String jsonPath;

  @Parameter(names = "--bucket", description = CommonCanaryCommandProperties.BUCKET)
  private String bucket;

  @Parameter(names = "--root-folder", description = CommonCanaryCommandProperties.ROOT_FOLDER)
  private String rootFolder;

  @Parameter(
      names = "--bucket-location",
      description = CommonCanaryGoogleCommandProperties.BUCKET_LOCATION)
  private String bucketLocation;

  @Override
  protected AbstractCanaryAccount buildAccount(Canary canary, String accountName) {
    GoogleCanaryAccount account =
        (GoogleCanaryAccount) new GoogleCanaryAccount().setName(accountName);
    account.setProject(project).setJsonPath(jsonPath);

    account.setBucket(bucket).setBucketLocation(bucketLocation);
    account.setRootFolder(isSet(rootFolder) ? rootFolder : account.getRootFolder());

    GoogleAddEditCanaryAccountUtils.updateSupportedTypes(canary, account);

    return account;
  }

  @Override
  protected AbstractCanaryAccount emptyAccount() {
    return new GoogleCanaryAccount();
  }
}
