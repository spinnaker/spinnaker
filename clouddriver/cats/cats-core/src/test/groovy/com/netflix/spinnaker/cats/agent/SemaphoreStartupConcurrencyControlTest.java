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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Optional;
import org.junit.jupiter.api.Test;

public class SemaphoreStartupConcurrencyControlTest {

  @Test
  public void testRespectsTheLimit() throws InterruptedException {
    StartupConcurrencyControl concurrencyControl = new SemaphoreStartupConcurrencyControl(2);
    StartupConcurrencyPermit permit1 = concurrencyControl.acquire();
    StartupConcurrencyPermit permit2 = concurrencyControl.acquire();
    Optional<StartupConcurrencyPermit> permit3 = concurrencyControl.acquire(1);
    assertThat(permit3).isNotPresent();
  }

  @Test
  public void testWorksWithTryWithResourceBlocks() throws InterruptedException {
    StartupConcurrencyControl concurrencyControl = new SemaphoreStartupConcurrencyControl(2);
    try (StartupConcurrencyPermit permit1 = concurrencyControl.acquire()) {
      try (StartupConcurrencyPermit permit2 = concurrencyControl.acquire()) {
        Optional<StartupConcurrencyPermit> permit3 = concurrencyControl.acquire(1);
        assertThat(permit3).isNotPresent();
      }
      Optional<StartupConcurrencyPermit> permit4 = concurrencyControl.acquire(1);
      assertThat(permit4).isPresent();
    }
  }

  @Test
  public void testReturnsPermitAfterReleasing() throws InterruptedException {
    StartupConcurrencyControl concurrencyControl = new SemaphoreStartupConcurrencyControl(2);
    StartupConcurrencyPermit permit1 = concurrencyControl.acquire();
    StartupConcurrencyPermit permit2 = concurrencyControl.acquire();
    Thread concurrent =
        new Thread() {
          @Override
          public void run() {
            assertThatNoException()
                .isThrownBy(
                    () -> {
                      long start = System.nanoTime();
                      StartupConcurrencyPermit permit3 = concurrencyControl.acquire();
                      long delta = System.nanoTime() - start;
                      assertThat(delta).isGreaterThanOrEqualTo(50);
                      assertThat(permit3).isNotNull();
                    });
          }
        };
    concurrent.start();
    Thread.sleep(50);
    permit1.close();
    concurrent.join();
  }
}
