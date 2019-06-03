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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.github;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.AbstractEditRoleProviderCommand;
import com.netflix.spinnaker.halyard.config.model.v1.security.GithubRoleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.security.GroupMembership;
import com.netflix.spinnaker.halyard.config.model.v1.security.RoleProvider;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Parameters(separators = "=")
public class EditGithubRoleProviderCommand
    extends AbstractEditRoleProviderCommand<GithubRoleProvider> {
  private final GroupMembership.RoleProviderType roleProviderType =
      GroupMembership.RoleProviderType.GITHUB;

  @Parameter(
      names = "--baseUrl",
      description =
          "Used if using GitHub enterprise some other non github.com GitHub installation.")
  private String baseUrl;

  @Parameter(
      names = "--accessToken",
      description =
          "A personal access token of an account with access to your organization's "
              + "GitHub Teams structure.")
  private String accessToken;

  @Parameter(
      names = "--organization",
      description = "The GitHub organization under which to query for GitHub Teams.")
  private String organization;

  @Override
  protected RoleProvider editRoleProvider(GithubRoleProvider roleProvider) {
    roleProvider.setBaseUrl(isSet(baseUrl) ? baseUrl : roleProvider.getBaseUrl());
    roleProvider.setAccessToken(isSet(accessToken) ? accessToken : roleProvider.getAccessToken());
    roleProvider.setOrganization(
        isSet(organization) ? organization : roleProvider.getOrganization());
    return roleProvider;
  }
}
