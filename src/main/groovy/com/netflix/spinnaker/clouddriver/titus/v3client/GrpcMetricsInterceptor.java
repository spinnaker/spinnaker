/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.titus.v3client;

import com.google.common.base.Stopwatch;
import com.netflix.grpc.metrics.MetricsContext;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion;
import io.grpc.*;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class GrpcMetricsInterceptor implements ClientInterceptor {

  private final Registry registry;
  private final ConcurrentMap<String, AtomicInteger> methodToInflightCount = new ConcurrentHashMap<>();
  private final TitusRegion titusRegion;


  public GrpcMetricsInterceptor(Registry registry, TitusRegion titusRegion) {
    this.registry = registry;
    this.titusRegion = titusRegion;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

    Stopwatch startTime = Stopwatch.createStarted();
    String methodName = extractSimpleMethodName(method.getFullMethodName());

    final MetricsContext metricsContext = callOptions.getOption(MetricsContext.CALL_OPTIONS_KEY);

    ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);

    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(call) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
          @Override
          public void onClose(Status status, Metadata trailers) {
            callEnded(status, methodName, startTime, status.getDescription());
            if (metricsContext != null) {
              metricsContext.putTag("titusAccount",titusRegion.getAccount());
              metricsContext.putTag("titusRegion",titusRegion.getName());
            }
            super.onClose(status, trailers);
          }
        }, headers);
      }

      @Override
      public void cancel(@Nullable String message, @Nullable Throwable cause) {
        super.cancel(message, cause);
        callEnded(Status.CANCELLED, methodName, startTime, message);
      }
    };
  }

  private void callEnded(Status status, String methodName, Stopwatch startTime, String message) {
    Id timerId = registry.createId("titus.request")
      .withTag("titusAccount", titusRegion.getAccount())
      .withTag("titusRegion", titusRegion.getName())
      .withTag("request", methodName)
      .withTag("apiVersion", "3")
      .withTag("success", Boolean.toString(status.isOk()))
      .withTag("responseCode", Optional.ofNullable(status.getCode().value()).map(Object::toString).orElse("UNKNOWN"))
      .withTag("message", Optional.ofNullable(message).map(Object::toString).orElse(status.getCode().name()));
    registry.timer(timerId).record(startTime.elapsed(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS);
  }

  public static String extractSimpleMethodName(String fullMethodName) {
    int pos = fullMethodName.indexOf("/");
    return pos > -1 ? fullMethodName.substring(pos + 1) : fullMethodName;
  }

}
