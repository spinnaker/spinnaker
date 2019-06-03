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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.AbstractConfigCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.iap.IAPCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.ldap.LdapCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.oauth2.OAuth2Command;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.saml.SamlCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.x509.X509Command;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Parameters(separators = "=")
@Data
@EqualsAndHashCode(callSuper = false)
public class AuthnCommand extends AbstractConfigCommand {
  String commandName = "authn";

  String shortDescription = "Configure your authentication settings for Spinnaker.";

  // This merits a better description.
  String longDescription =
      "This set of commands allows you to configure how users can authenticate against Spinnaker.";

  @Override
  protected void executeThis() {
    showHelp();
  }

  public AuthnCommand() {
    registerSubcommand(new OAuth2Command());
    registerSubcommand(new SamlCommand());
    registerSubcommand(new LdapCommand());
    registerSubcommand(new X509Command());
    registerSubcommand(new IAPCommand());
  }
}
