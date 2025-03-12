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
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.account.AbstractEditCanaryAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.canary.google.CommonCanaryGoogleCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google.CommonGoogleCommandProperties;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.cli.services.v1.Daemon;
import com.netflix.spinnaker.halyard.cli.services.v1.OperationHandler;
import com.netflix.spinnaker.halyard.config.model.v1.canary.AbstractCanaryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.canary.Canary;
import com.netflix.spinnaker.halyard.config.model.v1.canary.google.GoogleCanaryAccount;

@Parameters(separators = "=")
public class GoogleEditCanaryAccountCommand
    extends AbstractEditCanaryAccountCommand<GoogleCanaryAccount> {

  @Override
  protected String getServiceIntegration() {
    return "Google";
  }

  @Parameter(
      names = "--project",
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
  protected AbstractCanaryAccount editAccount(GoogleCanaryAccount account) {
    account.setProject(isSet(project) ? project : account.getProject());
    account.setJsonPath(isSet(jsonPath) ? jsonPath : account.getJsonPath());
    account.setBucket(isSet(bucket) ? bucket : account.getBucket());
    account.setRootFolder(isSet(rootFolder) ? rootFolder : account.getRootFolder());
    account.setBucketLocation(isSet(bucketLocation) ? bucketLocation : account.getBucketLocation());

    String currentDeployment = getCurrentDeployment();
    // Disable validation here, since we don't want an illegal config to prevent us from fixing it.
    Canary canary =
        new OperationHandler<Canary>()
            .setFailureMesssage("Failed to get canary.")
            .setOperation(Daemon.getCanary(currentDeployment, false))
            .get();

    GoogleAddEditCanaryAccountUtils.updateSupportedTypes(canary, account);

    return account;
  }
}
