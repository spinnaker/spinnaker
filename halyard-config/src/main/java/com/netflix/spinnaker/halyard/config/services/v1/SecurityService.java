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
import com.netflix.spinnaker.halyard.config.model.v1.security.*;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
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

  public void setAuthnMethodEnabled(String deploymentName, String methodName, boolean enabled) {
    AuthnMethod method = getAuthnMethod(deploymentName, methodName);
    method.setEnabled(enabled);
    setAuthnMethod(deploymentName, method);
  }

  public void setAuthzEnabled(String deploymentName, boolean enabled) {
    Authz authz = getAuthz(deploymentName);
    authz.setEnabled(enabled);
    setAuthz(deploymentName, authz);
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

  public GroupMembership getGroupMembership(String deploymentName) {
    Authz authz = getAuthz(deploymentName);
    if (authz.getGroupMembership() == null) {
      authz.setGroupMembership(new GroupMembership());
    }

    return authz.getGroupMembership();
  }

  public Authz getAuthz(String deploymentName) {
    Security security = getSecurity(deploymentName);
    Authz result = security.getAuthz();
    if (result == null) {
      result = new Authz();
      security.setAuthz(result);
    }

    return result;
  }

  public AuthnMethod getAuthnMethod(String deploymentName, String methodName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setSecurity().setAuthnMethod(methodName);

    List<AuthnMethod> matching = lookupService.getMatchingNodesOfType(filter, AuthnMethod.class);

    try {
      switch (matching.size()) {
        case 0:
          AuthnMethod security = AuthnMethod.translateAuthnMethodName(methodName).newInstance();
          setAuthnMethod(deploymentName, security);
          return security;
        case 1:
          return matching.get(0);
        default:
          throw new RuntimeException("It shouldn't be possible to have multiple security nodes. This is a bug.");
      }
    } catch (InstantiationException | IllegalAccessException e) {
      throw new HalException(new ConfigProblemBuilder(Severity.FATAL, "Can't create an empty authn node "
          + "for authn method name \"" + methodName + "\"").build()
      );
    }
  }

  public RoleProvider getRoleProvider(String deploymentName, String roleProviderName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setSecurity().setRoleProvider(roleProviderName);

    List<RoleProvider> matching = lookupService.getMatchingNodesOfType(filter, RoleProvider.class);

    try {
      switch (matching.size()) {
        case 0:
          RoleProvider roleProvider = GroupMembership.translateRoleProviderType(roleProviderName).newInstance();
          setRoleProvider(deploymentName, roleProvider);
          return roleProvider;
        case 1:
          return matching.get(0);
        default:
          throw new RuntimeException("It shouldn't be possible to have multiple security nodes. This is a bug.");
      }
    } catch (InstantiationException | IllegalAccessException e) {
      throw new HalException(new ConfigProblemBuilder(Severity.FATAL, "Can't create an empty authn node "
          + "for authn role provider name \"" + roleProviderName + "\"").build()
      );
    }
  }

  public void setSecurity(String deploymentName, Security newSecurity) {
    DeploymentConfiguration deploymentConfiguration = deploymentService.getDeploymentConfiguration(deploymentName);
    deploymentConfiguration.setSecurity(newSecurity);
  }

  public void setAuthn(String deploymentName, Authn authn) {
    getSecurity(deploymentName).setAuthn(authn);
  }

  public void setAuthz(String deploymentName, Authz authz) {
    getSecurity(deploymentName).setAuthz(authz);
  }

  public void setGroupMembership(String deploymentName, GroupMembership membership) {
    getAuthz(deploymentName).setGroupMembership(membership);
  }

  public void setAuthnMethod(String deploymentName, AuthnMethod method) {
    Authn authn = getAuthn(deploymentName);
    switch (method.getMethod()) {
      case OAuth2:
        authn.setOauth2((OAuth2) method);
        break;
      default:
        throw new RuntimeException("Unknown Authn method " + method.getMethod());
    }
  }

  public void setRoleProvider(String deploymentName, RoleProvider roleProvider) {
    Authz authz = getAuthz(deploymentName);
    if (authz.getGroupMembership() == null) {
      authz.setGroupMembership(new GroupMembership());
    }

    GroupMembership groupMembership = authz.getGroupMembership();

    switch (roleProvider.getRoleProviderType()) {
      case GOOGLE:
        groupMembership.setGoogle((GoogleRoleProvider) roleProvider);
        break;
      default:
        throw new RuntimeException("Unknown Role Provider " + roleProvider.getRoleProviderType());
    }
  }

  public ProblemSet validateSecurity(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setSecurity();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAuthnMethod(String deploymentName, String methodName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setSecurity().setAuthnMethod(methodName);
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAuthz(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setSecurity().withAnyRoleProvider();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateRoleProvider(String deploymentName, String roleProviderName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setSecurity().setRoleProvider(roleProviderName);
    return validateService.validateMatchingFilter(filter);
  }
}
