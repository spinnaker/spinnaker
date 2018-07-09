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

import com.netflix.hystrix.HystrixThreadPoolKey
import com.netflix.hystrix.HystrixThreadPoolMetrics
import com.netflix.hystrix.HystrixThreadPoolProperties
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry

import java.util.function.ToDoubleFunction

class HystrixSpectatorPublisherThreadPool implements HystrixMetricsPublisherThreadPool {
  private final HystrixThreadPoolKey key
  private final HystrixThreadPoolMetrics metrics
  private final HystrixThreadPoolProperties properties
  private final Registry metricRegistry
  private final String metricGroup
  private final String metricType

  public HystrixSpectatorPublisherThreadPool(HystrixThreadPoolKey threadPoolKey,
                                             HystrixThreadPoolMetrics metrics,
                                             HystrixThreadPoolProperties properties,
                                             Registry metricRegistry) {
    this.key = threadPoolKey
    this.metrics = metrics
    this.properties = properties
    this.metricRegistry = metricRegistry
    this.metricGroup = "HystrixThreadPool"
    this.metricType = key.name()
  }

  @Override
  public void initialize() {
    metricRegistry.gauge(createMetricName("currentTime"), metrics, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return System.currentTimeMillis()
      }
    })

    metricRegistry.gauge(createMetricName("threadActiveCount"), metrics, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return ((HystrixThreadPoolMetrics)ref).getCurrentActiveCount()
      }
    })

    metricRegistry.gauge(createMetricName("completedTaskCount"), metrics, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return ((HystrixThreadPoolMetrics)ref).getCurrentCompletedTaskCount()
      }
    })

    metricRegistry.gauge(createMetricName("largestPoolSize"), metrics, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return ((HystrixThreadPoolMetrics)ref).getCurrentLargestPoolSize()
      }
    })

    metricRegistry.gauge(createMetricName("totalTaskCount"), metrics, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return ((HystrixThreadPoolMetrics)ref).getCurrentTaskCount()
      }
    })

    metricRegistry.gauge(createMetricName("queueSize"), metrics, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return ((HystrixThreadPoolMetrics)ref).getCurrentQueueSize()
      }
    })

    metricRegistry.gauge(createMetricName("rollingMaxActiveThreads"), metrics, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return ((HystrixThreadPoolMetrics)ref).getRollingMaxActiveThreads()
      }
    })

    metricRegistry.gauge(createMetricName("countThreadsExecuted"), metrics, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return ((HystrixThreadPoolMetrics)ref).getCumulativeCountThreadsExecuted()
      }
    })

    metricRegistry.gauge(createMetricName("rollingCountThreadsExecuted"), metrics, new ToDoubleFunction() {
      @Override
      double applyAsDouble(Object ref) {
        return ((HystrixThreadPoolMetrics)ref).getRollingCountThreadsExecuted()
      }
    })
  }

  protected Id createMetricName(String name) {
    return metricRegistry
      .createId("hystrix.${name}" as String)
      .withTag("metricGroup", metricGroup)
      .withTag("metricType", metricType)
  }
}
