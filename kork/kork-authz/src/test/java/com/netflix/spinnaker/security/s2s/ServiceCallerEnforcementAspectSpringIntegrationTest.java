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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end test that a real Spring {@code @RestController} annotated with {@link
 * AllowServiceCallers} is actually AOP-proxied by the Spring container (via {@link
 * EnableAspectJAutoProxy}) and that {@link ServiceCallerEnforcementAspect} fires on the resulting
 * proxy.
 *
 * <p>Unlike {@link ServiceCallerEnforcementAspectTest} — which builds the proxy by hand with an
 * {@code AspectJProxyFactory} — this test obtains the proxied controller bean straight from a
 * Spring {@link AnnotationConfigApplicationContext}, proving the wiring works when Spring (not a
 * hand-built factory) creates the proxy. MockMvc is not used because {@code spring-webmvc} is not
 * on the kork-authz test classpath.
 */
class ServiceCallerEnforcementAspectSpringIntegrationTest {

  @AfterEach
  void tearDown() {
    ServiceCallerContext.clear();
  }

  /** Method-level {@link AllowServiceCallers} — exercises the {@code @annotation} pointcut. */
  @RestController
  static class MethodProtectedController {
    @GetMapping("/orca-only")
    @AllowServiceCallers(SpinnakerService.ORCA)
    String orcaOnly() {
      return "ok";
    }

    @GetMapping("/open")
    String open() {
      return "open";
    }
  }

  /** Type-level {@link AllowServiceCallers} — exercises the {@code @within} pointcut. */
  @RestController
  @AllowServiceCallers(SpinnakerService.ORCA)
  static class TypeProtectedController {
    @GetMapping("/type-orca-only")
    String orcaOnly() {
      return "ok-type";
    }
  }

  abstract static class BaseConfig {
    @Bean
    ServiceCallerEnforcementAspect serviceCallerEnforcementAspect(
        ServiceToServiceProperties properties) {
      return new ServiceCallerEnforcementAspect(properties, ServiceCallerResolver.disabled());
    }

    @Bean
    MethodProtectedController methodProtectedController() {
      return new MethodProtectedController();
    }

    @Bean
    TypeProtectedController typeProtectedController() {
      return new TypeProtectedController();
    }
  }

  @Configuration
  @EnableAspectJAutoProxy
  static class EnabledConfig extends BaseConfig {
    @Bean
    ServiceToServiceProperties serviceToServiceProperties() {
      ServiceToServiceProperties properties = new ServiceToServiceProperties();
      properties.setEnabled(true);
      return properties;
    }
  }

  @Configuration
  @EnableAspectJAutoProxy
  static class DisabledConfig extends BaseConfig {
    @Bean
    ServiceToServiceProperties serviceToServiceProperties() {
      ServiceToServiceProperties properties = new ServiceToServiceProperties();
      properties.setEnabled(false);
      return properties;
    }
  }

  @Test
  void controllerIsProxiedBySpring() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(EnabledConfig.class)) {
      MethodProtectedController controller = context.getBean(MethodProtectedController.class);
      assertThat(AopUtils.isAopProxy(controller)).isTrue();
    }
  }

  @Test
  void allowsPermittedCallerOnMethodAnnotation() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(EnabledConfig.class)) {
      MethodProtectedController controller = context.getBean(MethodProtectedController.class);
      ServiceCallerContext.set(new ServiceCaller(SpinnakerService.ORCA, "CN=orca", "test"));

      assertThat(controller.orcaOnly()).isEqualTo("ok");
    }
  }

  @Test
  void deniesDisallowedCallerOnMethodAnnotation() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(EnabledConfig.class)) {
      MethodProtectedController controller = context.getBean(MethodProtectedController.class);
      ServiceCallerContext.set(new ServiceCaller(SpinnakerService.ECHO, "CN=echo", "test"));

      assertThatThrownBy(controller::orcaOnly).isInstanceOf(AccessDeniedException.class);
    }
  }

  @Test
  void deniesMissingCallerOnMethodAnnotation() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(EnabledConfig.class)) {
      MethodProtectedController controller = context.getBean(MethodProtectedController.class);

      assertThatThrownBy(controller::orcaOnly).isInstanceOf(AccessDeniedException.class);
    }
  }

  @Test
  void enforcesTypeLevelAnnotation() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(EnabledConfig.class)) {
      TypeProtectedController controller = context.getBean(TypeProtectedController.class);

      ServiceCallerContext.set(new ServiceCaller(SpinnakerService.ECHO, "CN=echo", "test"));
      assertThatThrownBy(controller::orcaOnly).isInstanceOf(AccessDeniedException.class);

      ServiceCallerContext.set(new ServiceCaller(SpinnakerService.ORCA, "CN=orca", "test"));
      assertThat(controller.orcaOnly()).isEqualTo("ok-type");
    }
  }

  @Test
  void disabledIsNoOpEvenForDisallowedCaller() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(DisabledConfig.class)) {
      MethodProtectedController controller = context.getBean(MethodProtectedController.class);
      ServiceCallerContext.set(new ServiceCaller(SpinnakerService.ECHO, "CN=echo", "test"));

      assertThat(controller.orcaOnly()).isEqualTo("ok");
    }
  }
}
