/*
 * Copyright 2017 Google, Inc.
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
import com.netflix.spinnaker.halyard.cli.command.v1.providers.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.CommonGoogleAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleAccount;
import java.util.ArrayList;
import java.util.List;

public class GoogleEditAccountCommand extends AbstractEditAccountCommand<GoogleAccount> {
  @Override
  protected String getProviderName() {
    return "google";
  }

  @Parameter(
      names = "--project",
      description = CommonGoogleCommandProperties.PROJECT_DESCRIPTION
  )
  private String project;

  @Parameter(
      names = "--json-path",
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
      names = "--add-image-project",
      description = "Add this image project to the list of image projects to cache and deploy images from."
  )
  private String addImageProject;

  @Parameter(
      names = "--remove-image-project",
      description = "Remove this image project from the list of image projects to cache and deploy images from."
  )
  private String removeImageProject;

  @Parameter(
      names = "--set-alpha-listed",
      description = GoogleCommandProperties.ALPHA_LISTED_DESCRIPTION,
      arity = 1
  )
  private Boolean alphaListed = null;

  @Override
  protected Account editAccount(GoogleAccount account) {
    account.setJsonPath(isSet(jsonPath) ? jsonPath : account.getJsonPath());
    account.setProject(isSet(project) ? project : account.getProject());
    account.setAlphaListed(alphaListed != null ? alphaListed : account.isAlphaListed());

    try {
      account.setImageProjects(
          updateStringList(account.getImageProjects(), imageProjects, addImageProject,
              removeImageProject));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Set either --image-projects or --[add/remove]-image-project");
    }

    return account;
  }
}
