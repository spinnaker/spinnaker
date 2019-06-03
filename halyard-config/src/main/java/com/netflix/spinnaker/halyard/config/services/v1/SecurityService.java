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
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SecurityService {
  @Autowired private LookupService lookupService;

  @Autowired private DeploymentService deploymentService;

  @Autowired private ValidateService validateService;

  public SpringSsl getSpringSsl(String deploymentName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setSecurity()
            .setApiSecurity()
            .setSpringSsl();

    return lookupService.getSingularNodeOrDefault(
        filter, SpringSsl.class, SpringSsl::new, n -> setSpringSsl(deploymentName, n));
  }

  public void setSpringSsl(String deploymentName, SpringSsl apacheSsl) {
    ApiSecurity uiSecurity = getApiSecurity(deploymentName);
    uiSecurity.setSsl(apacheSsl);
  }

  public void setSpringSslEnabled(String deploymentName, boolean enabled) {
    getSpringSsl(deploymentName).setEnabled(enabled);
  }

  public ApacheSsl getApacheSsl(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setSecurity().setUiSecurity().setApacheSsl();

    return lookupService.getSingularNodeOrDefault(
        filter, ApacheSsl.class, ApacheSsl::new, n -> setApacheSsl(deploymentName, n));
  }

  public void setApacheSsl(String deploymentName, ApacheSsl apacheSsl) {
    UiSecurity uiSecurity = getUiSecurity(deploymentName);
    uiSecurity.setSsl(apacheSsl);
  }

  public void setApacheSslEnabled(String deploymentName, boolean enabled) {
    getApacheSsl(deploymentName).setEnabled(enabled);
  }

  public UiSecurity getUiSecurity(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setSecurity().setUiSecurity();

    return lookupService.getSingularNodeOrDefault(
        filter, UiSecurity.class, UiSecurity::new, n -> setUiSecurity(deploymentName, n));
  }

  public void setUiSecurity(String deploymentName, UiSecurity apiSecurity) {
    Security security = getSecurity(deploymentName);
    security.setUiSecurity(apiSecurity);
  }

  public ApiSecurity getApiSecurity(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setSecurity().setApiSecurity();

    return lookupService.getSingularNodeOrDefault(
        filter, ApiSecurity.class, ApiSecurity::new, n -> setApiSecurity(deploymentName, n));
  }

  public void setApiSecurity(String deploymentName, ApiSecurity apiSecurity) {
    Security security = getSecurity(deploymentName);
    security.setApiSecurity(apiSecurity);
  }

  public Security getSecurity(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setSecurity();

    return lookupService.getSingularNodeOrDefault(
        filter, Security.class, Security::new, n -> setSecurity(deploymentName, n));
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
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setSecurity().setAuthnMethod(methodName);

    return lookupService.getSingularNodeOrDefault(
        filter,
        AuthnMethod.class,
        () -> {
          try {
            return AuthnMethod.translateAuthnMethodName(methodName).newInstance();
          } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        },
        n -> setAuthnMethod(deploymentName, n));
  }

  public RoleProvider getRoleProvider(String deploymentName, String roleProviderName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setSecurity()
            .setRoleProvider(roleProviderName);

    return lookupService.getSingularNodeOrDefault(
        filter,
        RoleProvider.class,
        () -> {
          try {
            return GroupMembership.translateRoleProviderType(roleProviderName).newInstance();
          } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        },
        n -> setRoleProvider(deploymentName, n));
  }

  public void setSecurity(String deploymentName, Security newSecurity) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
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
      case SAML:
        authn.setSaml((Saml) method);
        break;
      case LDAP:
        authn.setLdap((Ldap) method);
        break;
      case X509:
        authn.setX509((X509) method);
        break;
      case IAP:
        authn.setIap((IAP) method);
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
      case GITHUB:
        groupMembership.setGithub((GithubRoleProvider) roleProvider);
        break;
      case FILE:
        groupMembership.setFile((FileRoleProvider) roleProvider);
        break;
      case LDAP:
        groupMembership.setLdap((LdapRoleProvider) roleProvider);
        break;
      default:
        throw new RuntimeException("Unknown Role Provider " + roleProvider.getRoleProviderType());
    }
  }

  public ProblemSet validateApacheSsl(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setSecurity().setUiSecurity().setApacheSsl();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateSpringSsl(String deploymentName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setSecurity()
            .setApiSecurity()
            .setSpringSsl();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateUiSecurity(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setSecurity().setUiSecurity();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateApiSecurity(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setSecurity().setApiSecurity();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateSecurity(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setSecurity();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAuthnMethod(String deploymentName, String methodName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setSecurity().setAuthnMethod(methodName);
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAuthz(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setSecurity().withAnyRoleProvider();
    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateRoleProvider(String deploymentName, String roleProviderName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setSecurity()
            .setRoleProvider(roleProviderName);
    return validateService.validateMatchingFilter(filter);
  }
}
