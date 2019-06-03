/*
 * Copyright 2018 Joel Wilsson
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
 *
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.http;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.AbstractAddArtifactAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.http.HttpArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;

@Parameters(separators = "=")
public class HttpAddArtifactAccountCommand extends AbstractAddArtifactAccountCommand {
  @Parameter(names = "--username", description = "HTTP basic auth username")
  private String username;

  @Parameter(names = "--password", password = true, description = "HTTP basic auth password")
  private String password;

  @Parameter(
      names = "--username-password-file",
      converter = LocalFileConverter.class,
      description = "File containing \"username:password\" to use for HTTP basic auth")
  private String usernamePasswordFile;

  @Override
  protected ArtifactAccount buildArtifactAccount(String accountName) {
    HttpArtifactAccount artifactAccount = new HttpArtifactAccount().setName(accountName);
    artifactAccount
        .setUsername(username)
        .setPassword(password)
        .setUsernamePasswordFile(usernamePasswordFile);
    return artifactAccount;
  }

  @Override
  protected ArtifactAccount emptyArtifactAccount() {
    return new HttpArtifactAccount();
  }

  @Override
  protected String getArtifactProviderName() {
    return "http";
  }
}
