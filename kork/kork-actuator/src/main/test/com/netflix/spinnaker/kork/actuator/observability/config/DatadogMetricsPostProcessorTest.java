/*
 * Copyright 2025
 */
package com.netflix.spinnaker.kork.actuator.observability.config;

import static org.junit.Assert.assertEquals;

import java.util.Properties;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

public class DatadogMetricsPostProcessorTest {

  @Test
  public void setsManagementPropertyWhenEnabledTrue() {
    ConfigurableEnvironment env = new StandardEnvironment();
    Properties p = new Properties();
    p.setProperty("observability.config.metrics.datadog.enabled", "true");
    env.getPropertySources().addFirst(new PropertiesPropertySource("test", p));

    new DatadogMetricsPostProcessor()
        .postProcessEnvironment(env, new SpringApplication(Object.class));

    assertEquals("true", env.getProperty("management.metrics.export.datadog.enabled"));
  }

  @Test
  public void setsManagementPropertyWhenEnabledFalse() {
    ConfigurableEnvironment env = new StandardEnvironment();
    Properties p = new Properties();
    p.setProperty("observability.config.metrics.datadog.enabled", "false");
    env.getPropertySources().addFirst(new PropertiesPropertySource("test", p));

    new DatadogMetricsPostProcessor()
        .postProcessEnvironment(env, new SpringApplication(Object.class));

    assertEquals("false", env.getProperty("management.metrics.export.datadog.enabled"));
  }

  @Test
  public void defaultsToFalseWhenPropertyMissing() {
    ConfigurableEnvironment env = new StandardEnvironment();

    new DatadogMetricsPostProcessor()
        .postProcessEnvironment(env, new SpringApplication(Object.class));

    assertEquals("false", env.getProperty("management.metrics.export.datadog.enabled"));
  }
}
