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

package com.netflix.spinnaker.cats.agent;

/** An AgentScheduler manages the execution of a CachingAgent. */
public interface AgentScheduler<T extends AgentLock> {
  void schedule(
      Agent agent,
      AgentExecution agentExecution,
      ExecutionInstrumentation executionInstrumentation);

  default void unschedule(Agent agent) {};

  /**
   * @return True iff this scheduler supports synchronization between LoadData and OnDemand cache
   *     updates.
   */
  default boolean isAtomic() {
    return false;
  };

  /**
   * @param agent The agent being locked.
   * @return A "Lock" that will allow exclusive access to updating this agent's cache data. null iff
   *     isAtomic == false.
   */
  default T tryLock(Agent agent) {
    return null;
  };

  /**
   * @param lock The lock being released.
   * @return True iff the lock was still in our possession when the release call was made.
   */
  default boolean tryRelease(T lock) {
    return false;
  };

  /**
   * @param lock The lock being checked for validity.
   * @return True iff the lock is still in our possession.
   */
  default boolean lockValid(T lock) {
    return false;
  };
}
