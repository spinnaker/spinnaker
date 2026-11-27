/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.front50;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.front50.model.SqlStorageService;
import com.netflix.spinnaker.front50.model.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Starts the whole Front50 application, backed by in-memory SQL storage. */
@SpringBootTest(
    classes = Main.class,
    properties = {
      "spring.application.name=front50",
      "spring.main.allow-bean-definition-overriding=true",
      "sql.enabled=true",
      "sql.connectionPools.default.default=true",
      "sql.connectionPools.default.jdbcUrl=jdbc:h2:mem:front50-main-test",
      "sql.connectionPools.default.dialect=H2",
      "sql.migration.jdbcUrl=jdbc:h2:mem:front50-main-test",
      "services.fiat.baseUrl=http://localhost:7003"
    })
class MainTest {

  @Autowired StorageService storageService;

  @Test
  void startsWithSqlStorage() {
    assertThat(storageService).isInstanceOf(SqlStorageService.class);
  }
}
