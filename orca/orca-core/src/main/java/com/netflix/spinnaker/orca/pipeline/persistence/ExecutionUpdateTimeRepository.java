/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.pipeline.persistence;

import java.time.Instant;
import javax.annotation.Nullable;

/**
 * An ExecutionUpdateTimeRepository is responsible for persisting the latest update time for a
 * particular execution under an execution ID key
 */
public interface ExecutionUpdateTimeRepository {
  /**
   * Updates the repository with the latest update time for the pipeline execution
   *
   * @param id Pipeline execution ID
   * @param latestUpdate Latest update timestamp of the pipeline execution
   */
  void putPipelineExecutionUpdate(String id, Instant latestUpdate);

  /**
   * Retrieves the latest update time for the pipeline execution given by the execution id, or null
   * if there is no update information for the pipeline execution
   *
   * @param id Pipeline execution ID
   * @return Latest update timestamp of the pipeline execution
   */
  @Nullable
  Instant getPipelineExecutionUpdate(String id);

  /**
   * Updates the repository with the latest update time for the stage execution
   *
   * @param id Stage execution ID
   * @param latestUpdate Latest update timestamp of the stage execution
   */
  void putStageExecutionUpdate(String id, Instant latestUpdate);

  /**
   * Retrieves the latest update time for the stage execution given by the execution id, or null if
   * there is no update information for the stage execution
   *
   * @param id Stage execution ID
   * @return Latest update timestamp of the stage execution
   */
  @Nullable
  Instant getStageExecutionUpdate(String id);
}
