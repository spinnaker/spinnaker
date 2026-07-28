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

package com.netflix.spinnaker.security.s2s;

import com.netflix.spinnaker.security.s2s.config.ServiceToServiceProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Enforces the codified {@link AllowServiceCallers} policy against the {@link ServiceCaller}
 * authenticated for the current request.
 *
 * <p>The caller is taken from {@link ServiceCallerContext} (populated by the authentication
 * filter); if absent — e.g. a service annotated an endpoint but did not install the filter — it is
 * resolved on demand from the current request, so enforcement never silently passes. When
 * service-to-service auth is disabled the aspect is a no-op; when enabled, a disallowed caller is
 * always denied — there is no log-only mode.
 */
@Aspect
public class ServiceCallerEnforcementAspect {

  private static final Logger log = LoggerFactory.getLogger(ServiceCallerEnforcementAspect.class);

  private final ServiceToServiceProperties properties;
  private final ServiceCallerResolver resolver;

  public ServiceCallerEnforcementAspect(
      ServiceToServiceProperties properties, ServiceCallerResolver resolver) {
    this.properties = properties;
    this.resolver = resolver;
  }

  @Before("@annotation(allow)")
  public void enforceOnMethod(AllowServiceCallers allow) {
    enforce(allow);
  }

  @Before("@within(allow) && execution(* *(..))")
  public void enforceOnType(AllowServiceCallers allow) {
    enforce(allow);
  }

  private void enforce(AllowServiceCallers allow) {
    if (!properties.isEnabled()) {
      return;
    }
    Set<SpinnakerService> allowed = EnumSet.copyOf(Arrays.asList(allow.value()));
    Optional<ServiceCaller> caller = currentCaller();
    boolean permitted = caller.map(c -> c.isKnown() && allowed.contains(c.service())).orElse(false);
    if (permitted) {
      return;
    }

    String description =
        caller.map(c -> c.service() + " (" + c.subject() + ")").orElse("<no authenticated caller>");
    log.warn("Denied caller {}; only {} may invoke this endpoint", description, allowed);
    throw new AccessDeniedException("Service caller not permitted to invoke this endpoint");
  }

  private Optional<ServiceCaller> currentCaller() {
    Optional<ServiceCaller> fromContext = ServiceCallerContext.current();
    if (fromContext.isPresent()) {
      return fromContext;
    }
    return currentRequest().flatMap(resolver::resolve);
  }

  private static Optional<HttpServletRequest> currentRequest() {
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes servletRequestAttributes) {
      return Optional.of(servletRequestAttributes.getRequest());
    }
    return Optional.empty();
  }
}
