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

package com.netflix.spinnaker.halyard.cli.command.v1.providers.appengine;

import com.beust.jcommander.Parameter;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.google.CommonGoogleCommandProperties;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.appengine.AppengineAccount;

public class AppengineEditAccountCommand extends AbstractEditAccountCommand<AppengineAccount> {
  @Override
  protected String getProviderName() {
    return "appengine";
  }

  @Parameter(
      names = "--project",
      required = true,
      description = CommonGoogleCommandProperties.PROJECT_DESCRIPTION
  )
  private String project;

  @Parameter(
      names = "--json-path",
      description = CommonGoogleCommandProperties.JSON_PATH_DESCRIPTION
  )
  private String jsonPath;

  @Override
  protected Account editAccount(AppengineAccount account) {
    account.setJsonPath(isSet(jsonPath) ? jsonPath : account.getJsonPath());
    account.setProject(isSet(project) ? project : account.getProject());

    return account;
  }
}
