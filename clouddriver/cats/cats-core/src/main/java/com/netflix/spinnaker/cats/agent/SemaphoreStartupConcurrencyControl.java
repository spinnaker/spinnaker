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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class SemaphoreStartupConcurrencyControl implements StartupConcurrencyControl {
  private final Semaphore semaphore;

  public SemaphoreStartupConcurrencyControl(int concurrencyLimit) {
    this.semaphore = new Semaphore(concurrencyLimit);
  }

  @Override
  public StartupConcurrencyPermit acquire() throws InterruptedException {
    semaphore.acquire();
    return new SemaphoreStartupConcurrencyControlPermit(semaphore);
  }

  @Override
  public Optional<StartupConcurrencyPermit> acquire(long timeoutMillis)
      throws InterruptedException {
    if (semaphore.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
      return Optional.of(new SemaphoreStartupConcurrencyControlPermit(semaphore));
    } else {
      return Optional.empty();
    }
  }
}

class SemaphoreStartupConcurrencyControlPermit implements StartupConcurrencyPermit {
  private final Semaphore semaphore;

  public SemaphoreStartupConcurrencyControlPermit(Semaphore semaphore) {
    this.semaphore = semaphore;
  }

  @Override
  public void close() {
    semaphore.release();
  }
}
