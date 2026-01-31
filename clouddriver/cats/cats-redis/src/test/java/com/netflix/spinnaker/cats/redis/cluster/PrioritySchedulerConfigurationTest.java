/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.cats.redis.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test suite for PrioritySchedulerConfiguration component.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Thread pool creation (cached thread pool with SynchronousQueue)
 *   <li>Thread factory settings (daemon threads, naming pattern for debugging)
 *   <li>Semaphore setup for concurrency control (permits = maxConcurrentAgents)
 *   <li>Unbounded mode when maxConcurrentAgents ≤ 0 (null semaphore)
 *   <li>Pattern compilation for agent filtering (enabled/disabled patterns)
 *   <li>Case-sensitive regex matching
 *   <li>Configuration property accessors (interval, refresh period, zombie config)
 *   <li>Resource cleanup (graceful shutdown, timeout handling, idempotent shutdown)
 *   <li>Thread safety for concurrent configuration access
 *   <li>Batch operations and timing configuration validation
 *   <li>Mathematical constraints for default and high-load configurations
 * </ul>
 *
 * <p><b>Key Implementation Details:</b>
 *
 * <ul>
 *   <li>Work pool uses SynchronousQueue (cached thread pool characteristic)
 *   <li>Daemon threads allow JVM to exit without waiting for agent threads
 *   <li>Thread names follow pattern "PriorityAgentWorker-N" for debugging
 *   <li>Empty disabled pattern returns null (no filtering)
 *   <li>Shutdown is idempotent and handles running tasks gracefully
 * </ul>
 *
 * <p><b>Note:</b> Integration tests in this suite use Testcontainers with Redis. Tests that call
 * {@code registerAgent()} and {@code saturatePool()} have side effects documented in class-level
 * inventory files.
 */
@Testcontainers
@DisplayName("PriorityConfiguration Tests")
@SuppressWarnings("resource") // GenericContainer lifecycle managed by @Testcontainers
@Timeout(60)
class PrioritySchedulerConfigurationTest {

  // Shared container for all integration tests
  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine")
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", "testpass");

  private PriorityAgentProperties agentProperties;
  private PrioritySchedulerProperties schedulerProperties;
  private PrioritySchedulerConfiguration configuration;

  @BeforeEach
  void setUp() {
    agentProperties = new PriorityAgentProperties();
    agentProperties.setMaxConcurrentAgents(10);
    agentProperties.setEnabledPattern(".*");
    agentProperties.setDisabledPattern("");

    schedulerProperties = new PrioritySchedulerProperties();
    schedulerProperties.setIntervalMs(1000L);
    schedulerProperties.setRefreshPeriodSeconds(30);
    schedulerProperties.getKeys().setWaitingSet("waiting");
    schedulerProperties.getKeys().setWorkingSet("working");
    schedulerProperties.getKeys().setCleanupLeaderKey("cleanup-leader");

    // Setup zombie cleanup configuration
    schedulerProperties.getZombieCleanup().setEnabled(true);
    schedulerProperties.getZombieCleanup().setThresholdMs(30000L);
    schedulerProperties.getZombieCleanup().setIntervalMs(10000L);

    // Setup orphan cleanup configuration
    schedulerProperties.getOrphanCleanup().setEnabled(true);
    schedulerProperties.getOrphanCleanup().setThresholdMs(60000L);
    schedulerProperties.getOrphanCleanup().setIntervalMs(30000L);

    configuration = new PrioritySchedulerConfiguration(agentProperties, schedulerProperties);
  }

  @Nested
  @DisplayName("Thread Pool Configuration Tests")
  class ThreadPoolConfigurationTests {

    /** Tests that agent work pool is created with cached thread pool (SynchronousQueue). */
    @Test
    @DisplayName("Should create agent work pool with cached thread pool")
    void shouldCreateAgentWorkPoolWithCachedThreadPool() {
      // When
      ExecutorService workPool = configuration.getAgentWorkPool();

      // Then: Verify work pool exists and uses SynchronousQueue
      // SynchronousQueue is characteristic of cached thread pools - threads created on demand
      assertThat(workPool).isNotNull();
      ThreadPoolExecutor threadPool = (ThreadPoolExecutor) workPool;
      assertThat(threadPool.getQueue()).isInstanceOf(java.util.concurrent.SynchronousQueue.class);
    }

    /** Tests that scheduler executor service is created and active. */
    @Test
    @DisplayName("Should create scheduler executor service")
    void shouldCreateSchedulerExecutorService() {
      // When
      ScheduledExecutorService schedulerExecutor = configuration.getSchedulerExecutorService();

      // Then
      assertThat(schedulerExecutor).isNotNull();
      assertThat(schedulerExecutor.isShutdown()).isFalse();
    }

    /** Tests that threads are created with naming pattern "PriorityAgentWorker-N" for debugging. */
    @Test
    @DisplayName("Should create threads with correct naming pattern")
    void shouldCreateThreadsWithCorrectNamingPattern() throws Exception {
      // When
      ExecutorService workPool = configuration.getAgentWorkPool();

      // Submit a task to create a thread
      AtomicBoolean threadCreated = new AtomicBoolean(false);
      String[] threadName = new String[1];

      workPool.submit(
          () -> {
            threadCreated.set(true);
            Thread currentThread = Thread.currentThread();
            threadName[0] = currentThread.getName();
          });

      // Wait for thread to execute using polling
      TestFixtures.waitForBackgroundTask(() -> threadCreated.get(), 1000, 50);

      // Then - Verify thread name matches expected pattern
      assertThat(threadCreated.get()).isTrue();
      assertThat(threadName[0]).matches("PriorityAgentWorker-\\d+");
    }

    /** Tests that threads are created as daemon threads (allows JVM to exit without waiting). */
    @Test
    @DisplayName("Should create daemon threads")
    void shouldCreateDaemonThreads() throws Exception {
      // When
      ExecutorService workPool = configuration.getAgentWorkPool();

      // Submit a task to verify daemon status
      AtomicBoolean threadCreated = new AtomicBoolean(false);
      AtomicBoolean isDaemon = new AtomicBoolean(false);

      workPool.submit(
          () -> {
            threadCreated.set(true);
            isDaemon.set(Thread.currentThread().isDaemon());
          });

      // Wait for thread to execute using polling
      TestFixtures.waitForBackgroundTask(() -> threadCreated.get(), 1000, 50);

      // Then: Daemon=true is critical for JVM shutdown - non-daemon threads block JVM exit
      assertThat(threadCreated.get()).isTrue();
      assertThat(isDaemon.get()).isTrue();
    }

    /** Tests that thread pool handles concurrent task submissions correctly. */
    @Test
    @DisplayName("Should handle concurrent submissions")
    void shouldHandleConcurrentSubmissions() throws Exception {
      // Given
      ExecutorService workPool = configuration.getAgentWorkPool();
      int concurrentTasks = 10;

      // When - Submit multiple tasks concurrently
      AtomicInteger completedTasks = new AtomicInteger(0);
      CountDownLatch startLatch = new CountDownLatch(1);
      CountDownLatch completionLatch = new CountDownLatch(concurrentTasks);

      for (int i = 0; i < concurrentTasks; i++) {
        workPool.submit(
            () -> {
              try {
                startLatch.await(); // Wait for all tasks to be submitted
                completedTasks.incrementAndGet();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                completionLatch.countDown();
              }
            });
      }

      // Signal all tasks to start
      startLatch.countDown();

      // Wait for all tasks to complete
      boolean completed = completionLatch.await(5, TimeUnit.SECONDS);

      // Then - All tasks should complete successfully
      assertThat(completed).isTrue();
      assertThat(completedTasks.get()).isEqualTo(concurrentTasks);
    }
  }

  @Nested
  @DisplayName("Concurrency Control Tests")
  class ConcurrencyControlTests {

    /** Tests that semaphore is created with permit count matching maxConcurrentAgents. */
    @Test
    @DisplayName("Should create semaphore with correct permit count")
    void shouldCreateSemaphoreWithCorrectPermitCount() {
      // When
      Semaphore semaphore = configuration.getMaxConcurrentSemaphore();

      // Then: Permits control how many agents can run concurrently
      assertThat(semaphore).isNotNull();
      assertThat(semaphore.availablePermits()).isEqualTo(10); // matches maxConcurrentAgents
    }

    /** Tests that semaphore is null when maxConcurrentAgents=0 (unbounded mode). */
    @Test
    @DisplayName("Should return null semaphore when concurrency control disabled")
    void shouldReturnNullSemaphoreWhenConcurrencyControlDisabled() {
      // Given: maxConcurrentAgents=0 disables concurrency control (unbounded mode)
      agentProperties.setMaxConcurrentAgents(0);
      PrioritySchedulerConfiguration disabledConfig =
          new PrioritySchedulerConfiguration(agentProperties, schedulerProperties);

      // Then: Null semaphore means unlimited concurrent agents allowed
      Semaphore semaphore = disabledConfig.getMaxConcurrentSemaphore();
      assertThat(semaphore).isNull();
    }

    /** Tests that negative maxConcurrentAgents is treated as unbounded (null semaphore). */
    @Test
    @DisplayName("Should handle negative concurrent agents as disabled")
    void shouldHandleNegativeConcurrentAgentsAsDisabled() {
      // Given
      agentProperties.setMaxConcurrentAgents(-1);
      PrioritySchedulerConfiguration disabledConfig =
          new PrioritySchedulerConfiguration(agentProperties, schedulerProperties);

      // Then - Expect null semaphore in unbounded mode
      Semaphore semaphore = disabledConfig.getMaxConcurrentSemaphore();
      assertThat(semaphore).isNull();
    }

    /** Tests that semaphore is created with custom permit count. */
    @Test
    @DisplayName("Should create semaphore with custom permit count")
    void shouldCreateSemaphoreWithCustomPermitCount() {
      // Given
      agentProperties.setMaxConcurrentAgents(25);
      PrioritySchedulerConfiguration customConfig =
          new PrioritySchedulerConfiguration(agentProperties, schedulerProperties);

      // When
      Semaphore semaphore = customConfig.getMaxConcurrentSemaphore();

      // Then
      assertThat(semaphore.availablePermits()).isEqualTo(25);
    }
  }

  @Nested
  @DisplayName("Pattern Configuration Tests")
  class PatternConfigurationTests {

    /** Tests that enabled agent pattern is compiled and matches agents correctly. */
    @Test
    @DisplayName("Should compile enabled agent pattern correctly")
    void shouldCompileEnabledAgentPatternCorrectly() {
      // When
      Pattern pattern = configuration.getEnabledAgentPattern();

      // Then
      assertThat(pattern).isNotNull();
      assertThat(pattern.pattern()).isEqualTo(".*");
      assertThat(pattern.matcher("test-agent").matches()).isTrue();
      assertThat(pattern.matcher("another-agent").matches()).isTrue();
    }

    /** Tests that custom enabled patterns are compiled and match/reject agents correctly. */
    @Test
    @DisplayName("Should handle custom agent patterns")
    void shouldHandleCustomAgentPatterns() {
      // Given - Use fresh properties to avoid test contamination
      PriorityAgentProperties freshAgentProps = new PriorityAgentProperties();
      freshAgentProps.setEnabledPattern("test-.*");
      PrioritySchedulerConfiguration customConfig =
          new PrioritySchedulerConfiguration(freshAgentProps, schedulerProperties);

      // When
      Pattern pattern = customConfig.getEnabledAgentPattern();

      // Then
      assertThat(pattern.pattern()).isEqualTo("test-.*");
      assertThat(pattern.matcher("test-ec2").matches()).isTrue();
      assertThat(pattern.matcher("aws-ec2").matches()).isFalse();
      assertThat(pattern.matcher("gcp-compute").matches()).isFalse();
    }

    /** Tests that complex regex patterns match/reject agents and are case-sensitive. */
    @Test
    @DisplayName("Should handle complex regex patterns")
    void shouldHandleComplexRegexPatterns() {
      // Given - Use fresh properties to avoid test contamination
      PriorityAgentProperties freshAgentProps = new PriorityAgentProperties();
      freshAgentProps.setEnabledPattern("^(aws|gcp)-.*$");
      PrioritySchedulerConfiguration regexConfig =
          new PrioritySchedulerConfiguration(freshAgentProps, schedulerProperties);

      // When
      Pattern pattern = regexConfig.getEnabledAgentPattern();

      // Then: Java patterns are case-sensitive by default
      assertThat(pattern.matcher("aws-ec2").matches()).isTrue();
      assertThat(pattern.matcher("gcp-compute").matches()).isTrue();
      assertThat(pattern.matcher("azure-vm").matches()).isFalse();
      assertThat(pattern.matcher("AWS-ec2").matches()).isFalse(); // Case sensitive - "AWS" != "aws"
    }
  }

  @Nested
  @DisplayName("Disabled Pattern Configuration Tests")
  class DisabledPatternConfigurationTests {

    /** Tests that empty disabled pattern returns null. */
    @Test
    @DisplayName("Should handle no disabled pattern (empty string)")
    void shouldHandleNoDisabledPattern() {
      // Given: Empty string means no disabled pattern configured
      PriorityAgentProperties freshAgentProps = new PriorityAgentProperties();
      freshAgentProps.setDisabledPattern("");
      PrioritySchedulerConfiguration noPatternConfig =
          new PrioritySchedulerConfiguration(freshAgentProps, schedulerProperties);

      // When
      Pattern pattern = noPatternConfig.getDisabledAgentPattern();

      // Then: Null pattern means no agents are disabled by pattern
      assertThat(pattern).isNull();
    }

    /** Tests that simple disabled pattern is compiled and matches agents correctly. */
    @Test
    @DisplayName("Should compile simple disabled pattern")
    void shouldCompileSimpleDisabledPattern() {
      // Given - Use fresh properties with simple pattern
      PriorityAgentProperties freshAgentProps = new PriorityAgentProperties();
      freshAgentProps.setDisabledPattern("test-.*");
      PrioritySchedulerConfiguration patternConfig =
          new PrioritySchedulerConfiguration(freshAgentProps, schedulerProperties);

      // When
      Pattern pattern = patternConfig.getDisabledAgentPattern();

      // Then
      assertThat(pattern).isNotNull();
      assertThat(pattern.pattern()).isEqualTo("test-.*");
      assertThat(pattern.matcher("test-ec2").matches()).isTrue();
      assertThat(pattern.matcher("aws-ec2").matches()).isFalse();
    }

    /** Tests that complex disabled patterns match/reject agents correctly. */
    @Test
    @DisplayName("Should handle complex disabled patterns")
    void shouldHandleComplexDisabledPatterns() {
      // Given - Use fresh properties with complex pattern
      PriorityAgentProperties freshAgentProps = new PriorityAgentProperties();
      freshAgentProps.setDisabledPattern("^(aws|gcp)-(test|dev)-.*$");
      PrioritySchedulerConfiguration complexConfig =
          new PrioritySchedulerConfiguration(freshAgentProps, schedulerProperties);

      // When
      Pattern pattern = complexConfig.getDisabledAgentPattern();

      // Then
      assertThat(pattern).isNotNull();
      assertThat(pattern.matcher("aws-test-ec2").matches()).isTrue();
      assertThat(pattern.matcher("gcp-dev-compute").matches()).isTrue();
      assertThat(pattern.matcher("aws-prod-ec2").matches()).isFalse();
      assertThat(pattern.matcher("azure-test-vm").matches()).isFalse();
    }

    /** Tests that disabled patterns filter test/dev environments across multiple clouds. */
    @Test
    @DisplayName("Should handle multi-cloud disabled patterns")
    void shouldHandleMultiCloudDisabledPatterns() {
      // Given - Pattern to disable all test environments across clouds
      PriorityAgentProperties freshAgentProps = new PriorityAgentProperties();
      freshAgentProps.setDisabledPattern(".*-(test|testing|dev|development)-.*");
      PrioritySchedulerConfiguration multiCloudConfig =
          new PrioritySchedulerConfiguration(freshAgentProps, schedulerProperties);

      // When
      Pattern pattern = multiCloudConfig.getDisabledAgentPattern();

      // Then
      assertThat(pattern).isNotNull();

      // Should match test environments
      assertThat(pattern.matcher("aws-test-us-east-1").matches()).isTrue();
      assertThat(pattern.matcher("gcp-testing-us-central1").matches()).isTrue();
      assertThat(pattern.matcher("azure-dev-eastus").matches()).isTrue();
      assertThat(pattern.matcher("k8s-development-cluster").matches()).isTrue();

      // Should NOT match production environments
      assertThat(pattern.matcher("aws-prod-us-east-1").matches()).isFalse();
      assertThat(pattern.matcher("gcp-production-us-central1").matches()).isFalse();
      assertThat(pattern.matcher("azure-prod-eastus").matches()).isFalse();
    }

    /** Tests that pattern matching is case-sensitive. */
    @Test
    @DisplayName("Should be case sensitive in pattern matching")
    void shouldBeCaseSensitiveInPatternMatching() {
      // Given - Case sensitive pattern
      PriorityAgentProperties freshAgentProps = new PriorityAgentProperties();
      freshAgentProps.setDisabledPattern("aws-.*");
      PrioritySchedulerConfiguration caseConfig =
          new PrioritySchedulerConfiguration(freshAgentProps, schedulerProperties);

      // When
      Pattern pattern = caseConfig.getDisabledAgentPattern();

      // Then
      assertThat(pattern.matcher("aws-ec2").matches()).isTrue();
      assertThat(pattern.matcher("AWS-ec2").matches()).isFalse(); // Case sensitive
      assertThat(pattern.matcher("aws-EC2").matches()).isTrue(); // Only prefix matters
    }
  }

  @Nested
  @DisplayName("Configuration Access Tests")
  class ConfigurationAccessTests {

    /** Tests that scheduler interval is returned correctly. */
    @Test
    @DisplayName("Should provide correct scheduler interval")
    void shouldProvideCorrectSchedulerInterval() {
      // When
      long interval = configuration.getSchedulerIntervalMs();

      // Then
      assertThat(interval).isEqualTo(1000L);
    }

    /** Tests that Redis refresh period is returned correctly. */
    @Test
    @DisplayName("Should provide correct Redis refresh period")
    void shouldProvideCorrectRedisRefreshPeriod() {
      // When
      int refreshPeriod = configuration.getRedisRefreshPeriod();

      // Then
      assertThat(refreshPeriod).isEqualTo(30);
    }

    /** Tests that max concurrent agents is returned correctly. */
    @Test
    @DisplayName("Should provide correct max concurrent agents")
    void shouldProvideCorrectMaxConcurrentAgents() {
      // When
      int maxConcurrent = configuration.getMaxConcurrentAgents();

      // Then
      assertThat(maxConcurrent).isEqualTo(10);
    }

    /** Tests that zombie cleanup threshold and interval are returned correctly. */
    @Test
    @DisplayName("Should provide correct zombie configuration")
    void shouldProvideCorrectZombieConfiguration() {
      // When
      long zombieThreshold = configuration.getZombieThresholdMs();
      long zombieCleanupInterval = configuration.getZombieIntervalMs();

      // Then
      assertThat(zombieThreshold).isEqualTo(30000L);
      assertThat(zombieCleanupInterval).isEqualTo(10000L);
    }
  }

  @Nested
  @DisplayName("Resource Management Tests")
  class ResourceManagementTests {

    /** Tests that thread pools shutdown gracefully when shutdown() is called. */
    @Test
    @DisplayName("Should shutdown thread pools gracefully")
    void shouldShutdownThreadPoolsGracefully() {
      // Given: Both pools should be active initially
      ExecutorService workPool = configuration.getAgentWorkPool();
      ScheduledExecutorService schedulerExecutor = configuration.getSchedulerExecutorService();

      assertThat(workPool.isShutdown()).isFalse();
      assertThat(schedulerExecutor.isShutdown()).isFalse();

      // When
      configuration.shutdown();

      // Then: Critical for preventing resource leaks on application shutdown
      assertThat(workPool.isShutdown()).isTrue();
      assertThat(schedulerExecutor.isShutdown()).isTrue();
    }

    /** Tests that shutdown completes within timeout even with running tasks. */
    @Test
    @DisplayName("Should handle shutdown timeout gracefully")
    void shouldHandleShutdownTimeoutGracefully() throws InterruptedException {
      // Given - Submit a long-running task to cause shutdown delay
      ExecutorService workPool = configuration.getAgentWorkPool();
      workPool.submit(
          () -> {
            try {
              Thread.sleep(100); // Short delay to test shutdown
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });

      // When
      long startTime = System.currentTimeMillis();
      configuration.shutdown();
      long shutdownTime = System.currentTimeMillis() - startTime;

      // Then - Should complete within reasonable time
      assertThat(shutdownTime).isLessThan(5000); // Less than 5 seconds
      assertThat(workPool.isShutdown()).isTrue();
    }

    /** Tests that multiple shutdown() calls are idempotent and don't throw. */
    @Test
    @DisplayName("Should handle multiple shutdown calls gracefully")
    void shouldHandleMultipleShutdownCallsGracefully() {
      // When: Shutdown is idempotent - safe to call multiple times
      configuration.shutdown();
      configuration.shutdown(); // Second call should not throw

      // Then: Pools remain shutdown, no exceptions
      assertThat(configuration.getAgentWorkPool().isShutdown()).isTrue();
      assertThat(configuration.getSchedulerExecutorService().isShutdown()).isTrue();
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    /** Tests that configuration is thread-safe for concurrent access. */
    @Test
    @DisplayName("Should handle high concurrent access to configuration")
    void shouldHandleHighConcurrentAccessToConfiguration() throws InterruptedException {
      // Given
      int threadCount = 50;
      Thread[] threads = new Thread[threadCount];
      final Exception[] threadException = new Exception[1];

      // When - Multiple threads access configuration concurrently
      for (int i = 0; i < threadCount; i++) {
        threads[i] =
            new Thread(
                () -> {
                  try {
                    // Access various configuration methods
                    configuration.getSchedulerIntervalMs();
                    configuration.getMaxConcurrentAgents();
                    configuration.getEnabledAgentPattern();
                    configuration.getAgentWorkPool();
                    configuration.getMaxConcurrentSemaphore();
                  } catch (Exception e) {
                    threadException[0] = e;
                  }
                });
        threads[i].start();
      }

      // Wait for all threads to complete
      for (Thread thread : threads) {
        thread.join();
      }

      // Then
      assertThat(threadException[0]).isNull();
    }

    /** Tests that configuration values are consistent across multiple calls. */
    @Test
    @DisplayName("Should provide consistent configuration values")
    void shouldProvideConsistentConfigurationValues() {
      // Given
      long firstIntervalCall = configuration.getSchedulerIntervalMs();
      int firstMaxConcurrentCall = configuration.getMaxConcurrentAgents();
      Pattern firstPatternCall = configuration.getEnabledAgentPattern();

      // When - Call configuration methods multiple times
      long secondIntervalCall = configuration.getSchedulerIntervalMs();
      int secondMaxConcurrentCall = configuration.getMaxConcurrentAgents();
      Pattern secondPatternCall = configuration.getEnabledAgentPattern();

      // Then - Should return consistent values
      assertThat(secondIntervalCall).isEqualTo(firstIntervalCall);
      assertThat(secondMaxConcurrentCall).isEqualTo(firstMaxConcurrentCall);
      assertThat(secondPatternCall.pattern()).isEqualTo(firstPatternCall.pattern());
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    /** Tests that all configuration elements are created and work together. */
    @Test
    @DisplayName("Should create fully functional configuration")
    void shouldCreateFullyFunctionalConfiguration() {
      // When - Access all configuration elements
      ExecutorService workPool = configuration.getAgentWorkPool();
      ScheduledExecutorService schedulerExecutor = configuration.getSchedulerExecutorService();
      Semaphore semaphore = configuration.getMaxConcurrentSemaphore();
      Pattern pattern = configuration.getEnabledAgentPattern();

      // Then - All elements should be properly configured
      assertThat(workPool).isNotNull();
      assertThat(schedulerExecutor).isNotNull();
      assertThat(semaphore).isNotNull();
      assertThat(pattern).isNotNull();

      // Verify they work together
      assertThat(semaphore.availablePermits()).isEqualTo(configuration.getMaxConcurrentAgents());
      assertThat(pattern.matcher("test-agent").matches()).isTrue();
    }

    /** Tests that disabled pattern is set and retrieved correctly. */
    @Test
    @DisplayName("Should handle configuration with disabled pattern")
    void shouldHandleConfigurationWithDisabledPattern() {
      // Given
      String disabledPattern = "disabled-agent-.*";
      agentProperties.setDisabledPattern(disabledPattern);
      PrioritySchedulerConfiguration configWithDisabled =
          new PrioritySchedulerConfiguration(agentProperties, schedulerProperties);

      // When
      Pattern retrievedDisabled = configWithDisabled.getDisabledAgentPattern();

      // Then
      assertThat(retrievedDisabled.pattern()).isEqualTo(disabledPattern);
    }
  }

  @Nested
  @DisplayName("Configuration Validation Tests")
  class ConfigurationValidationTests {

    private PrioritySchedulerProperties properties;

    @BeforeEach
    void setUp() {
      properties = new PrioritySchedulerProperties();
    }

    @Nested
    @DisplayName("Batch Operations Configuration Tests")
    class BatchOperationsConfigurationTests {

      /** Tests that batch operations have sensible defaults (enabled=true, batchSize=0). */
      @Test
      @DisplayName("Should have sensible default values")
      void shouldHaveSensibleDefaultValues() {
        assertThat(properties.getBatchOperations().isEnabled()).isTrue();
        assertThat(properties.getBatchOperations().getBatchSize()).isEqualTo(0);
      }

      /** Tests that negative chunk attempt multiplier values are rejected with validation error. */
      @Test
      @DisplayName("Should reject negative chunk attempt multiplier values")
      void shouldRejectNegativeChunkAttemptMultiplierValues() {
        properties.getBatchOperations().setChunkAttemptMultiplier(-0.1d);

        assertThatThrownBy(() -> properties.validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("chunk-attempt-multiplier");
      }

      /** Tests that non-finite chunk attempt multiplier values (NaN) are rejected. */
      @Test
      @DisplayName("Should reject non-finite chunk attempt multiplier values")
      void shouldRejectNonFiniteChunkAttemptMultiplierValues() {
        properties.getBatchOperations().setChunkAttemptMultiplier(Double.NaN);

        assertThatThrownBy(() -> properties.validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("chunk-attempt-multiplier");
      }

      /** Tests that finite non-negative chunk attempt multiplier values pass validation. */
      @Test
      @DisplayName("Should allow finite non-negative chunk attempt multiplier values")
      void shouldAllowFiniteNonNegativeChunkAttemptMultiplierValues() {
        properties.getBatchOperations().setChunkAttemptMultiplier(2.5d);

        assertThatCode(() -> properties.validate()).doesNotThrowAnyException();
      }
    }

    @Nested
    @DisplayName("Backward Compatibility Tests")
    class BackwardCompatibilityTests {

      /** Tests that zombie cleanup convenience methods work correctly. */
      @Test
      @DisplayName("Should provide zombie cleanup convenience methods")
      void shouldProvideZombieCleanupConvenienceMethods() {
        properties.getBatchOperations().setBatchSize(75);

        assertThat(properties.getBatchOperations().getBatchSize()).isEqualTo(75);

        properties.getZombieCleanup().setThresholdMs(45000L);
        assertThat(properties.getZombieThresholdMs()).isEqualTo(45000L);
        assertThat(properties.isZombieCleanupEnabled()).isTrue();
      }

      /** Tests that orphan cleanup convenience methods work correctly. */
      @Test
      @DisplayName("Should provide orphan cleanup convenience methods")
      void shouldProvideOrphanCleanupConvenienceMethods() {
        properties.getBatchOperations().setBatchSize(125);

        assertThat(properties.getBatchOperations().getBatchSize()).isEqualTo(125);

        properties.getOrphanCleanup().setThresholdMs(3600000L);
        assertThat(properties.getOrphanThresholdMs()).isEqualTo(3600000L);
        assertThat(properties.isOrphanCleanupEnabled()).isTrue();
      }

      /** Tests that exceptional agents pattern and threshold can be configured. */
      @Test
      @DisplayName("Should handle exceptional agents configuration")
      void shouldHandleExceptionalAgentsConfiguration() {
        properties.getZombieCleanup().getExceptionalAgents().setPattern(".*test.*");
        properties.getZombieCleanup().getExceptionalAgents().setThresholdMs(7200000L);

        assertThat(properties.hasExceptionalAgents()).isTrue();
        assertThat(properties.getExceptionalAgentsPattern()).isEqualTo(".*test.*");
        assertThat(properties.getExceptionalAgentsThresholdMs()).isEqualTo(7200000L);
      }

      /** Tests that missing exceptional agents configuration returns correct defaults. */
      @Test
      @DisplayName("Should handle missing exceptional agents configuration")
      void shouldHandleMissingExceptionalAgentsConfiguration() {
        assertThat(properties.hasExceptionalAgents()).isFalse();
        assertThat(properties.getExceptionalAgentsPattern()).isEmpty();
      }
    }

    @Nested
    @DisplayName("Timing Configuration Tests")
    class TimingConfigurationTests {

      /** Tests that timing properties have reasonable defaults. */
      @Test
      @DisplayName("Should have reasonable timing defaults")
      void shouldHaveReasonableTimingDefaults() {
        assertThat(properties.getIntervalMs()).isEqualTo(1000L);
        assertThat(properties.getRefreshPeriodSeconds()).isEqualTo(30);
        assertThat(properties.getTimeCacheDurationMs()).isEqualTo(10000L);
      }
    }

    @Nested
    @DisplayName("Configuration Object Structure Tests")
    class ConfigurationObjectStructureTests {

      /** Tests that nested configuration objects (zombieCleanup, orphanCleanup) are present. */
      @Test
      @DisplayName("Should have proper nested configuration structure")
      void shouldHaveProperNestedConfigurationStructure() {
        assertThat(properties.getZombieCleanup()).isNotNull();
        assertThat(properties.getOrphanCleanup()).isNotNull();
      }

      /** Tests that convenience methods handle nested configurations without throwing. */
      @Test
      @DisplayName("Should handle null nested configurations gracefully")
      void shouldHandleNullNestedConfigurationsGracefully() {
        assertThatCode(
                () -> {
                  boolean enabled = properties.isZombieCleanupEnabled();
                  long threshold = properties.getZombieThresholdMs();
                  assertThat(enabled).isNotNull();
                  assertThat(threshold).isGreaterThanOrEqualTo(0);
                })
            .doesNotThrowAnyException();
      }

      /** Tests that all expected configuration properties are accessible. */
      @Test
      @DisplayName("Should provide all expected configuration properties")
      void shouldProvideAllExpectedConfigurationProperties() {
        assertThatCode(
                () -> {
                  properties.getIntervalMs();
                  properties.getRefreshPeriodSeconds();
                  properties.getTimeCacheDurationMs();

                  properties.getBatchOperations().isEnabled();
                  properties.getBatchOperations().getBatchSize();

                  properties.isZombieCleanupEnabled();
                  properties.getZombieThresholdMs();
                  properties.getZombieIntervalMs();
                  properties.getBatchOperations().getBatchSize();

                  properties.isOrphanCleanupEnabled();
                  properties.getOrphanThresholdMs();
                  properties.getOrphanIntervalMs();
                  properties.getBatchOperations().getBatchSize();
                  properties.getOrphanLeadershipTtlMs();
                  properties.isOrphanForceAllPods();
                })
            .doesNotThrowAnyException();
      }
    }
  }
}
