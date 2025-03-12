/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.front50.api.validator;

import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;

/**
 * A {@link PipelineValidator} provides a hook where custom validation can be applied to pipeline
 * pre-save/update operations.
 */
@Beta
public interface PipelineValidator extends SpinnakerExtensionPoint {
  /**
   * @param pipeline the pipeline being created/modified
   * @param errors specific validation errors for @param pipeline
   */
  void validate(Pipeline pipeline, ValidatorErrors errors);
}
