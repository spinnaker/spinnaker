package com.netflix.spinnaker.kork.lock

import java.time.Clock
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import spock.lang.Specification
import static com.netflix.spinnaker.kork.lock.LockManager.*
import static com.netflix.spinnaker.kork.lock.LockManager.LockStatus.ACQUIRED
import static com.netflix.spinnaker.kork.lock.LockManager.LockStatus.TAKEN
import static com.netflix.spinnaker.kork.lock.RefreshableLockManager.HeartbeatLockRequest
import static com.netflix.spinnaker.kork.lock.RefreshableLockManager.LockHeartbeatStatus

abstract class BaseLockManagerSpec<T extends LockManager> extends Specification {

  def clock = Clock.systemDefaultZone()
  def testLockMaxDurationMillis = 1000L

  protected abstract T subject()

  T lockManager

  def setup() {
    lockManager = subject()
  }

  def "should acquire a simple lock and auto release"() {
    when:
    def result = lockManager.acquireLock("veryImportantLock", testLockMaxDurationMillis, {
      return "Run after lock is safely acquired."
    } as Callable<String>)

    then:
    result.lockStatus == ACQUIRED
    result.released == true
    result.onLockAcquiredCallbackResult == "Run after lock is safely acquired."
  }

  def "should store arbitrary data as attributes alongside the lock"() {
    given:
    def lockOptions = new LockOptions()
      .withMaximumLockDuration(Duration.ofMillis(testLockMaxDurationMillis))
      .withLockName("veryImportantLock")
      .withAttributes(["key:value", "key2:value2"])

    when:
    def result = lockManager.acquireLock(lockOptions, {
      return "Lock with data in attributes"
    } as Callable<String>)

    then:
    result.lock.attributes == "key:value;key2:value2"
  }

  def "should fail to acquire an already taken lock"() {
    given:
    def lockOptions = new LockOptions()
      .withMaximumLockDuration(Duration.ofMillis(testLockMaxDurationMillis))
      .withLockName("veryImportantLock")

    and:
    lockManager.tryCreateLock(lockOptions)

    when:
    def result = lockManager.acquireLock(lockOptions, {
      return "attempting to acquire lock"
    } as Callable<String>)

    then:
    result.lockStatus == TAKEN
    result.onLockAcquiredCallbackResult == null
  }

  def "should acquire with heartbeat"() {
    given:
    def lockOptions = new LockOptions()
      .withMaximumLockDuration(Duration.ofMillis(testLockMaxDurationMillis))
      .withLockName("veryImportantLock")

    when:
    def result = lockManager.acquireLock(lockOptions, {
      // simulates long running task
      Thread.sleep(100)
      "Done"
    } as Callable<String>)

    then:
    result.released == true
    result.lockStatus == ACQUIRED
    result.onLockAcquiredCallbackResult == "Done"

    when:
    result = lockManager.acquireLock(lockOptions.withLockName("withRunnable"), {
      // simulates long running task
      Thread.sleep(100)
    } as Runnable)

    then:
    result.onLockAcquiredCallbackResult == null
    result.lockStatus == ACQUIRED
  }

  def "should propagate exception on callback failure"() {
    given:
    def lockName = "veryImportantLock"
    def onLockAcquiredCallback = {
      throw new IllegalStateException("Failure")
    }

    when:
    lockManager.acquireLock(lockName, testLockMaxDurationMillis, onLockAcquiredCallback)

    then:
    thrown(LockCallbackException)
  }

  def "should release a lock"() {
    given:
    def lockOptions = new LockOptions()
      .withMaximumLockDuration(Duration.ofMillis(testLockMaxDurationMillis))
      .withLockName("veryImportantLock")

    and:
    def lock = lockManager.tryCreateLock(lockOptions)

    expect:
    ensureLockExists(lockOptions.lockName)

    when:
    def response = lockManager.tryReleaseLock(lock, true)

    then:
    response == LockReleaseStatus.SUCCESS.toString()

    ensureLockReleased(lockOptions.lockName)
  }

  def "should heartbeat by updating lock ttl"() {
    given:
    def heartbeatRetriesOnFailure = new AtomicInteger(1)
    def lockOptions = new LockOptions()
      .withMaximumLockDuration(Duration.ofMillis(testLockMaxDurationMillis))
      .withLockName("veryImportantLock")

    and:
    def lock = lockManager.tryCreateLock(lockOptions)
    def request = new HeartbeatLockRequest(lock, heartbeatRetriesOnFailure, clock, Duration.ofMillis(200), false)
    Thread.sleep(10)

    when:
    def response = lockManager.heartbeat(request)
    Thread.sleep(10)

    then:
    response.lockStatus == LockHeartbeatStatus.SUCCESS

    when: "Late heartbeat resulting in expired lock "
    request = new HeartbeatLockRequest(lock, heartbeatRetriesOnFailure, clock, Duration.ofMillis(200), false)
    lockManager.heartbeat(request)
    Thread.sleep(30)
    response = lockManager.heartbeat(request)

    then:
    response.lockStatus == LockHeartbeatStatus.EXPIRED
  }

  def "should support success and failure intervals when releasing a lock"() {
    given:
    def lockOptions = new LockOptions()
      .withMaximumLockDuration(Duration.ofMillis(testLockMaxDurationMillis))
      .withLockName("veryImportantLock")
      .withSuccessInterval(Duration.ofSeconds(100))
      .withFailureInterval(Duration.ofSeconds(25))

    and:
    def lock = lockManager.tryCreateLock(lockOptions)

    when:
    def response = lockManager.tryReleaseLock(lock, true)

    then:
    response == LockReleaseStatus.SUCCESS.toString()

    ensureLockTtlGreaterThan(lockOptions.lockName, 25)

    when:
    response = lockManager.tryReleaseLock(lock, false)

    then:
    response == LockReleaseStatus.SUCCESS.toString()

    ensureLockTtlLessThanOrEqualTo(lockOptions.lockName, 25)
  }

  protected void ensureLockExists(String lockName) {}

  protected void ensureLockReleased(String lockName) {}

  protected void ensureLockTtlGreaterThan(String lockName, int ttlSeconds) {}

  protected void ensureLockTtlLessThanOrEqualTo(String lockName, int ttlSeconds) {}
}
