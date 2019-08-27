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

package com.netflix.spinnaker.halyard.config.validate.v1.security;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.security.Ldap;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class LdapValidator extends Validator<Ldap> {

  @Override
  public void validate(ConfigProblemSetBuilder p, Ldap ldap) {

    if (!ldap.isEnabled()) {
      return;
    }

    if (ldap.getUrl() == null) {
      p.addProblem(Problem.Severity.ERROR, "LDAP url missing.");
    } else if (ldap.getUrl().getScheme() == null) {
      p.addProblem(Problem.Severity.ERROR, "LDAP url scheme is missing.");
    } else if (ldap.getUrl().getPort() == -1) {
      p.addProblem(Problem.Severity.ERROR, "LDAP url port is undefined");
    } else if (!ldap.getUrl().getScheme().equalsIgnoreCase("ldaps")
        && !ldap.getUrl().getScheme().equalsIgnoreCase("ldap")) {
      p.addProblem(Problem.Severity.ERROR, "LDAP url must use ldap or ldaps protocol.");
    }

    switch (UserSearchMethod.toUserSearchMethod(ldap)) {
      case DN_PATTERN: // fall through.
      case SEARCH_AND_OR_BASE:
        break;
      case UNSPECIFIED_OR_INVALID: // fall through.
      default:
        p.addProblem(
            Problem.Severity.ERROR,
            "No valid user search method defined. Please "
                + "specify with either --user-dn-pattern OR (--user-search-filter with an optional --user-search-base).");
    }
  }

  enum UserSearchMethod {
    UNSPECIFIED_OR_INVALID,
    DN_PATTERN,
    SEARCH_AND_OR_BASE;

    static UserSearchMethod toUserSearchMethod(Ldap ldap) {
      if (StringUtils.isNotEmpty(ldap.getUserDnPattern())) {
        return DN_PATTERN;
      } else if (StringUtils.isNotEmpty(ldap.getUserSearchFilter())) {
        return SEARCH_AND_OR_BASE;
      }
      return UNSPECIFIED_OR_INVALID;
    }
  }
}
