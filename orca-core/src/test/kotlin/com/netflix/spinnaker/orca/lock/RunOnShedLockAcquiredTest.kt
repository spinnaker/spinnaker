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

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.SimpleLock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.`when`
import java.util.*
import java.util.concurrent.Callable

class RunOnShedLockAcquiredTest {

  private val lockProvider: LockProvider = Mockito.mock(LockProvider::class.java)
  private val runnable: Runnable = Mockito.mock(Runnable::class.java)
  private val callable: Callable<Boolean> = Mockito.mock(Callable::class.java) as Callable<Boolean>

  @AfterEach
  fun cleanup() {
    Mockito.reset(lockProvider, runnable, callable)
  }

  @BeforeEach
  fun setup(){
    `when`(callable.call()).thenReturn(true)
  }

  @Test
  @DisplayName("should return callable result, when lock successfully acquired")
  fun test1() {
    givenLockIsAcquired()

    val classUnderTest = RunOnShedLockAcquired(lockProvider)
    val result = classUnderTest.execute(callable, "key")

    assertTrue(result.lockAcquired)
    assertTrue(result.actionExecuted)
    assertNull(result.exception)
    Mockito.verify(callable).call()
    assertEquals(result.result, this.callable.call())
  }

  @Test
  @DisplayName("should run runnable, when lock successfully acquired")
  fun test2() {
    givenLockIsAcquired()

    val classUnderTest = RunOnShedLockAcquired(lockProvider)
    val result = classUnderTest.execute(runnable, "key")

    assertTrue(result.lockAcquired)
    assertTrue(result.actionExecuted)
    assertNull(result.exception)
    assertNull(result.result)
    Mockito.verify(runnable).run()
  }

  @Test
  @DisplayName("should not call callable, when lock was not acquired")
  fun test3(){
    givenLockWasNotAcquired()

    val classUnderTest = RunOnShedLockAcquired(lockProvider)
    val result = classUnderTest.execute(callable, "key")

    assertFalse(result.lockAcquired)
    assertFalse(result.actionExecuted)
    assertNull(result.exception)
    assertNull(result.result);
    Mockito.verify(callable, Mockito.never()).call()
  }

  @Test
  @DisplayName("should not call runnable, when lock was not acquired")
  fun test4(){
    givenLockWasNotAcquired()

    val classUnderTest = RunOnShedLockAcquired(lockProvider)
    val result = classUnderTest.execute(callable, "key")

    assertFalse(result.lockAcquired)
    assertFalse(result.actionExecuted)
    assertNull(result.exception)
    assertNull(result.result);
    Mockito.verify(runnable, Mockito.never()).run()
  }

  private fun givenLockIsAcquired(){
    `when`(lockProvider.lock(any())).thenReturn(Optional.of(SimpleLock { -> println("Unlocking") }))
  }

  private fun givenLockWasNotAcquired(){
    `when`(lockProvider.lock(any())).thenReturn(Optional.empty())
  }
}
