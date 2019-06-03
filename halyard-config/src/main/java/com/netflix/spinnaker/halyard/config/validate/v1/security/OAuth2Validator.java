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

package com.netflix.spinnaker.halyard.config.validate.v1.security;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.security.OAuth2;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.springframework.stereotype.Component;

@Component
public class OAuth2Validator extends Validator<OAuth2> {
  @Override
  public void validate(ConfigProblemSetBuilder p, OAuth2 n) {
    if (!n.isEnabled()) {
      return;
    }

    if (n.getClient().getClientId() == null) {
      p.addProblem(Problem.Severity.ERROR, "No OAuth2 client id was supplied");
    }

    if (n.getClient().getClientSecret() == null) {
      p.addProblem(Problem.Severity.ERROR, "No OAuth2 client secret was supplied");
    }

    if (n.getProvider() == OAuth2.Provider.GOOGLE
        && (n.getUserInfoRequirements() == null
            || !n.getUserInfoRequirements().containsKey("hd"))) {
      p.addProblem(
          Problem.Severity.WARNING,
          "Missing 'hd' field within "
              + "userInfoRequirements of Google OAuth provider. This could expose your Spinnaker "
              + "instance to anyone with a Gmail account.",
          "userInfoRequirements");
    }
  }
}
