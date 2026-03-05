/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for SqlUnknownAgentCleanupAgent.
 *
 * This agent periodically scans cache tables and removes records created by caching agents
 * that are no longer configured (e.g., after an account is removed from clouddriver).
 *
 * Example configuration:
 * ```yaml
 * sql:
 *   unknown-agent-cleanup-agent:
 *     enabled: true
 *     minRecordAgeSeconds: 300   # Only delete records older than 5 minutes
 *     dryRun: true               # Log what would be deleted without actually deleting
 * ```
 */
@ConfigurationProperties("sql.unknown-agent-cleanup-agent")
class SqlUnknownAgentCleanupProperties {
  /** Whether the cleanup agent is enabled. Defaults to false for safety. */
  var enabled: Boolean = false

  /** How often the cleanup agent runs, in seconds. */
  var pollIntervalSeconds: Long = 120

  /** Maximum execution time before the agent is considered timed out, in seconds. */
  var timeoutSeconds: Long = 60

  /**
   * Minimum age of records (in seconds) before they're eligible for deletion.
   * Protects against deleting recently-written data during agent startup races.
   * Set to 0 to disable the age check.
   */
  var minRecordAgeSeconds: Long = 300

  /** Number of records to delete per SQL DELETE statement. */
  var deleteBatchSize: Int = 100

  /** If true, log what would be deleted but don't actually delete. Useful for validation. */
  var dryRun: Boolean = false

  /** List of data type names to exclude from cleanup (e.g., ["instances", "serverGroups"]). */
  var excludedDataTypes: List<String> = emptyList()
}

