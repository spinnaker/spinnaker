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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.dockerRegistry;

import com.beust.jcommander.Parameter;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractEditAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryAccount;
import java.util.ArrayList;
import java.util.List;

public class DockerRegistryEditAccountCommand extends AbstractEditAccountCommand<DockerRegistryAccount> {
  protected String getProviderName() {
    return "dockerRegistry";
  }

  @Parameter(
      names = "--address",
      description = DockerRegistryCommandProperties.ADDRESS_DESCRIPTION
  )
  private String address;

  @Parameter(
      names = "--repositories",
      variableArity = true,
      description = DockerRegistryCommandProperties.REPOSITORIES_DESCRIPTION
  )
  private List<String> repositories = new ArrayList<>();

  @Parameter(
      names = "--add-repository",
      description = "Add this repository to the list of repositories to cache images from."
  )
  private String addRepository;

  @Parameter(
      names = "--remove-repository",
      description = "Remove this repository to the list of repositories to cache images from."
  )
  private String removeRepository;

  @Parameter(
      names = "--password",
      password = true,
      description = DockerRegistryCommandProperties.PASSWORD_DESCRIPTION
  )
  private String password;

  @Parameter(
      names = "--password-file",
      description = DockerRegistryCommandProperties.PASSWORD_FILE_DESCRIPTION
  )
  private String passwordFile;

  @Parameter(
      names = "--username",
      description = DockerRegistryCommandProperties.USERNAME_DESCRIPTION
  )
  private String username;

  @Parameter(
      names = "--email",
      description = DockerRegistryCommandProperties.EMAIL_DESCRIPTION
  )
  private String email;

  @Override
  protected Account editAccount(DockerRegistryAccount account) {
    account.setAddress(isSet(address) ? address : account.getAddress());

    try {
      account.setRepositories(
          updateStringList(account.getRepositories(), repositories, addRepository, removeRepository));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Set either --repositories or --[add/remove]-repository");
    }

    boolean passwordSet = isSet(password);
    boolean passwordFileSet = isSet(passwordFile);

    if (passwordSet && passwordFileSet) {
      throw new IllegalArgumentException("Set either --password or --password-file");
    } else if (passwordSet) {
      account.setPassword(password);
      account.setPasswordFile(null);
    } else if (passwordFileSet) {
      account.setPassword(null);
      account.setPasswordFile(passwordFile);
    }

    account.setUsername(isSet(username) ? username : account.getUsername());

    account.setEmail(isSet(email) ? email : account.getEmail());

    return account;
  }
}
