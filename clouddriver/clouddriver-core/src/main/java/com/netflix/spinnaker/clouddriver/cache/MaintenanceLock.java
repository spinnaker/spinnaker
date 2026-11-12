/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.clouddriver.cache;

/**
 * Cluster-wide, per-agent mutual exclusion for {@link MaintenanceAgent}s.
 *
 * <p>The lock key is the bare agent type, which is the same key the CATS clustered schedulers use,
 * so pods that still schedule an agent through CATS contend on the same lock during a rolling
 * deploy.
 */
public interface MaintenanceLock {

  /**
   * Try to take the lock for {@code agentType}.
   *
   * @param ttlMillis how long the lock is held before it expires if not extended
   * @return true if this node now owns the lock
   */
  boolean tryAcquire(String agentType, long ttlMillis);

  /**
   * Extend the lock, if we own it, so that it expires at {@code untilEpochMillis} (the time the
   * next run is due). No-op if this node does not own the lock.
   */
  void hold(String agentType, long untilEpochMillis);

  /** Release the lock if this node owns it. */
  void release(String agentType);
}
