/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.sql

import com.netflix.spinnaker.clouddriver.data.task.Task
import java.sql.ResultSet

class TaskMapper(
  private val sqlTaskRepository: SqlTaskRepository
) {

  fun map(rs: ResultSet): Collection<Task> {
    val results = mutableListOf<SqlTask>()
    while (rs.next()) {
      results.add(
        SqlTask(
          rs.getString("id"),
          rs.getString("owner_id"),
          rs.getString("request_id"),
          rs.getLong("created_at"),
          sqlTaskRepository
        )
      )
    }
    return results
  }
}
