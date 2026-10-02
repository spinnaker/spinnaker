/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.clouddriver.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.core.test.TaskRepositoryTck;
import com.netflix.spinnaker.config.ConnectionPools;
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;

/** Runs the shared {@link TaskRepositoryTck} against Postgres, alongside the MySQL run. */
class SqlTaskRepositoryPostgresTest extends TaskRepositoryTck<SqlTaskRepository> {

  private SqlTestUtil.TestDatabase database;

  @Override
  protected SqlTaskRepository createTaskRepository() {
    database = SqlTestUtil.initTcPostgresDatabase();
    return new SqlTaskRepository(
        database.context,
        new ObjectMapper(),
        Clock.systemDefaultZone(),
        ConnectionPools.TASKS.getValue());
  }

  @AfterEach
  void cleanup() {
    if (database != null) {
      SqlTestUtil.cleanupDb(database.context);
    }
  }
}
