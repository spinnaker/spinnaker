/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.gate.security;

import com.netflix.spinnaker.security.AuthenticatedRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * No longer backed by a thread local, this is now a readonly proxy to {@link AuthenticatedRequest},
 * which is the only source of truth for context values. This is to avoid the situation where
 * RequestContext and {@link AuthenticatedRequest} provide an inconsistent view of the context or
 * lose it across thread boundaries.
 */
@Slf4j
public class RequestContext {
  private static final RequestContext placeholder = new RequestContext();

  private RequestContext() {}

  public RequestContext(String origin, String authenticatedUser) {
    log.warn("call to deprecated RequestContext's constructor");
  }

  @Deprecated
  public static void set(RequestContext requestContext) {
    log.warn("call to deprecated method RequestContext::set");
  }

  @Deprecated
  public static void setApplication(String application) {
    log.warn("call to deprecated method RequestContext::setApplication");
  }

  @Deprecated
  public static void setExecutionType(String executionType) {
    log.warn("call to deprecated method RequestContext::setExecutionType");
  }

  @Deprecated
  public static void setExecutionId(String executionId) {
    log.warn("call to deprecated method RequestContext::setExecutionId");
  }

  @Deprecated
  public static void clear() {
    log.warn("call to deprecated method RequestContext::clear");
  }

  public static RequestContext get() {
    return placeholder;
  }

  public String getApplication() {
    return AuthenticatedRequest.getSpinnakerApplication().orElse(null);
  }

  public String getAuthenticatedUser() {
    return AuthenticatedRequest.getSpinnakerUser().orElse(null);
  }

  public String getExecutionType() {
    return AuthenticatedRequest.getSpinnakerExecutionType().orElse(null);
  }

  public String getExecutionId() {
    return AuthenticatedRequest.getSpinnakerExecutionId().orElse(null);
  }

  public String getOrigin() {
    return AuthenticatedRequest.getSpinnakerUserOrigin().orElse(null);
  }
}
