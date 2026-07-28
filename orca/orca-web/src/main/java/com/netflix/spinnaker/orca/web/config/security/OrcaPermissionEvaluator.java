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

package com.netflix.spinnaker.orca.web.config.security;

import com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.pdp.PolicyDecisionPoint;
import javax.annotation.Nullable;

/**
 * Orca's {@code PermissionEvaluator} that {@code @PreAuthorize("hasPermission(...)")} /
 * {@code @PostFilter} bind to (registered under the bean name {@code spinnakerPermissionEvaluator}
 * that the SpEL in {@code TaskController} resolves against).
 *
 * <p>Orca does not own {@code application} ACLs — those live in Front50 — but it does own execution
 * data, and {@code TaskController}'s checks are the only enforcement point on that path. Its
 * resolver therefore reads application ACLs from Front50 (see {@link
 * Front50ApplicationAclResolver}) rather than deferring to a downstream owner that never sees the
 * request.
 *
 * <p>When no resolver is available (Front50 disabled) application checks fall through to {@code
 * allowAccessToUnknownApplications}, and when authorization is disabled ({@code authz.enabled}
 * default off) every check short-circuits to allow.
 */
public class OrcaPermissionEvaluator extends PolicyDecisionPointPermissionEvaluator {

  public OrcaPermissionEvaluator(
      PolicyDecisionPoint policyDecisionPoint,
      @Nullable ResourceAclResolver resourceAclResolver,
      boolean enabled,
      boolean allowAccessToUnknownApplications) {
    super(policyDecisionPoint, resourceAclResolver, enabled, allowAccessToUnknownApplications);
  }
}
