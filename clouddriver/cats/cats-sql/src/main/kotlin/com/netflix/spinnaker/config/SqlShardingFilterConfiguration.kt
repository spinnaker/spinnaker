/*
 * Copyright 2021 OpsMx
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
 *
 */

package com.netflix.spinnaker.config

import com.netflix.spinnaker.cats.cluster.DefaultNodeIdentity
import com.netflix.spinnaker.cats.sql.cluster.SqlCachingPodsObserver
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(value = ["caching.write-enabled"], matchIfMissing = true)
class SqlShardingFilterConfiguration {

  @Bean
  @ConditionalOnProperty(
    value = [
      "sql.enabled",
      "sql.scheduler.enabled",
      "cache-sharding.enabled"
    ]
  )
  fun shardingFilter(
    jooq: DSLContext,
    @Value("\${sql.table-namespace:#{null}}") tableNamespace: String?,
    dynamicConfigService: DynamicConfigService
  ): SqlCachingPodsObserver {
    return SqlCachingPodsObserver(
      jooq = jooq,
      nodeIdentity = DefaultNodeIdentity(),
      tableNamespace = tableNamespace,
      dynamicConfigService = dynamicConfigService
    )
  }


}
