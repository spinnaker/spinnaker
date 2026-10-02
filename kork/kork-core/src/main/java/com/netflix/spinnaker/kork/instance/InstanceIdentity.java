/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.kork.instance;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * A best-effort, process-local identifier for this instance, used to name things that must be
 * distinguishable across a fleet of otherwise-identical pods/processes (e.g. a lock owner, or a
 * Redis Streams consumer name within a shared consumer group).
 *
 * <p>This is a behavior-preserving extraction of an idiom that had been independently reimplemented
 * in {@code LockManager.getOwnerName()} and clouddriver's {@code
 * PubSubAgentRunner.resolveConsumerName()}: prefer the local hostname, falling back to a random
 * identifier computed once per JVM if hostname resolution fails.
 */
public final class InstanceIdentity {
  private static final String FALLBACK_ID = UUID.randomUUID().toString();

  private InstanceIdentity() {}

  public static String getLocalInstanceId() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return FALLBACK_ID;
    }
  }
}
