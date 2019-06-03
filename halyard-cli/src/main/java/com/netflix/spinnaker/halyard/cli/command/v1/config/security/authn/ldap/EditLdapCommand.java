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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.ldap;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.URIConverter;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.AbstractEditAuthnMethodCommand;
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod;
import com.netflix.spinnaker.halyard.config.model.v1.security.Ldap;
import java.net.URI;
import lombok.Getter;

public class EditLdapCommand extends AbstractEditAuthnMethodCommand<Ldap> {

  @Getter
  private String shortDescription = "Configure authentication using a LDAP identity provider.";

  @Getter
  private String longDescription =
      String.join(
          " ",
          "Lightweight Directory Access Protocol (LDAP)",
          "is a standard way many organizations maintain user credentials and group memberships.",
          "Spinnaker uses the standard 'bind' approach for user authentication.",
          "This is a fancy way of saying that Gate uses your username and password to login to the LDAP server,",
          "and if the connection is successful, you're considered authenticated.");

  @Getter private AuthnMethod.Method method = AuthnMethod.Method.LDAP;

  @Parameter(
      names = "--url",
      description = "ldap:// or ldaps:// url of the LDAP server",
      converter = URIConverter.class)
  private URI url;

  @Parameter(
      names = "--user-dn-pattern",
      description =
          "The pattern for finding a user's DN using simple pattern matching. For "
              + "example, if your LDAP server has the URL ldap://mysite.com/dc=spinnaker,dc=org, "
              + "and you have the pattern 'uid={0},ou=members', 'me' will map to a DN "
              + "uid=me,ou=members,dc=spinnaker,dc=org. If no match is found, will try to find the user "
              + "using user-search-filter, if set.")
  private String userDnPattern;

  @Parameter(
      names = "--user-search-base",
      description =
          "The part of the directory tree under which user searches should be performed. "
              + "If user-search-base isn't supplied, the search will be performed from the root.")
  private String userSearchBase;

  @Parameter(
      names = "--user-search-filter",
      description =
          "The filter to use when searching for a user's DN. Will search either from "
              + "user-search-base (if specified) or root for entires matching the filter, then attempt "
              + "to bind as that user with the login password. For example, the filter 'uid={0}' would "
              + "apply to any user where uid matched the user's login name. If --user-dn-pattern is also "
              + "specified, will attempt to find a match using the specified pattern first, before "
              + "searching with the specified search filter if no match is found from the pattern.")
  private String userSearchFilter;

  @Parameter(
      names = "--manager-dn",
      description =
          "An LDAP manager user is required for binding to the LDAP server for the user "
              + "authentication process. This property refers to the DN of that entry. I.e. this is not "
              + "the user which will be authenticated when logging into DHIS2, rather the user which binds "
              + "to the LDAP server in order to do the authentication.")
  private String managerDn;

  @Parameter(
      names = "--manager-password",
      password = true,
      description = "The password for the LDAP manager user.")
  private String managerPassword;

  @Parameter(
      names = "--group-search-base",
      description =
          "The part of the directory tree under which group searches should be performed. ")
  private String groupSearchBase;

  @Override
  protected AuthnMethod editAuthnMethod(Ldap ldap) {
    ldap.setUrl(isSet(url) ? url : ldap.getUrl());
    ldap.setUserDnPattern(isSet(userDnPattern) ? userDnPattern : ldap.getUserDnPattern());
    ldap.setUserSearchBase(isSet(userSearchBase) ? userSearchBase : ldap.getUserSearchBase());
    ldap.setUserSearchFilter(
        isSet(userSearchFilter) ? userSearchFilter : ldap.getUserSearchFilter());

    if (isSet(userSearchBase)) {
      if (userSearchBase.isEmpty()) {
        ldap.setUserSearchBase(null);
      }
    }

    if (isSet(userSearchFilter)) {
      if (userSearchFilter.isEmpty()) {
        ldap.setUserSearchFilter(null);
      }
    }

    ldap.setManagerDn(isSet(managerDn) ? managerDn : ldap.getManagerDn());
    ldap.setManagerPassword(isSet(managerPassword) ? managerPassword : ldap.getManagerPassword());
    ldap.setGroupSearchBase(isSet(groupSearchBase) ? groupSearchBase : ldap.getGroupSearchBase());

    return ldap;
  }
}
