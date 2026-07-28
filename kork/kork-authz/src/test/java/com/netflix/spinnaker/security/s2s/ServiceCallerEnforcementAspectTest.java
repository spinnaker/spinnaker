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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spinnaker.security.s2s.config.ServiceToServiceProperties;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ServiceCallerEnforcementAspectTest {

  @AfterEach
  void tearDown() {
    ServiceCallerContext.clear();
    RequestContextHolder.resetRequestAttributes();
  }

  static class Protected {
    @AllowServiceCallers(SpinnakerService.ORCA)
    String orcaOnly() {
      return "ok";
    }
  }

  private Protected proxy(boolean enabled) {
    return proxy(enabled, ServiceCallerResolver.disabled());
  }

  private Protected proxy(boolean enabled, ServiceCallerResolver resolver) {
    ServiceToServiceProperties properties = new ServiceToServiceProperties();
    properties.setEnabled(enabled);
    ServiceCallerEnforcementAspect aspect =
        new ServiceCallerEnforcementAspect(properties, resolver);
    AspectJProxyFactory factory = new AspectJProxyFactory(new Protected());
    factory.addAspect(aspect);
    return factory.getProxy();
  }

  @Test
  void allowsPermittedCaller() {
    Protected target = proxy(true);
    ServiceCallerContext.set(new ServiceCaller(SpinnakerService.ORCA, "CN=orca", "test"));

    assertThat(target.orcaOnly()).isEqualTo("ok");
  }

  @Test
  void deniesDisallowedCaller() {
    Protected target = proxy(true);
    ServiceCallerContext.set(new ServiceCaller(SpinnakerService.ECHO, "CN=echo", "test"));

    assertThatThrownBy(target::orcaOnly).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void deniesMissingCaller() {
    Protected target = proxy(true);

    assertThatThrownBy(target::orcaOnly).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void disabledIsNoOp() {
    Protected target = proxy(false);

    assertThat(target.orcaOnly()).isEqualTo("ok");
  }

  @Test
  void deniesUnknownCallerEvenWhenAllowed() {
    // Guards SpinnakerService.UNKNOWN never satisfying @AllowServiceCallers: even if a future
    // refactor listed UNKNOWN, the aspect's isKnown() check must still deny it.
    Protected target = proxy(true);
    ServiceCallerContext.set(new ServiceCaller(SpinnakerService.UNKNOWN, "CN=mystery", "test"));

    assertThatThrownBy(target::orcaOnly).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void resolvesCallerOnDemandFromRequestWhenContextEmpty() {
    // Exercises the documented fallback: when ServiceCallerContext is empty (no auth filter ran)
    // the aspect resolves the caller from the live request so enforcement never silently passes.
    MockHttpServletRequest request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    ServiceCallerResolver resolver =
        req ->
            req == request
                ? Optional.of(new ServiceCaller(SpinnakerService.ORCA, "CN=orca", "on-demand"))
                : Optional.empty();
    Protected target = proxy(true, resolver);

    assertThat(target.orcaOnly()).isEqualTo("ok");
  }

  @Test
  void deniesWhenOnDemandResolverReturnsDisallowedCaller() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    ServiceCallerResolver resolver =
        req -> Optional.of(new ServiceCaller(SpinnakerService.ECHO, "CN=echo", "on-demand"));
    Protected target = proxy(true, resolver);

    assertThatThrownBy(target::orcaOnly).isInstanceOf(AccessDeniedException.class);
  }
}
