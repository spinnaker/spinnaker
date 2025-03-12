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
 *
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.file.FileRoleProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.github.GithubRoleProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.google.GoogleRoleProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.ldap.LdapRoleProviderCommand;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Parameters(separators = "=")
@Data
@EqualsAndHashCode(callSuper = false)
public class AuthzCommand extends AbstractConfigCommand {
  String commandName = "authz";

  String shortDescription = "Configure your authorization settings for Spinnaker.";

  // This merits a better description.
  String longDescription =
      "This set of commands allows you to configure what resources users of Spinnaker can read and modify.";

  @Override
  protected void executeThis() {
    showHelp();
  }

  public AuthzCommand() {
    registerSubcommand(new AuthzEditCommand());
    registerSubcommand(new EnableDisableAuthzCommandBuilder().setEnable(true).build());
    registerSubcommand(new EnableDisableAuthzCommandBuilder().setEnable(false).build());
    registerSubcommand(new GoogleRoleProviderCommand());
    registerSubcommand(new GithubRoleProviderCommand());
    registerSubcommand(new FileRoleProviderCommand());
    registerSubcommand(new LdapRoleProviderCommand());
  }
}
