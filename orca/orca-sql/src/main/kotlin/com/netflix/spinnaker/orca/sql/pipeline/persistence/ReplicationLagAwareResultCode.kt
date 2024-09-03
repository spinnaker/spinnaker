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

import com.netflix.spinnaker.orca.sql.pipeline.persistence.ReplicationLagAwareResultCode.INVALID_VERSION
import com.netflix.spinnaker.orca.sql.pipeline.persistence.ReplicationLagAwareResultCode.MISSING_FROM_REPLICATION_LAG_REPOSITORY
import com.netflix.spinnaker.orca.sql.pipeline.persistence.ReplicationLagAwareResultCode.NOT_FOUND

/**
 * Defines result codes for an replication-lag-aware operation
 *
 * [NOT_FOUND], [INVALID_VERSION], and [MISSING_FROM_REPLICATION_LAG_REPOSITORY] are all
 * possible reasons why an ExecutionMapper will return an empty collection of PipelineExecutions.
 * ExceptionMapper doesn't use [FAILURE], but it's helpful for e.g. exception handling.
 * The caller of [ExecutionMapper.map] can use information in order to perform additional
 * operations after receiving an empty collection of PipelineExecutions.
 */
enum class ReplicationLagAwareResultCode {
  /**
   * Represents a successful result
   */
  SUCCESS,

  /**
   * The ResultSet representing a List of PipelineExecutions was empty. Therefore,
   * no executions were found
   */
  NOT_FOUND,

  /**
   * When requireUpToDateVersion is true and one or more executions fail to satisfy the version
   * requirements in [ExecutionMapper.isUpToDateVersion]
   */
  INVALID_VERSION,

  /**
   * When requireUpToDateVersion is true and one or more execution IDs are missing from the
   * ReplicationLagAwareRepository. This is a more specific case of [INVALID_VERSION]
   * since we will never be able to determine whether the version of an execution is valid if it
   * is missing from the ReplicationLagAwareRepository
   */
  MISSING_FROM_REPLICATION_LAG_REPOSITORY,

  /**
   * When there's an exception reading from the read pool
   */
  FAILURE
}
