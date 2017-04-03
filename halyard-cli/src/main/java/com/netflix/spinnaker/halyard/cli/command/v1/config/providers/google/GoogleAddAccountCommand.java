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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.google;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.account.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.PathExpandingConverter;
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
      description = CommonGoogleCommandProperties.PROJECT_DESCRIPTION
  )
  private String project;

  @Parameter(
      names = "--json-path",
      converter = PathExpandingConverter.class,
      description = CommonGoogleCommandProperties.JSON_PATH_DESCRIPTION
  )
  private String jsonPath;

  @Parameter(
      names = "--image-projects",
      variableArity = true,
      description = GoogleCommandProperties.IMAGE_PROJECTS_DESCRIPTION
  )
  private List<String> imageProjects = new ArrayList<>();

  @Parameter(
      names = "--alpha-listed",
      description = GoogleCommandProperties.ALPHA_LISTED_DESCRIPTION
  )
  private boolean alphaListed = false;

  @Override
  protected Account buildAccount(String accountName) {
    GoogleAccount account = (GoogleAccount) new GoogleAccount().setName(accountName);
    account = (GoogleAccount) account.setJsonPath(jsonPath)
        .setProject(project);

    account.setAlphaListed(alphaListed)
        .setImageProjects(imageProjects);

    return account;
  }

  @Override
  protected Account emptyAccount() {
    return new GoogleAccount();
  }
}
