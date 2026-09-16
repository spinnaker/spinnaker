/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.kork.aws;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

/**
 * Records AWS SDK v2 client metrics (call latency, retry counts, throttling, etc.) for every v2
 * client built anywhere in the JVM. This is attached automatically via the SDK's global
 * execution-interceptor classpath discovery mechanism (see {@code
 * software/amazon/awssdk/global/handlers/execution.interceptors} in this module's resources), so it
 * needs a public no-arg constructor -- the SDK instantiates it by reflection, not through Spring.
 * It resolves the target registry via {@link Metrics#globalRegistry}, which {@link AwsComponents}
 * adds every Spinnaker app's own {@code MeterRegistry} bean to, so metrics land in the same place
 * regardless of whether a caller injects {@code MeterRegistry} directly. This replaces {@code
 * com.netflix.spectator.aws2.SpectatorExecutionInterceptor}, which relied on {@code
 * Spectator.globalRegistry()} the same way.
 */
public class MicrometerExecutionInterceptor implements ExecutionInterceptor {
  private static final ExecutionAttribute<Long> START_TIME_NANOS =
      new ExecutionAttribute<>("micrometerStartTimeNanos");

  @Override
  public void beforeTransmission(Context.BeforeTransmission context, ExecutionAttributes attrs) {
    attrs.putAttribute(START_TIME_NANOS, System.nanoTime());
  }

  @Override
  public void afterExecution(Context.AfterExecution context, ExecutionAttributes attrs) {
    recordDuration(attrs, "true");
  }

  @Override
  public void onExecutionFailure(Context.FailedExecution context, ExecutionAttributes attrs) {
    recordDuration(attrs, "false");
  }

  private void recordDuration(ExecutionAttributes attrs, String success) {
    Long startTimeNanos = attrs.getAttribute(START_TIME_NANOS);
    if (startTimeNanos == null) {
      return;
    }
    String serviceName = attrs.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String operationName = attrs.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
    Timer.builder("aws.sdk.v2.apiCallDuration")
        .tags("service", serviceName, "operation", operationName, "success", success)
        .register(Metrics.globalRegistry)
        .record(System.nanoTime() - startTimeNanos, TimeUnit.NANOSECONDS);
  }
}
