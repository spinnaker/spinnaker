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

package com.netflix.spinnaker.halyard.cli.command.v1.providers.dockerRegistry;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.providers.AbstractAddAccountCommand;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryAccount;
import java.util.ArrayList;
import java.util.List;

@Parameters()
public class DockerAddAccountCommand extends AbstractAddAccountCommand {
  protected String getProviderName() {
    return "dockerRegistry";
  }

  @Parameter(
      names = "--address",
      required = true,
      description = "The registry address you want to pull and deploy images from. For example:\n\n"
          + "  index.docker.io     - DockerHub\n"
          + "  quay.io             - Quay\n"
          + "  gcr.io              - Google Container Registry (GCR)\n"
          + "  [us|eu|asia].gcr.io - Regional GCR\n"
          + "  localhost           - Locally deployed registry"
  )
  private String address="gcr.io";

  @Parameter(
      names = "--repositories",
      variableArity = true,
      description = "An optional list of repositories to cache images from. "
          + "If not provided, Spinnaker will attempt to read accessible repositories from the registries _catalog endpoint"
  )
  private List<String> repositories = new ArrayList<>();

  @Parameter(
      names = "--password",
      password = true,
      description = "Your docker registry password"
  )
  private String password;

  @Parameter(
      names = "--password-file",
      description = "The path to a file containing your docker password in plaintext (not a docker/config.json file)"
  )
  private String passwordFile;

  @Parameter(
      names = "--username",
      description = "Your docker registry username"
  )
  private String username;

  @Parameter(
      names = "--email",
      description = "Your docker registry email (often this only needs to be well-formed, rather than be a real address)"
  )
  private String email="fake.email@spinnaker.io";

  @Override
  protected Account buildAccount(String accountName) {
    DockerRegistryAccount account = (DockerRegistryAccount) new DockerRegistryAccount().setName(accountName);

    account.setAddress(address)
        .setRepositories(repositories)
        .setPassword(password)
        .setPasswordFile(passwordFile)
        .setEmail(email);

    return account;
  }
}
