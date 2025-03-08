/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.cats.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.sql.cache.SqlCache
import com.netflix.spinnaker.cats.sql.cache.SqlCacheMetrics
import com.netflix.spinnaker.config.SqlConstraintsInitializer
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import org.jooq.SQLDialect
import org.testcontainers.DockerClientFactory
import spock.lang.Requires

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Requires({ DockerClientFactory.instance().isDockerAvailable() })
class MySqlCacheSpec extends SqlCacheSpec {
  def providerCacheConfiguration = new StaticProviderCacheConfiguration(supportsFullEviction: false)

  @Override
  Cache getSubject() {
    def mapper = new ObjectMapper()
    def clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC"))
    def sqlRetryProperties = new SqlRetryProperties(new RetryProperties(1, 10), new RetryProperties(1, 10))

    def dynamicConfigService = Mock(DynamicConfigService) {
      getConfig(_ as Class, _ as String, _) >> 2
    }

    SqlTestUtil.TestDatabase testDatabase = SqlTestUtil.initTcMysqlDatabase()
    context = testDatabase.context
    dataSource = testDatabase.dataSource

    return new SqlCache(
      "test",
      context,
      mapper,
      null,
      clock,
      sqlRetryProperties,
      "test",
      Mock(SqlCacheMetrics),
      dynamicConfigService,
      new SqlConstraintsInitializer().getDefaultSqlConstraints(SQLDialect.MYSQL),
      providerCacheConfiguration
    )
  }

  def cleanup() {
    providerCacheConfiguration.supportsFullEviction = false
  }

  def "mergeAll with full eviction support"() {
    when:
    providerCacheConfiguration.supportsFullEviction = false
    ((WriteableCache) cache).mergeAll("keys", [
      createData("keys-1"),
      createData("keys-2")
    ])
    ((WriteableCache) cache).mergeAll("keys", [])
    def retrieved = ((SqlCache) cache).getAll("keys")

    then:
    retrieved.size() == 2

    when:
    providerCacheConfiguration.supportsFullEviction = true
    ((WriteableCache) cache).mergeAll("keys", [
      createData("keys-1"),
      createData("keys-2")
    ])
    ((WriteableCache) cache).mergeAll("keys", [])

    retrieved = ((SqlCache) cache).getAll("keys")

    then:
    retrieved.isEmpty()
  }
}
