/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.cats.agent;

import java.util.Optional;

/**
 * This interface is used to control concurrency mainly during startup of Streaming agents that
 * usually require a `list` operation before starting to `observe` changes to resources. If we don't
 * impose a concurrency limit per node we might end up starving resources and on extreme cases it
 * might even lead to OOM errors on the JVM due to the peak in resource allocation. Standard polling
 * agents have a similar control on the overall execution of the agents.
 */
public interface StartupConcurrencyControl {

  /**
   * Acquirea a permit to run an expensive `list-like` operation
   *
   * @return permit object in which you can release the pemission acquired to run the expensive
   *     operation
   * @throws InterruptedException
   */
  StartupConcurrencyPermit acquire() throws InterruptedException;

  /**
   * Tries to acquirea a permit to run an expensive `list-like` operation within a time limit
   *
   * @return permit object in which you can release the pemission acquired to run the expensive
   *     operation or an Empty Optional in case it timed out
   * @throws InterruptedException
   */
  Optional<StartupConcurrencyPermit> acquire(long timeoutMillis) throws InterruptedException;
}
