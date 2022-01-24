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
 *
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.bitbucket;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.artifacts.account.AbstractArtifactEditAccountCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.artifacts.bitbucket.BitbucketArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;

@Parameters(separators = "=")
public class BitbucketEditArtifactAccountCommand
    extends AbstractArtifactEditAccountCommand<BitbucketArtifactAccount> {
  @Parameter(names = "--username", description = "Bitbucket username")
  private String username;

  @Parameter(names = "--password", password = true, description = "Bitbucket password")
  private String password;

  @Parameter(
      names = "--username-password-file",
      converter = LocalFileConverter.class,
      description = "File containing \"username:password\" to use for Bitbucket authentication")
  private String usernamePasswordFile;

  @Parameter(names = "--token", password = true, description = "Bitbucket Server token")
  private String token;

  @Parameter(
      names = "--token-file",
      converter = LocalFileConverter.class,
      description = "File containing a Bitbucket Server authentication token")
  private String tokenFile;

  @Override
  protected ArtifactAccount editArtifactAccount(BitbucketArtifactAccount account) {
    account.setUsername(isSet(username) ? username : account.getUsername());
    account.setPassword(isSet(password) ? password : account.getPassword());
    account.setUsernamePasswordFile(
        isSet(usernamePasswordFile) ? usernamePasswordFile : account.getUsernamePasswordFile());
    account.setToken(isSet(token) ? token : account.getToken());
    account.setTokenFile(isSet(tokenFile) ? tokenFile : account.getTokenFile());
    return account;
  }

  @Override
  protected String getArtifactProviderName() {
    return "bitbucket";
  }
}
