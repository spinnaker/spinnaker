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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.security.Authn;
import com.netflix.spinnaker.halyard.config.model.v1.security.OAuth2;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SecurityService {
  @Autowired
  private LookupService lookupService;

  @Autowired
  private DeploymentService deploymentService;

  @Autowired
  private ValidateService validateService;

  public Security getSecurity(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setSecurity();

    List<Security> matching = lookupService.getMatchingNodesOfType(filter, Security.class);

    switch (matching.size()) {
      case 0:
        Security security = new Security();
        setSecurity(deploymentName, security);
        return security;
      case 1:
        return matching.get(0);
      default:
        throw new RuntimeException("It shouldn't be possible to have multiple security nodes. This is a bug.");
    }
  }

  public Authn getAuthn(String deploymentName) {
    Security security = getSecurity(deploymentName);
    Authn result = security.getAuthn();
    if (result == null) {
      result = new Authn();
      security.setAuthn(result);
    }

    return result;
  }

  public OAuth2 getOAuth2(String deploymentName) {
    Authn authn = getAuthn(deploymentName);
    OAuth2 result = authn.getOAuth2();
    if (result == null) {
      result = new OAuth2();
      authn.setOAuth2(result);
    }

    return result;
  }

  public void setSecurity(String deploymentName, Security newSecurity) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    deploymentConfiguration.setSecurity(newSecurity);
  }

  public void setAuthn(String deploymentName, Authn authn) {
    getSecurity(deploymentName).setAuthn(authn);
  }

  public void setOAuth2(String deploymentName, OAuth2 oauth2) {
    getAuthn(deploymentName).setOAuth2(oauth2);
  }

  public ProblemSet validateSecurity(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setSecurity();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateOAuth2(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setSecurity().setOAuth2();
    return validateService.validateMatchingFilter(filter);
  }
}
