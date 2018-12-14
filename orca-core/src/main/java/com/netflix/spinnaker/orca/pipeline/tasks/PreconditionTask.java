/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.pipeline.CheckPreconditionsStage;

/**
 * Interface for tasks used to evaluate a precondition.
 * <p>
 * A precondition task is intended to evaluates current state without any
 * side-effects. It will be executed by {@link CheckPreconditionsStage}.
 */
public interface PreconditionTask extends Task {
  String getPreconditionType();
}
