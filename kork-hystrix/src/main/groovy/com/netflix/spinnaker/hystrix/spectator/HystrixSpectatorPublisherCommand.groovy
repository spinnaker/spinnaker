/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.hystrix.spectator

import com.netflix.hystrix.HystrixCircuitBreaker
import com.netflix.hystrix.HystrixCommandGroupKey
import com.netflix.hystrix.HystrixCommandKey
import com.netflix.hystrix.HystrixCommandMetrics
import com.netflix.hystrix.HystrixCommandProperties
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand
import com.netflix.hystrix.util.HystrixRollingNumberEvent
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry

import java.util.function.ToDoubleFunction

class HystrixSpectatorPublisherCommand implements HystrixMetricsPublisherCommand {
  private final HystrixCommandKey key
  private final HystrixCommandGroupKey commandGroupKey
  private final HystrixCommandMetrics metrics
  private final HystrixCircuitBreaker circuitBreaker
  private final HystrixCommandProperties properties
  private final Registry metricRegistry
  private final String metricGroup
  private final String metricType

  public HystrixSpectatorPublisherCommand(HystrixCommandKey commandKey,
                                          HystrixCommandGroupKey commandGroupKey,
                                          HystrixCommandMetrics metrics,
                                          HystrixCircuitBreaker circuitBreaker,
                                          HystrixCommandProperties properties,
                                          Registry metricRegistry) {
    this.key = commandKey
    this.commandGroupKey = commandGroupKey
    this.metrics = metrics
    this.circuitBreaker = circuitBreaker
    this.properties = properties
    this.metricRegistry = metricRegistry
    this.metricGroup = commandGroupKey.name()
    this.metricType = commandKey.name()
  }

  @Override
  public void initialize() {
    metricRegistry.gauge(createMetricName("isCircuitBreakerOpen"), circuitBreaker, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return ((HystrixCircuitBreaker) ref).isOpen() ? 1 : 0
      }
    })

    metricRegistry.gauge(createMetricName("currentTime"), metrics, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return System.currentTimeMillis()
      }
    })

    // cumulative counts
    createCumulativeCountForEvent("countCollapsedRequests", HystrixRollingNumberEvent.COLLAPSED)
    createCumulativeCountForEvent("countExceptionsThrown", HystrixRollingNumberEvent.EXCEPTION_THROWN)
    createCumulativeCountForEvent("countFailure", HystrixRollingNumberEvent.FAILURE)
    createCumulativeCountForEvent("countFallbackFailure", HystrixRollingNumberEvent.FALLBACK_FAILURE)
    createCumulativeCountForEvent("countFallbackRejection", HystrixRollingNumberEvent.FALLBACK_REJECTION)
    createCumulativeCountForEvent("countFallbackSuccess", HystrixRollingNumberEvent.FALLBACK_SUCCESS)
    createCumulativeCountForEvent("countResponsesFromCache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE)
    createCumulativeCountForEvent("countSemaphoreRejected", HystrixRollingNumberEvent.SEMAPHORE_REJECTED)
    createCumulativeCountForEvent("countShortCircuited", HystrixRollingNumberEvent.SHORT_CIRCUITED)
    createCumulativeCountForEvent("countSuccess", HystrixRollingNumberEvent.SUCCESS)
    createCumulativeCountForEvent("countThreadPoolRejected", HystrixRollingNumberEvent.THREAD_POOL_REJECTED)
    createCumulativeCountForEvent("countTimeout", HystrixRollingNumberEvent.TIMEOUT)

    // rolling counts
    createRollingCountForEvent("rollingCountCollapsedRequests", HystrixRollingNumberEvent.COLLAPSED)
    createRollingCountForEvent("rollingCountExceptionsThrown", HystrixRollingNumberEvent.EXCEPTION_THROWN)
    createRollingCountForEvent("rollingCountFailure", HystrixRollingNumberEvent.FAILURE)
    createRollingCountForEvent("rollingCountFallbackFailure", HystrixRollingNumberEvent.FALLBACK_FAILURE)
    createRollingCountForEvent("rollingCountFallbackRejection", HystrixRollingNumberEvent.FALLBACK_REJECTION)
    createRollingCountForEvent("rollingCountFallbackSuccess", HystrixRollingNumberEvent.FALLBACK_SUCCESS)
    createRollingCountForEvent("rollingCountResponsesFromCache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE)
    createRollingCountForEvent("rollingCountSemaphoreRejected", HystrixRollingNumberEvent.SEMAPHORE_REJECTED)
    createRollingCountForEvent("rollingCountShortCircuited", HystrixRollingNumberEvent.SHORT_CIRCUITED)
    createRollingCountForEvent("rollingCountSuccess", HystrixRollingNumberEvent.SUCCESS)
    createRollingCountForEvent("rollingCountThreadPoolRejected", HystrixRollingNumberEvent.THREAD_POOL_REJECTED)
    createRollingCountForEvent("rollingCountTimeout", HystrixRollingNumberEvent.TIMEOUT)

    // the number of executionSemaphorePermits in use right now
    createGuageForMetrics(createMetricName("executionSemaphorePermitsInUse"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getCurrentConcurrentExecutionCount()
    })

    // error percentages
    createGuageForMetrics(createMetricName("errorPercentage"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getHealthCounts().getErrorPercentage()
    })

    // latency metrics
    createGuageForMetrics(createMetricName("latencyExecute_mean"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getExecutionTimeMean()
    })

    createGuageForMetrics(createMetricName("latencyExecute__percentile_5"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getExecutionTimePercentile(5)
    })

    createGuageForMetrics(createMetricName("latencyExecute__percentile_25"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getExecutionTimePercentile(25)
    })

    createGuageForMetrics(createMetricName("latencyExecute__percentile_50"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getExecutionTimePercentile(50)
    })

    createGuageForMetrics(createMetricName("latencyExecute__percentile_75"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getExecutionTimePercentile(75)
    })

    createGuageForMetrics(createMetricName("latencyExecute__percentile_90"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getExecutionTimePercentile(90)
    })

    createGuageForMetrics(createMetricName("latencyExecute__percentile_99"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getExecutionTimePercentile(99)
    })

    createGuageForMetrics(createMetricName("latencyExecute__percentile_995"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getExecutionTimePercentile(99.5)
    })

    createGuageForMetrics(createMetricName("latencyTotal_mean"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getTotalTimeMean()
    })

    createGuageForMetrics(createMetricName("latencyTotal__percentile_5"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getTotalTimePercentile(5)
    })

    createGuageForMetrics(createMetricName("latencyTotal__percentile_25"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getTotalTimePercentile(25)
    })

    createGuageForMetrics(createMetricName("latencyTotal__percentile_50"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getTotalTimePercentile(50)
    })

    createGuageForMetrics(createMetricName("latencyTotal__percentile_75"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getTotalTimePercentile(75)
    })

    createGuageForMetrics(createMetricName("latencyTotal__percentile_90"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getTotalTimePercentile(90)
    })

    createGuageForMetrics(createMetricName("latencyTotal__percentile_99"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getTotalTimePercentile(99)
    })

    createGuageForMetrics(createMetricName("latencyTotal__percentile_995"), { Object ref ->
      return ((HystrixCommandMetrics) ref).getTotalTimePercentile(99.5)
    })
  }

  void createGuageForMetrics(Id id, Closure<Double> closure) {
    metricRegistry.gauge(id, metrics, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return closure.call(ref)
      }
    })
  }

  protected Id createMetricName(String name) {
    return metricRegistry
      .createId("hystrix.${name}" as String)
      .withTag("metricGroup", metricGroup)
      .withTag("metricType", metricType)
  }

  protected void createCumulativeCountForEvent(String name, final HystrixRollingNumberEvent event) {
    metricRegistry.gauge(createMetricName(name), event, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return metrics.getCumulativeCount((HystrixRollingNumberEvent) event)
      }
    })
  }

  protected void createRollingCountForEvent(String name, final HystrixRollingNumberEvent event) {
    metricRegistry.gauge(createMetricName(name), event, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return metrics.getRollingCount((HystrixRollingNumberEvent) event)
      }
    })
  }
}
