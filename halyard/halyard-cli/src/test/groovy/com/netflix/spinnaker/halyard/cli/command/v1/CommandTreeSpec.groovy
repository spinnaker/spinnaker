/*
 * Copyright 2017 Target, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1

import com.netflix.spinnaker.halyard.cli.command.v1.config.DeploymentEnvironmentCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.EditConfigCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.FeaturesCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.GenerateCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.MetricStoresCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.PersistentStorageCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.SecurityCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.VersionConfigCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.ci.CiCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.ProviderCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.api.ApiSecurityCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.AuthnCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.ldap.EditLdapCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.ldap.LdapCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.oauth2.EditOAuth2Command
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.oauth2.OAuth2Command
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.saml.EditSamlCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.saml.SamlCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.x509.X509Command
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.AuthzCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.ui.UiSecurityCommand
import com.netflix.spinnaker.halyard.cli.command.v1.config.stats.StatsCommand
import com.netflix.spinnaker.halyard.cli.command.v1.plugins.AddPluginCommand
import com.netflix.spinnaker.halyard.cli.command.v1.plugins.DeletePluginCommand
import com.netflix.spinnaker.halyard.cli.command.v1.plugins.ListPluginsCommand
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.AuthnMethodEnableDisableCommandBuilder.AuthnMethodEnableDisableCommand

class CommandTreeSpec extends Specification {

  /**
   * Documents and validates the tree structure of the hal command and all of its subcommands.
   * validation of each command's parameters will go elsewhere, but this could and should be the
   * one-stop spec for where each command is nested within the tree.
   *
   * Each row of the table validates two things about a subcommand:
   *   1. which parent command it lives under.
   *   2. by what name it can be invoked on the command line.
   *
   * commandClass is the simple class name of the parent command under test.
   * subcommandName is the name by which the subcommand is invoked at that command line.
   * subcommandClass is the implementing class of the subcommand.
   *
   * When adding a new subcommand to halyard, this will be a good place to begin: add your subcommand
   * under the parent where you want it, and test-drive the implementation from there.
   *
   * Note: This is only a partial spec so far. If everyone likes this direction then we will fill
   *       in all the other commands.
   */
  @Unroll()
  void "#commandClass.simpleName command includes subcommand #subcommandName"() {
    setup:
    NestableCommand command = commandClass.newInstance()

    when:
    def subcommands = command.subcommands

    then:
    subcommands.containsKey(subcommandName)
    subcommands[subcommandName].class == subcommandClass

    where:
    commandClass    | subcommandName  | subcommandClass

    HalCommand      | "admin"         | AdminCommand
    HalCommand      | "backup"        | BackupCommand
    HalCommand      | "config"        | ConfigCommand
    HalCommand      | "deploy"        | DeployCommand
    HalCommand      | "task"          | TaskCommand
    HalCommand      | "version"       | VersionCommand

    ConfigCommand   | "deploy"        | DeploymentEnvironmentCommand
    ConfigCommand   | "edit"          | EditConfigCommand
    ConfigCommand   | "features"      | FeaturesCommand
    ConfigCommand   | "generate"      | GenerateCommand
    ConfigCommand   | "metric-stores" | MetricStoresCommand
    ConfigCommand   | "storage"       | PersistentStorageCommand
    ConfigCommand   | "provider"      | ProviderCommand
    ConfigCommand   | "security"      | SecurityCommand
    ConfigCommand   | "version"       | VersionConfigCommand
    ConfigCommand   | "ci"            | CiCommand
    ConfigCommand   | "stats"         | StatsCommand

    SecurityCommand | "api"           | ApiSecurityCommand
    SecurityCommand | "authn"         | AuthnCommand
    SecurityCommand | "authz"         | AuthzCommand
    SecurityCommand | "ui"            | UiSecurityCommand

    AuthnCommand    | "oauth2"        | OAuth2Command
    AuthnCommand    | "saml"          | SamlCommand
    AuthnCommand    | "ldap"          | LdapCommand
    AuthnCommand    | "x509"          | X509Command

    OAuth2Command   | "disable"       | AuthnMethodEnableDisableCommand
    OAuth2Command   | "enable"        | AuthnMethodEnableDisableCommand
    OAuth2Command   | "edit"          | EditOAuth2Command

    SamlCommand     | "disable"       | AuthnMethodEnableDisableCommand
    SamlCommand     | "enable"        | AuthnMethodEnableDisableCommand
    SamlCommand     | "edit"          | EditSamlCommand

    LdapCommand     | "disable"       | AuthnMethodEnableDisableCommand
    LdapCommand     | "enable"        | AuthnMethodEnableDisableCommand
    LdapCommand     | "edit"          | EditLdapCommand

    PluginCommand   | "list"          | ListPluginsCommand
    PluginCommand   | "delete"        | DeletePluginCommand
    PluginCommand   | "add"           | AddPluginCommand
  }

}
