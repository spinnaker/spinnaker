/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */
package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.ApplicationVetoRepository
import com.netflix.spinnaker.keel.persistence.metamodel.Tables.APPLICATION_VETO
import org.jooq.DSLContext

class SqlApplicationVetoRepository(
  private val jooq: DSLContext
) : ApplicationVetoRepository {
  override fun appVetoed(application: String): Boolean =
    jooq
      .selectOne()
      .from(APPLICATION_VETO)
      .where(APPLICATION_VETO.APPLICATION_NAME.eq(application))
      .fetchOne() != null

  override fun optOut(application: String) {
    jooq.insertInto(APPLICATION_VETO)
      .set(APPLICATION_VETO.APPLICATION_NAME, application)
      .execute()
  }

  override fun optIn(application: String) {
    jooq.deleteFrom(APPLICATION_VETO)
      .where(APPLICATION_VETO.APPLICATION_NAME.eq(application))
      .execute()
  }

  override fun getAll(): Set<String> =
    jooq.select(APPLICATION_VETO.APPLICATION_NAME)
      .from(APPLICATION_VETO)
      .fetch()
      .map { (json) ->
        json
      }
      .toSet()
}
