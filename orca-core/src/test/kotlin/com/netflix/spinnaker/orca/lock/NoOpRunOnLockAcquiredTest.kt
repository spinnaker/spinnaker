/*
 * Copyright 2023 Armory, Inc.
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

package com.netflix.spinnaker.orca.lock

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.concurrent.Callable

class NoOpRunOnLockAcquiredTest {

  @Test
  @DisplayName("no op should execute runnable just once")
  fun noOpExecutesRunnable() {
    val classUnderTest = NoOpRunOnLockAcquired()
    val mockedRunnable = mock(Runnable::class.java)

    classUnderTest.execute(mockedRunnable, "key")

    verify(mockedRunnable).run()
  }

  @Test
  @DisplayName("no op should execute callable just once")
  fun noOpExecutesCallable() {
    val classUnderTest = NoOpRunOnLockAcquired()
    val mockedCallable = mock(Callable::class.java)

    classUnderTest.execute(mockedCallable, "key")

    verify(mockedCallable).call()
  }
}
