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

import com.netflix.spinnaker.cats.agent.RunnableAgent;

/**
 * An agent that maintains data written by serving pods (for example purging old tasks or events).
 *
 * <p>Maintenance agents are scheduled by {@link MaintenanceAgentScheduler} independently of caching
 * ({@code caching.write-enabled}), so they keep running on pods that have caching disabled. They
 * must never be registered with a CATS provider, otherwise the caching scheduler would run them as
 * well.
 */
public interface MaintenanceAgent extends RunnableAgent, CustomScheduledAgent {}
