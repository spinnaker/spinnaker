/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.front50.migrations;

import static net.logstash.logback.argument.StructuredArguments.value;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Migration runner runs all the registered migrations as scheduled. By default, migration runner
 * will <strong>not</strong> run and need to set <code>migrations.enabled</code> property to enable
 * it. The interval between migration runs can be set using <code>migrations.intervalMs</code>, and
 * the initial delay before running the first migration can be set using <code>
 * migrations.initialDelayMs</code> (can be useful if you initialize migrations in plugins, and they
 * need some time to start up). Default values are 8 hours and 10 seconds respectively.
 *
 * <p>Note: Ideally migrations should be running only on one instance.
 */
@Component
@ConditionalOnProperty(name = "migrations.enabled", havingValue = "true", matchIfMissing = false)
public class MigrationRunner {
  private final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

  private final ApplicationContext applicationContext;

  public MigrationRunner(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /** Run migrations every 8hrs (by default). */
  @Scheduled(
      fixedDelayString = "${migrations.intervalMs:28800000}",
      initialDelayString = "${migrations.initialDelayMs:10000}")
  void run() {
    applicationContext.getBeansOfType(Migration.class).values().stream()
        .filter(Migration::isValid)
        .forEach(
            migration -> {
              try {
                log.debug(
                    "Running migration: {}", value("class", migration.getClass().getSimpleName()));
                migration.run();
                log.debug(
                    "Migration complete: {}", value("class", migration.getClass().getSimpleName()));
              } catch (Exception e) {
                log.error(
                    "Migration failure ({}):",
                    value("class", migration.getClass().getSimpleName()),
                    e);
              }
            });
  }
}
