/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.orchestration

import com.netflix.spinnaker.clouddriver.data.task.Task

/**
 * Implementations of this interface should perform orchestration of operations in a workflow. Often will be used in
 * conjunction with {@link AtomicOperation} instances.
 *
 *
 */
public interface OrchestrationProcessor {

  /**
   * This is the invocation point of orchestration.
   * @param key a unique key, used to de-dupe orchestration requests
   * @return a list of results
   */
  Task process(List<AtomicOperation> atomicOperations, String key)
}
