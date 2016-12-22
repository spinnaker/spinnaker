/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.providers.google;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import java.util.ArrayList;
import java.util.List;

@Parameters()
public class GoogleAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return "google";
  }

  @Parameter(
      names = "--project",
      required = true,
      description = "The Google Cloud Platform project this Spinnaker account will manage."
  )
  private String project;

  @Parameter(
      names = "--json-path",
      description = "The path to a JSON service account that Spinnaker will use as credentials. "
          + "This is only needed if Spinnaker is not deployed on a Google Compute Engine VM, "
          + "or needs permissions not afforded to the VM it is running on. "
          + "See https://cloud.google.com/compute/docs/access/service-accounts for more information."
  )
  private String jsonPath;

  @Parameter(
      names = "--image-projects",
      variableArity = true,
      description = "A list of Google Cloud Platform projects Spinnaker will be able to cache and deploy images from. "
          + "Leaving this blank defaults to the current project."
  )
  private List<String> imageProjects = new ArrayList<>();

  @Parameter(
      names = "--alpha-listed",
      description = "Provide this flag if your project has access to alpha features "
          + "and you want Spinnaker to take advantage of them."
  )
  private boolean alphaListed = false;

  @Override
  protected Account buildAccount(String accountName) {
    GoogleAccount account = (GoogleAccount) new GoogleAccount().setName(accountName);
    account.setJsonPath(jsonPath)
        .setAlphaListed(alphaListed)
        .setProject(project)
        .setImageProjects(imageProjects);

    return account;
  }
}
