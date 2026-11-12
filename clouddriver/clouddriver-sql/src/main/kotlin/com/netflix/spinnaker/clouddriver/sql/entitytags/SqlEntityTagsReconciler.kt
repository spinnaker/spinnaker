/*
 * Copyright 2026 Spinnaker Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.sql.entitytags

import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.model.EntityTagsProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroupProvider
import com.netflix.spinnaker.clouddriver.tags.EntityTagger.ENTITY_TYPE_SERVER_GROUP
import java.time.Clock
import java.time.Duration
import org.slf4j.LoggerFactory

/**
 * Removes entity tags that reference server groups which no longer exist.
 *
 * Tags for cloud providers without a registered [ServerGroupProvider] are never removed, and tags
 * younger than [minAge] are left alone as a safeguard against racing with a server group that is
 * still being created.
 */
class SqlEntityTagsReconciler(
  serverGroupProviders: Collection<ServerGroupProvider>,
  private val clock: Clock,
  private val minAge: Duration = Duration.ofDays(14)
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val providersByCloudProvider = serverGroupProviders.associateBy { it.cloudProviderId }

  fun reconcile(
    entityTagsProvider: EntityTagsProvider,
    cloudProvider: String,
    account: String?,
    region: String?,
    dryRun: Boolean
  ): Map<String, Any> {
    val serverGroupProvider = providersByCloudProvider[cloudProvider]
      ?: return mapOf("dryRun" to dryRun, "orphanCount" to 0, "skipped" to "no ServerGroupProvider for $cloudProvider")

    val cutoff = clock.millis() - minAge.toMillis()
    val candidates = entityTagsProvider
      .getAll(cloudProvider, null, ENTITY_TYPE_SERVER_GROUP, null, null, account, region, null, null, Int.MAX_VALUE)
      .filter { (it.lastModified ?: Long.MAX_VALUE) < cutoff }

    val existing = serverGroupProvider.getServerGroupIdentifiers(account, region).map { it.lowercase() }.toSet()
    val orphaned = candidates.filter { !existing.contains(identifierFor(serverGroupProvider, it)) }

    log.debug(
      "Found {} server group entity tags (valid: {}, orphaned: {}, dryRun: {})",
      candidates.size, candidates.size - orphaned.size, orphaned.size, dryRun
    )

    if (!dryRun && orphaned.isNotEmpty()) {
      entityTagsProvider.bulkDelete(orphaned)
      log.info("Removed {} orphaned entity tags", orphaned.size)
    }

    return mapOf("dryRun" to dryRun, "orphanCount" to orphaned.size)
  }

  private fun identifierFor(provider: ServerGroupProvider, tags: EntityTags): String {
    val ref = tags.entityRef
    return provider.buildServerGroupIdentifier(ref.account, ref.region, ref.entityId).lowercase()
  }
}
