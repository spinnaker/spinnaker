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

import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MigrationRunner {
  private final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

  @Autowired Collection<Migration> migrations;

  /** Run migrations every 8hrs. */
  @Scheduled(fixedDelay = 28800000)
  void run() {
    migrations.stream()
        .filter(Migration::isValid)
        .forEach(
            migration -> {
              try {
                migration.run();
              } catch (Exception e) {
                log.error(
                    "Migration failure ({}):",
                    value("class", migration.getClass().getSimpleName()),
                    e);
              }
            });
  }
}
