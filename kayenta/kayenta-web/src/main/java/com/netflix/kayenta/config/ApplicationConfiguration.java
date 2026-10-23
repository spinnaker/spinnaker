package com.netflix.kayenta.config;

import com.netflix.spinnaker.config.PluginsAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Import({
  KayentaConfiguration.class,
  WebConfiguration.class,
  PluginsAutoConfiguration.class,
})
@ComponentScan({
  "com.netflix.spinnaker.config",
  "com.netflix.spinnaker.endpoint",
})
// Kayenta only needs a DataSource when kayenta.sql.enabled is set, and SqlConfiguration imports
// DataSourceAutoConfiguration itself in that case. Excluding it here rather than via
// spring.autoconfigure.exclude means a config that names the class wrongly (e.g. its Spring Boot 3
// package) can't silently re-enable it.
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
@EnableAsync
@EnableScheduling
public class ApplicationConfiguration {}
