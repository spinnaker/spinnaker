/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.extensionpoint.pipeline;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * A preprocessor that can modify an Execution upon initial receipt of the configuration.
 */
public interface ExecutionPreprocessor {

  /**
   * Returns whether or not the preprocess can handle the inbound execution.
   */
  boolean supports(@Nonnull Map<String, Object> execution, @Nonnull Type type);

  /**
   * Allows modification of an execution configuration.
   */
  @Nonnull Map<String, Object> process(@Nonnull Map<String, Object> execution);

  enum Type {
    PIPELINE,
    ORCHESTRATION
  }
}
