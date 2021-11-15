/*
 * Copyright 2021 Salesforce, Inc.
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
package com.netflix.spinnaker.kork.sql.test;

import static org.junit.Assume.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.testcontainers.DockerClientFactory;

/**
 * Verify that SqlTestUtil can bring up database containers. Beyond testing the code, it also helps
 * to verify that appropriate docker images are available in CI environments.
 */
@RunWith(JUnitPlatform.class)
public class SqlTestUtilTest {

  @BeforeAll
  static void setupOnce() {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable());
  }

  @Test
  void testInitTcMysqlDatabase() {
    // This would typically be in a @BeforeAll-annotated method, but here
    // bringing up the container is the test.
    try (SqlTestUtil.TestDatabase ignored = SqlTestUtil.initTcMysqlDatabase()) {}
  }

  @Test
  void testInitTcPostgresDatabase() {
    try (SqlTestUtil.TestDatabase ignored = SqlTestUtil.initTcPostgresDatabase()) {}
  }
}
