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

package com.netflix.spinnaker.gate.security;

import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.config.AuthzPolicyProperties;
import com.netflix.spinnaker.security.authz.pdp.LegacyPermissionsPolicyDecisionPoint;
import com.netflix.spinnaker.security.authz.pdp.PolicyDecisionPoint;
import com.netflix.spinnaker.security.authz.pdp.acl.SpringAclPolicyDecisionPoint;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Gate's token-derived authorization model. Registers a single Spring {@code
 * PermissionEvaluator} bean (named {@code spinnakerPermissionEvaluator} so existing
 * {@code @PreAuthorize}/{@code @PostFilter} SpEL keeps binding) backed by the kork {@link
 * PolicyDecisionPoint} (Spring ACL by default, legacy-permissions fallback via {@code
 * authz.pdp.provider}).
 */
@Configuration
@EnableConfigurationProperties({AuthzPolicyProperties.class, AuthorizationProperties.class})
public class GateAuthorizationConfig {

  private static final Logger log = LoggerFactory.getLogger(GateAuthorizationConfig.class);

  @Bean
  @ConditionalOnMissingBean(PolicyDecisionPoint.class)
  public PolicyDecisionPoint policyDecisionPoint(AuthzPolicyProperties properties) {
    if (LegacyPermissionsPolicyDecisionPoint.PROVIDER_ID.equalsIgnoreCase(
        properties.getProvider())) {
      log.info("Gate authorization using legacy permissions PolicyDecisionPoint (fallback)");
      return new LegacyPermissionsPolicyDecisionPoint();
    }
    log.info("Gate authorization using Spring ACL PolicyDecisionPoint (default)");
    return new SpringAclPolicyDecisionPoint();
  }

  /**
   * The Spring {@code PermissionEvaluator} that {@code hasPermission(...)} binds to, registered
   * under the SpEL bean name {@code spinnakerPermissionEvaluator} that {@code
   * @spinnakerPermissionEvaluator.*} references resolve against.
   */
  @Bean(name = "spinnakerPermissionEvaluator")
  public GatePermissionEvaluator spinnakerPermissionEvaluator(
      PolicyDecisionPoint policyDecisionPoint,
      AuthzPolicyProperties properties,
      AuthorizationProperties authz,
      ObjectProvider<ResourceAclResolver> resourceAclResolver) {
    return new GatePermissionEvaluator(
        policyDecisionPoint,
        resourceAclResolver.getIfAvailable(),
        authz.isEnabled(),
        properties.isAllowAccessToUnknownApplications());
  }
}
