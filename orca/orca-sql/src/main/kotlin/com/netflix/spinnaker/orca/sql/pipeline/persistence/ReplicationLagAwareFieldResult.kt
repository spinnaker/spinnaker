/*
 * Copyright 2024 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.sql.pipeline.persistence

/**
 * The result of querying for a database field using replication-lag-aware
 * logic.  Use "data object" instead of "object" and remove toString overrides
 * wth kotlin >= 1.7.20.
 */
sealed interface ReplicationLagAwareFieldResult {
  data class Success(val result: String) : ReplicationLagAwareFieldResult
  object NotFound: ReplicationLagAwareFieldResult {
    override fun toString(): String = "NotFound"
  }
  object InvalidVersion: ReplicationLagAwareFieldResult {
    override fun toString(): String = "InvalidVersion"
  }
  object MissingFromReplicationLagRepository: ReplicationLagAwareFieldResult {
    override fun toString(): String = "MissingFromReplicationLagRepository"
  }
  object Failure: ReplicationLagAwareFieldResult {
    override fun toString(): String = "Failure"
  }
}
