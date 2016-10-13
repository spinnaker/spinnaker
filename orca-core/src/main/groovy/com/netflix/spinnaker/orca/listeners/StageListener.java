/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.listeners;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import org.springframework.core.Ordered;

public interface StageListener extends Ordered, Comparable<StageListener> {
  default <T extends Execution<T>> void beforeTask(Persister persister,
                                                   Stage<T> stage,
                                                   Task task) {
    // do nothing
  }

  default <T extends Execution<T>> void beforeStage(Persister persister,
                                                    Stage<T> stage) {
    // do nothing
  }

  default <T extends Execution<T>> void afterTask(Persister persister,
                                                  Stage<T> stage,
                                                  Task task,
                                                  ExecutionStatus executionStatus,
                                                  boolean wasSuccessful) {
    // do nothing
  }

  default <T extends Execution<T>> void afterStage(Persister persister,
                                                   Stage<T> stage) {
    // do nothing
  }

  @Override
  default int getOrder() {
    return 0;
  }

  @Override
  default int compareTo(StageListener o) {
    return o.getOrder() - getOrder();
  }
}
