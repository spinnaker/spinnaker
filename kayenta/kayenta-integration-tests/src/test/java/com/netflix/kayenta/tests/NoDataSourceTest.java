/*
 * Copyright 2026 Spinnaker contributors.
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
package com.netflix.kayenta.tests;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Kayenta ships kayenta-sql (and so spring-boot-starter-data-jpa) on its classpath, but only wants
 * a DataSource when kayenta.sql.enabled is set. The test configuration deliberately has no {@code
 * spring.autoconfigure.exclude} entry for DataSourceAutoConfiguration, so this fails with "Failed
 * to configure a DataSource" at startup if the exclusion in code stops working.
 */
public class NoDataSourceTest extends BaseIntegrationTest {

  @Autowired ApplicationContext applicationContext;

  @Test
  public void noDataSourceIsCreatedWhenSqlIsNotEnabled() {
    assertThat(applicationContext.getBeanNamesForType(DataSource.class)).isEmpty();
  }
}
