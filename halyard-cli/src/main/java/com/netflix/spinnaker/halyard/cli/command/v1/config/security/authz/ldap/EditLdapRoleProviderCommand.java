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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.ldap;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.URIConverter;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authz.AbstractEditRoleProviderCommand;
import com.netflix.spinnaker.halyard.config.model.v1.security.GroupMembership;
import com.netflix.spinnaker.halyard.config.model.v1.security.LdapRoleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.security.RoleProvider;
import java.net.URI;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Parameters(separators = "=")
public class EditLdapRoleProviderCommand extends AbstractEditRoleProviderCommand<LdapRoleProvider> {
  GroupMembership.RoleProviderType roleProviderType = GroupMembership.RoleProviderType.LDAP;

  @Parameter(
      names = "--url",
      description = "ldap:// or ldaps:// url of the LDAP server",
      converter = URIConverter.class)
  private URI url;

  @Parameter(
      names = "--manager-dn",
      description =
          "The manager user's distinguished name (principal) to use for querying ldap groups.")
  private String managerDn;

  @Parameter(
      names = "--manager-password",
      password = true,
      description = "The manager user's password to use for querying ldap groups.")
  private String managerPassword;

  @Parameter(
      names = "--group-search-base",
      description =
          "The part of the directory tree under which group searches should be performed. ")
  private String groupSearchBase;

  @Parameter(
      names = "--group-search-filter",
      description =
          "The filter which is used to search for group membership. The default is "
              + "'uniqueMember={0}', corresponding to the groupOfUniqueMembers LDAP class. In this case, "
              + "the substituted parameter is the full distinguished name of the user. The parameter "
              + "'{1}' can be used if you want to filter on the login name.")
  private String groupSearchFilter;

  @Parameter(
      names = "--group-role-attributes",
      description =
          "The attribute which contains the name of the authority defined by the group "
              + "entry. Defaults to 'cn'.")
  private String groupRoleAttributes;

  @Parameter(
      names = "--user-dn-pattern",
      description =
          "The pattern for finding a user's DN using simple pattern matching. For "
              + "example, if your LDAP server has the URL ldap://mysite.com/dc=spinnaker,dc=org, "
              + "and you have the pattern 'uid={0},ou=members', 'me' will map to a DN "
              + "uid=me,ou=members,dc=spinnaker,dc=org. If no match is found, will try to find the user "
              + "using --user-search-filter, if set.")
  private String userDnPattern;

  @Parameter(
      names = "--user-search-base",
      description =
          "The part of the directory tree under which user searches should be performed. "
              + "If --user-search-base isn't supplied, the search will be performed from the root.")
  private String userSearchBase;

  @Parameter(
      names = "--user-search-filter",
      description =
          "The filter to use when searching for a user's DN. Will search either from "
              + "--user-search-base (if specified) or root for entires matching the filter.")
  private String userSearchFilter;

  @Override
  protected RoleProvider editRoleProvider(LdapRoleProvider ldap) {
    ldap.setUrl(isSet(url) ? url : ldap.getUrl());
    ldap.setManagerDn(isSet(managerDn) ? managerDn : ldap.getManagerDn());
    ldap.setManagerPassword(isSet(managerPassword) ? managerPassword : ldap.getManagerPassword());
    ldap.setGroupSearchBase(isSet(groupSearchBase) ? groupSearchBase : ldap.getGroupSearchBase());
    ldap.setGroupSearchFilter(
        isSet(groupSearchFilter) ? groupSearchFilter : ldap.getGroupSearchFilter());
    ldap.setGroupRoleAttributes(
        isSet(groupRoleAttributes) ? groupRoleAttributes : ldap.getGroupRoleAttributes());
    ldap.setUserDnPattern(isSet(userDnPattern) ? userDnPattern : ldap.getUserDnPattern());
    ldap.setUserSearchBase(isSet(userSearchBase) ? userSearchBase : ldap.getUserSearchBase());
    ldap.setUserSearchFilter(
        isSet(userSearchFilter) ? userSearchFilter : ldap.getUserSearchFilter());

    return ldap;
  }
}
