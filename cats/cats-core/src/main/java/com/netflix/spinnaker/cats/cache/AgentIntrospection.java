/*
 * Copyright 2018 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.cats.cache;

import com.netflix.spinnaker.cats.agent.CacheResult;

/**
 * This is meant to store data about a single agent execution that _doesn't_ make sense to reasonably report to a
 * monitoring system. Being able to inspect a single clouddriver node's use of these caching agents, having them report
 * provider-specific details in the `details` field, (e.g. which namespaces/kinds are cached) and correlate that with
 * details of how long the caching agents are executing, allowing users to both diagnose faulty/underprovisioned nodes, as
 * well as tune their caching configuration by adjusting provider-specific fields.
 */
public interface AgentIntrospection {
  String getId();
  String getProvider();
  int getTotalAdditions();
  int getTotalEvictions();
  Long getLastExecutionDurationMs();
  Throwable getLastError();
  String getLastExecutionStartDate();
  void finishWithError(Throwable error, CacheResult result);
  void finish(CacheResult result);
}
