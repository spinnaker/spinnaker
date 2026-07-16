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

public class NoOpStartupConcurrencyControl implements StartupConcurrencyControl {
  private static final StartupConcurrencyPermit NO_OP_PERMIT =
      new NoOpStartupConcurrencyControlPermit();

  @Override
  public StartupConcurrencyPermit acquire() {
    return NO_OP_PERMIT;
  }

  @Override
  public Optional<StartupConcurrencyPermit> acquire(long timeoutMillis) {
    return Optional.of(NO_OP_PERMIT);
  }
}

class NoOpStartupConcurrencyControlPermit implements StartupConcurrencyPermit {
  @Override
  public void close() {}
}
