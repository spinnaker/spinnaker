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

import com.netflix.spinnaker.kork.lock.LockManager
import com.netflix.spinnaker.kork.lock.LockManager.LockStatus.ACQUIRED
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.core.SimpleLock
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.Callable

/**
 * Postpones the execution of an action until an external lock has been obtained
 */
interface RunOnLockAcquired {

  /**
   * Executes an action after lock identified by {@code keyName} was obtained
   *
   * @param action  action to execute once lock is acquired
   * @param keyName  name of a lock
   *
   * @return result of attempting to acquire a lock
   */
  fun execute(action: Runnable, keyName: String): RunOnLockResult<Void>

  /**
   * Executes an action after lock identified by {@code keyName} was obtained
   *
   * @param action  action to execute once lock is acquired
   * @param keyName  name of a lock
   *
   * @return result of attempting to acquire a lock and result of action execution
   */
  fun <R> execute(action: Callable<R>, keyName: String): RunOnLockResult<R?>

}

/**
 * This is a container object that stores the result of attempting to acquire a lock and executing an action if the lock is obtained.
 */
data class RunOnLockResult<R>(
  val lockAcquired: Boolean = false,
  val actionExecuted: Boolean = false,
  val exception: Exception? = null,
  val result: R? = null
)

/**
 * Implementation of {@code RunOnLockAcquired}. Delegates the locking attempt to LockProvider.
 * Executes action until shedlock has been obtained
 */
class RunOnShedLockAcquired(
  private val shedLockProvider: LockProvider
) : RunOnLockAcquired {

  private val log = LoggerFactory.getLogger(javaClass)
  override fun execute(action: Runnable, keyName: String): RunOnLockResult<Void> {
    val lockOpt = this.getLock(keyName)
    if (lockOpt.isEmpty) {
      log.error("Failed to acquire shedlock for key: {}", keyName)
      return RunOnLockResult(lockAcquired = false)
    }

    return try {
      log.debug("Executing action with a lock for key: {}", keyName)
      action.run()
      log.debug("Finished action execution with a lock for key: {}", keyName)
      RunOnLockResult(lockAcquired = true, actionExecuted = true)
    } catch (e: Exception) {
      log.error("An exception occurred while executing action with a lock for key: {}", keyName)
      RunOnLockResult(lockAcquired = true, exception = e)
    } finally {
      lockOpt.get().unlock()
      log.debug("Released shedlock for key {}", keyName)
    }
  }

  override fun <R> execute(action: Callable<R>, keyName: String): RunOnLockResult<R?> {
    val lockOpt = this.getLock(keyName)
    if (lockOpt.isEmpty) {
      log.error("Failed to acquire shedlock for key: {}", keyName)
      return RunOnLockResult(lockAcquired = false)
    }

    return try {
      log.debug("Executing action with a lock for key: {}", keyName)
      val callableResult = action.call()
      log.debug("Finished action execution with a lock for key: {}", keyName)
      RunOnLockResult(lockAcquired = true, actionExecuted = true, result = callableResult)

    } catch (e: Exception) {
      log.error("An exception occurred while executing action with a lock for key: {}", keyName)
      RunOnLockResult(lockAcquired = true, exception = e)
    } finally {
      lockOpt.get().unlock()
      log.debug("Released shedlock for key {}", keyName)
    }
  }

  private fun getLock(keyName: String): Optional<SimpleLock> {
    try {
      log.debug("Attempt to acquire shedlock for key: {}", keyName)
      return shedLockProvider.lock(LockConfiguration(Instant.now(), keyName, Duration.ofSeconds(1), Duration.ofMillis(200)))
    } catch (e: Exception) {
      log.error("An exception occurred during an attempt to acquire shedlock for key: {}", keyName)
      log.error(e.message)
      throw e
    }
  }

}


/**
 * Implementation of {@code RunOnLockAcquired}. Delegates the locking attempt to LockManager.
 * Executes action until redis lock has been obtained
 */
class RunOnRedisLockAcquired(
  private val lockManager: LockManager
) : RunOnLockAcquired {

  private fun lockOptions(name: String) = LockManager.LockOptions()
    .withLockName(name)
    .withMaximumLockDuration(Duration.ofSeconds(1L))

  override fun execute(action: Runnable, keyName: String): RunOnLockResult<Void> {
    return try {
      val acquireLock = lockManager.acquireLock(lockOptions(keyName), action)
      if (!acquireLock.lockStatus.equals(ACQUIRED)) {
        return RunOnLockResult(lockAcquired = false)
      }

      RunOnLockResult(
        lockAcquired = true,
        actionExecuted = true,
        result = acquireLock.onLockAcquiredCallbackResult
      )
    } catch (e: Exception) {
      RunOnLockResult(lockAcquired = true, exception = e)
    }
  }

  override fun <R> execute(action: Callable<R>, keyName: String): RunOnLockResult<R?> {
    return try {
      val acquireLock = lockManager.acquireLock(lockOptions(keyName), action)
      if (!acquireLock.lockStatus.equals(ACQUIRED)) {
        return RunOnLockResult(lockAcquired = false)
      }

      RunOnLockResult(
        lockAcquired = true,
        actionExecuted = true,
        result = acquireLock.onLockAcquiredCallbackResult
      )
    } catch (e: Exception) {
      RunOnLockResult(lockAcquired = true, exception = e)
    }
  }

}

/**
 * Implementation of {@code RunOnLockAcquired}. Doesn't try to obtain any lock, executes action right away
 */
class NoOpRunOnLockAcquired : RunOnLockAcquired {

  private val log = LoggerFactory.getLogger(javaClass)
  override fun execute(action: Runnable, keyName: String): RunOnLockResult<Void> {
    return try {
      log.debug("Executing action with no locking for key: {}", keyName)
      action.run()
      log.debug("Execution with no locking for key: {} successful", keyName)
      RunOnLockResult(lockAcquired = true, actionExecuted = true)
    } catch (e: Exception) {
      log.error("An exception was thrown while executing action with no locking for key: {}", keyName)
      log.error(e.message)
      RunOnLockResult(exception = e)
    }
  }

  override fun <R> execute(action: Callable<R>, keyName: String): RunOnLockResult<R?> {
    return try {
      RunOnLockResult(lockAcquired = true, actionExecuted = true, result = action.call())
    } catch (e: Exception) {
      RunOnLockResult(exception = e)
    }
  }

}
