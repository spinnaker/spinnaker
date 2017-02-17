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

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.springframework.stereotype.Component;

@Component
public class SecurityValidator extends Validator<Security> {
  @Override
  public void validate(ConfigProblemSetBuilder p, Security n) {
    DeploymentConfiguration deploymentConfiguration = n.parentOfType(DeploymentConfiguration.class);

    boolean localhostAccess = n.getApiDomain().equals("localhost") || n.getUiDomain().equals("localhost");
    switch (deploymentConfiguration.getDeploymentEnvironment().getType()) {
      case Flotilla:
        if (localhostAccess) {
          p.addProblem(Problem.Severity.WARNING, "Your UI or API domain is set to \"localhost\", "
              + "even though your Spinnaker deployment is a Flotilla deployment on a remote cloud provider. "
              + "As a result, you will need to open SSH tunnels against that deployment to access Spinnaker.")
              .setRemediation("We recommend that you instead configure an authentication mechanism (OAuth2, SAML2, or x509) "
                  + "to make it easier to access Spinnaker securely, and then register the intended Domain and IP addresses "
                  + "that your publicly facing services will be using."); // TODO(lwander) point to a guide here
        }
        break;
      case LocalhostDebian:
        break;
    }
  }
}
