/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.security.authz.config;

import com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.pdp.LegacyPermissionsPolicyDecisionPoint;
import com.netflix.spinnaker.security.authz.pdp.PolicyDecisionPoint;
import com.netflix.spinnaker.security.authz.pdp.acl.SpringAclPolicyDecisionPoint;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the authorization decision/enforcement beans. The {@link PolicyDecisionPoint} is selected
 * by {@code authz.pdp.provider}:
 *
 * <ul>
 *   <li>{@code spring-acl} (default) — the adapter-based Spring Security ACL evaluator
 *   <li>{@code legacy} — the behavior-preserving {@link LegacyPermissionsPolicyDecisionPoint}
 *       fallback
 * </ul>
 *
 * The {@link PolicyDecisionPointPermissionEvaluator} is the stable {@code PermissionEvaluator} seam
 * that {@code @PreAuthorize}/{@code @PostFilter} bind to, so the PDP can be swapped without
 * touching call sites. Leave clean seams for OPA/OpenFGA adapters by contributing a {@link
 * PolicyDecisionPoint} bean and selecting it via {@code authz.pdp.provider}.
 */
@Configuration
@EnableConfigurationProperties(AuthzPolicyProperties.class)
public class AuthzPolicyConfiguration {

  private static final Logger log = LoggerFactory.getLogger(AuthzPolicyConfiguration.class);

  @Bean
  public PolicyDecisionPoint policyDecisionPoint(AuthzPolicyProperties properties) {
    String provider = properties.getProvider();
    if (LegacyPermissionsPolicyDecisionPoint.PROVIDER_ID.equalsIgnoreCase(provider)) {
      log.info("Using legacy permissions PolicyDecisionPoint (fallback)");
      return new LegacyPermissionsPolicyDecisionPoint();
    }
    log.info("Using Spring ACL PolicyDecisionPoint (default)");
    return new SpringAclPolicyDecisionPoint();
  }

  @Bean
  public PolicyDecisionPointPermissionEvaluator permissionEvaluator(
      PolicyDecisionPoint policyDecisionPoint,
      AuthzPolicyProperties properties,
      AuthorizationProperties authz,
      ObjectProvider<ResourceAclResolver> resourceAclResolver) {
    return new PolicyDecisionPointPermissionEvaluator(
        policyDecisionPoint,
        resourceAclResolver.getIfAvailable(),
        authz.isEnabled(),
        properties.isAllowAccessToUnknownApplications());
  }
}
