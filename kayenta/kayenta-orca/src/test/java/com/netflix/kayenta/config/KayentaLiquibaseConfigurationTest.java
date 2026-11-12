/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.kayenta.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.kayenta.config.KayentaLiquibaseConfiguration.KayentaLiquibaseGuard;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class KayentaLiquibaseConfigurationTest {

  /** {@code shouldRun} is protected and has no public getter. */
  private static boolean shouldRun(Object springLiquibase) {
    return Boolean.TRUE.equals(ReflectionTestUtils.getField(springLiquibase, "shouldRun"));
  }

  private static KayentaLiquibaseGuard guard(String kayentaSqlEnabled) {
    MockEnvironment environment = new MockEnvironment();
    if (kayentaSqlEnabled != null) {
      environment.setProperty("kayenta.sql.enabled", kayentaSqlEnabled);
    }
    return new KayentaLiquibaseGuard(environment);
  }

  @Test
  void skipsMigrationWhenKayentaSqlPropertyIsAbsent() {
    SpringLiquibase korkProxy = new SpringLiquibase();
    korkProxy.setShouldRun(true);

    Object result = guard(null).postProcessBeforeInitialization(korkProxy, "liquibase");

    assertThat(result).isInstanceOf(SpringLiquibase.class).isNotSameAs(korkProxy);
    assertThat(shouldRun(result)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"liquibase", "secondaryLiquibase"})
  void skipsMigrationWhenKayentaSqlDisabled(String beanName) {
    SpringLiquibase korkProxy = new SpringLiquibase();
    korkProxy.setShouldRun(true);

    Object result = guard("false").postProcessBeforeInitialization(korkProxy, beanName);

    assertThat(result).isNotSameAs(korkProxy);
    assertThat(shouldRun(result)).isFalse();
  }

  @Test
  void leavesMigrationUntouchedWhenKayentaSqlEnabled() {
    SpringLiquibase korkProxy = new SpringLiquibase();
    korkProxy.setShouldRun(true);

    Object result = guard("true").postProcessBeforeInitialization(korkProxy, "liquibase");

    assertThat(result).isSameAs(korkProxy);
    assertThat(shouldRun(result)).isTrue();
  }

  @Test
  void ignoresOtherBeanNames() {
    SpringLiquibase unrelated = new SpringLiquibase();
    unrelated.setShouldRun(true);

    Object result = guard(null).postProcessBeforeInitialization(unrelated, "someOtherLiquibase");

    assertThat(result).isSameAs(unrelated);
  }

  @Test
  void ignoresNonSpringLiquibaseBeans() {
    Object bean = new Object();

    Object result = guard(null).postProcessBeforeInitialization(bean, "liquibase");

    assertThat(result).isSameAs(bean);
  }

  @Nested
  class InApplicationContext {

    private final ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withPropertyValues("sql.enabled=true")
            .withBean("liquibase", StubSpringLiquibase.class, StubSpringLiquibase::new)
            .withUserConfiguration(KayentaLiquibaseConfiguration.class);

    @Test
    void skipsMigrationWhenKayentaSqlPropertyIsAbsent() {
      runner.run(
          context ->
              assertThat(context.getBean("liquibase", SpringLiquibase.class))
                  .isNotInstanceOf(StubSpringLiquibase.class)
                  .matches(liquibase -> !shouldRun(liquibase), "shouldRun=false"));
    }

    @Test
    void leavesMigrationUntouchedWhenKayentaSqlEnabled() {
      runner
          .withPropertyValues("kayenta.sql.enabled=true")
          .run(
              context ->
                  assertThat(context.getBean("liquibase", SpringLiquibase.class))
                      .isInstanceOf(StubSpringLiquibase.class)
                      .matches(KayentaLiquibaseConfigurationTest::shouldRun, "shouldRun=true"));
    }
  }

  /** Stands in for kork's proxy and never opens a database connection. */
  static class StubSpringLiquibase extends SpringLiquibase {

    StubSpringLiquibase() {
      setShouldRun(true);
    }

    @Override
    public void afterPropertiesSet() {}
  }
}
