/*
 * Copyright 2023 Netflix, Inc.
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

/** A default no-op implementation of {@link ReplicationLagAwareRepository} */
public class NoopReplicationLagAwareRepository implements ReplicationLagAwareRepository {

  /**
   * No-op
   *
   * @param id Pipeline execution ID
   * @param latestUpdate Latest update timestamp of the pipeline execution
   */
  @Override
  public void putPipelineExecutionUpdate(String id, Instant latestUpdate) {}

  /**
   * No-op
   *
   * @param id Pipeline execution ID
   * @return Instant.EPOCH
   */
  @Override
  public Instant getPipelineExecutionUpdate(String id) {
    return Instant.EPOCH;
  }

  /**
   * No-op
   *
   * @param id Pipeline execution ID
   * @param numStages Number of stages that belong to the execution
   */
  @Override
  public void putPipelineExecutionNumStages(String id, Integer numStages) {}

  /**
   * No-op
   *
   * @param id Pipeline execution ID
   * @return 0
   */
  @Override
  public Integer getPipelineExecutionNumStages(String id) {
    return 0;
  }

  /**
   * No-op
   *
   * @param id Stage execution ID
   * @param latestUpdate Latest update timestamp of the stage execution
   */
  @Override
  public void putStageExecutionUpdate(String id, Instant latestUpdate) {}

  /**
   * No-op
   *
   * @param id Stage execution ID
   * @return Instant.EPOCH
   */
  @Override
  public Instant getStageExecutionUpdate(String id) {
    return Instant.EPOCH;
  }
}
