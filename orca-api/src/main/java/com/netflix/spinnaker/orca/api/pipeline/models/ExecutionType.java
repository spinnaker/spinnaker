/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.orca.api.pipeline.models;

/**
 * Defines a category of a {@link PipelineExecution}, used primarily for organization within the UI.
 *
 * <p>TODO(rz): Deprecate in favor of a labels concept?
 */
public enum ExecutionType {
  /** Executions will show under the "Pipelines" tab of Deck. */
  PIPELINE,

  /** Executions will show under the "Tasks" tab of Deck. */
  ORCHESTRATION;

  @Override
  public String toString() {
    return name().toLowerCase();
  }
}
