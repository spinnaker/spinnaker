/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.cats.pubsub;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

class PubSubTestSupport implements AutoCloseable {
  final Connection connection;
  final DSLContext sql;
  final StateMachine state;

  PubSubTestSupport() throws SQLException {
    connection =
        DriverManager.getConnection(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false", "sa", "");
    sql = DSL.using(connection, SQLDialect.MYSQL);
    sql.execute(
        """
        CREATE TABLE pubsub_agent_state (
          agent_type VARCHAR(512) PRIMARY KEY, current_state VARCHAR(20) NOT NULL,
          last_transition_time BIGINT, last_execution_time BIGINT,
          last_duration BIGINT, data_processed BIGINT)
        """);
    state = new StateMachine(sql);
  }

  @Override
  public void close() throws SQLException {
    connection.close();
  }
}
