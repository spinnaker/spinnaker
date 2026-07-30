/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.artifacts.s3;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;

/**
 * Records AWS SDK v2 request timing into a {@link MeterRegistry}, replacing Spectator's {@code
 * com.netflix.spectator.aws2.SpectatorExecutionInterceptor}.
 */
class MicrometerExecutionInterceptor implements ExecutionInterceptor {
  private static final ExecutionAttribute<Long> START_TIME_NANOS =
      new ExecutionAttribute<>("micrometerStartTimeNanos");

  private final MeterRegistry registry;

  MicrometerExecutionInterceptor(MeterRegistry registry) {
    this.registry = registry;
  }

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
        .register(registry)
        .record(System.nanoTime() - startTimeNanos, TimeUnit.NANOSECONDS);
  }
}
