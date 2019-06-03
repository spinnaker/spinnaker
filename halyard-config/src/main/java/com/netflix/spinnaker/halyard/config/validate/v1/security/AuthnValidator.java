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

package com.netflix.spinnaker.halyard.config.validate.v1.security;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.security.Authn;
import com.netflix.spinnaker.halyard.config.model.v1.security.IAP;
import com.netflix.spinnaker.halyard.config.model.v1.security.Ldap;
import com.netflix.spinnaker.halyard.config.model.v1.security.OAuth2;
import com.netflix.spinnaker.halyard.config.model.v1.security.Saml;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class AuthnValidator extends Validator<Authn> {

  @Override
  public void validate(ConfigProblemSetBuilder p, Authn n) {
    if (!n.isEnabled() && maybeShouldBeEnabled(n)) {
      p.addProblem(
          Problem.Severity.WARNING,
          "An authentication method is fully or "
              + "partially configured, but not enabled. It must be enabled to take effect.");
    }
  }

  /**
   * @return True if any core field in an authentication method has a non-empty value. "Core fields"
   *     are generally required fields to make an authentication method work, such as client
   *     ID/secret, or path to a certficate store.
   */
  private boolean maybeShouldBeEnabled(Authn n) {
    OAuth2 o = n.getOauth2();
    Saml s = n.getSaml();
    Ldap l = n.getLdap();
    IAP i = n.getIap();
    // There isn't a good "core fields" for X509

    return StringUtils.isNotEmpty(o.getClient().getClientId())
        || StringUtils.isNotEmpty(o.getClient().getClientSecret())
        || StringUtils.isNotEmpty(s.getIssuerId())
        || StringUtils.isNotEmpty(s.getKeyStore())
        || StringUtils.isNotEmpty(l.getUserDnPattern())
        || StringUtils.isNotEmpty(l.getUserSearchBase())
        || StringUtils.isNotEmpty(l.getUserSearchFilter())
        || StringUtils.isNotEmpty(i.getAudience());
  }
}
