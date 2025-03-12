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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.google;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.AbstractEditRoleProviderCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.LocalFileConverter;
import com.netflix.spinnaker.halyard.config.model.v1.security.GoogleRoleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.security.GroupMembership;
import com.netflix.spinnaker.halyard.config.model.v1.security.RoleProvider;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Parameters(separators = "=")
public class EditGoogleRoleProviderCommand
    extends AbstractEditRoleProviderCommand<GoogleRoleProvider> {
  GroupMembership.RoleProviderType roleProviderType = GroupMembership.RoleProviderType.GOOGLE;

  @Parameter(
      names = "--credential-path",
      converter = LocalFileConverter.class,
      description =
          "A path to a valid json service account that can authenticate against the Google role provider.")
  private String credentialPath;

  @Parameter(
      names = "--admin-username",
      description = "Your role provider's admin username e.g. admin@myorg.net")
  private String adminUsername;

  @Parameter(
      names = "--domain",
      description = "The domain your role provider is configured for e.g. myorg.net.")
  private String domain;

  @Override
  protected RoleProvider editRoleProvider(GoogleRoleProvider roleProvider) {
    roleProvider.setCredentialPath(
        isSet(credentialPath) ? credentialPath : roleProvider.getCredentialPath());
    roleProvider.setAdminUsername(
        isSet(adminUsername) ? adminUsername : roleProvider.getAdminUsername());
    roleProvider.setDomain(isSet(domain) ? domain : roleProvider.getDomain());
    return roleProvider;
  }
}
