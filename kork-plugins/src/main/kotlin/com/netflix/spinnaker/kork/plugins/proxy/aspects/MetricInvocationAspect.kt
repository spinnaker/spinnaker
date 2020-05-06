/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.kork.plugins.proxy.aspects

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.histogram.PercentileTimer
import com.netflix.spinnaker.kork.annotations.Metered
import com.netflix.spinnaker.kork.plugins.SpinnakerPluginDescriptor
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint
import com.netflix.spinnaker.kork.telemetry.MethodInstrumentation
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider

/**
 * Adds metric instrumentation to extension method invocations.
 *
 * Two metrics will be recorded for any extension: timing and invocations, with an additional tag
 * for "result", having either the value "success" or "failure".
 *
 */
class MetricInvocationAspect(
  private val registryProvider: ObjectProvider<Registry>
) : InvocationAspect<MetricInvocationState> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val methodMetricIds: Cache<Method, MetricIds> = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build<Method, MetricIds>()

  /**
   * Extensions are loaded early in the Spring application lifecycle, and there's a chance that the
   * Spectator [Registry] does not yet exist when an extension method is invoked.  This allows us to
   * fallback to a temporary registry.  Metrics collected in the fallback registry are discarded.
   *
   * Open question - If a fallback registry is returned, can we collect those metrics and then dump
   * them onto the main registry once that exists?
   */
  private fun ObjectProvider<Registry>.getOrFallback(extensionName: String): Registry {
    val registry = this.ifAvailable

    if (registry == null) {
      log.warn("Returning fallback registry for extension={}; metrics collected in fallback are discarded.", extensionName)
      return DefaultRegistry(Clock.SYSTEM)
    }

    return registry
  }

  override fun supports(invocationState: Class<InvocationState>): Boolean {
    return invocationState == MetricInvocationState::class.java
  }

  override fun before(
    target: SpinnakerExtensionPoint,
    proxy: Any,
    method: Method,
    args: Array<out Any>?,
    descriptor: SpinnakerPluginDescriptor
  ): MetricInvocationState {
    val extensionName = target.javaClass.simpleName.toString()
    val registry = registryProvider.getOrFallback(extensionName)
    val metricIds = methodMetricIds.getOrPut(target, method, descriptor, registry)

    return MetricInvocationState(
      extensionName = extensionName,
      startTimeMs = System.currentTimeMillis(),
      timingId = metricIds?.timingId
    )
  }

  override fun after(invocationState: MetricInvocationState) {
    recordMetrics(Result.SUCCESS, invocationState)
  }

  override fun error(e: InvocationTargetException, invocationState: MetricInvocationState) {
    recordMetrics(Result.FAILURE, invocationState)
  }

  private fun recordMetrics(result: Result, invocationState: MetricInvocationState) {
    if (invocationState.timingId != null) {
      val registry = registryProvider.getOrFallback(invocationState.extensionName)
      PercentileTimer.get(registry, invocationState.timingId.withTag("result", result.toString()))
        .record(System.currentTimeMillis() - invocationState.startTimeMs, TimeUnit.MILLISECONDS)
    }
  }

  /**
   * Looks for methods annotated with [Metered] and skips private methods.
   * Performs a [Cache] `get` which retrieves the cached data or else creates the data and then
   * inserts it into the cache.
   */
  private fun Cache<Method, MetricIds>.getOrPut(
    target: Any,
    method: Method,
    descriptor: SpinnakerPluginDescriptor,
    registry: Registry
  ): MetricIds? {
    if (!MethodInstrumentation.isMethodAllowed(method)) {
      return null
    } else {
      return this.get(method) { m ->
        m.declaredAnnotations
          .find { it is Metered }
          .let { metered ->
            if (metered != null) {
              (metered as Metered)

              if (metered.ignore) {
                null
              } else {
                val defaultTags = mapOf(
                  Pair("pluginVersion", descriptor.version),
                  Pair("pluginExtension", target.javaClass.simpleName.toString())
                )
                val tags = MethodInstrumentation.coalesceTags(target,
                  method, defaultTags, metered.tags)

                val metricIds = MetricIds(
                  timingId = registry.createId(toMetricId(m, descriptor.pluginId, metered.metricName, TIMING), tags))

                for (mutableEntry in this.asMap()) {
                  if (mutableEntry.value.timingId.name() == metricIds.timingId.name()) {
                    throw MethodInstrumentation.MetricNameCollisionException(target,
                      metricIds.timingId.name(), mutableEntry.key, m)
                  }
                }
                metricIds
              }
            } else {
              null
            }
          }
      }
    }
  }

  private fun toMetricId(method: Method, metricNamespace: String, annotationMetricId: String?, metricName: String): String? {
    val methodMetricId = if (method.parameterCount == 0) method.name else String.format("%s%d", method.name, method.parameterCount)
    val metricId = if (annotationMetricId.isNullOrEmpty()) methodMetricId else annotationMetricId
    return MethodInstrumentation.toMetricId(metricNamespace, metricId, metricName)
  }

  private data class MetricIds(val timingId: Id)

  companion object {
    private const val TIMING = "timing"

    enum class Result {
      SUCCESS, FAILURE;

      override fun toString(): String = name.toLowerCase()
    }
  }
}
