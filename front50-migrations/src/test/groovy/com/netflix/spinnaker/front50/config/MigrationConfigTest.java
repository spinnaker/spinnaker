/*
 * Copyright (c) 2024 Schibsted ASA. All rights reserved
 */

package com.netflix.spinnaker.front50.config;

import com.netflix.spinnaker.front50.migrations.MigrationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class MigrationConfigTest {

  @Bean
  public MigrationRunner migrationRunner(ApplicationContext applicationContext) {
    return new MigrationRunner(applicationContext);
  }
}
