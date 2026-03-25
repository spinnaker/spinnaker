/*
    Copyright (C) 2024 Nordix Foundation.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
          http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
*/

package com.netflix.spinnaker.echo.cdevents;

import io.cloudevents.CloudEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Sends CDEvents as OTel spans to an OTLP/gRPC endpoint (e.g. SigNoz on port 4317). */
@Slf4j
@Component
@ConditionalOnProperty(name = "cdevents.transport", havingValue = "otlp")
public class CDEventsOTelSender {

  private static final String INSTRUMENTATION_NAME = "spinnaker-cdevents";
  // Unbounded cache keyed by endpoint URL. In practice, endpoints are static per-pipeline config,
  // so the number of entries is small and bounded by the number of distinct OTLP endpoints.
  private final Map<String, OpenTelemetrySdk> sdkCache = new ConcurrentHashMap<>();
  private final long timeoutSeconds;

  public CDEventsOTelSender(CDEventsConfigProperties config) {
    this.timeoutSeconds = config.getOtlpTimeoutSeconds();
  }

  public void send(CloudEvent cdEvent, String otlpEndpoint) {
    Tracer tracer = getOrCreateSdk(otlpEndpoint).getTracer(INSTRUMENTATION_NAME);

    Attributes attrs =
        Attributes.of(
            AttributeKey.stringKey("cdevents.type"), cdEvent.getType(),
            AttributeKey.stringKey("cdevents.source"), cdEvent.getSource().toString(),
            AttributeKey.stringKey("cdevents.id"), cdEvent.getId());

    if (cdEvent.getData() != null) {
      String payload = new String(cdEvent.getData().toBytes(), StandardCharsets.UTF_8);
      attrs = attrs.toBuilder().put(AttributeKey.stringKey("cdevents.data"), payload).build();
    }

    Span span =
        tracer
            .spanBuilder(cdEvent.getType())
            .setSpanKind(SpanKind.PRODUCER)
            .setAllAttributes(attrs)
            .startSpan();

    if (cdEvent.getType() != null && cdEvent.getType().contains("finished")) {
      // Check data payload for outcome to set span status
      if (cdEvent.getData() != null) {
        String data = new String(cdEvent.getData().toBytes(), StandardCharsets.UTF_8);
        if (data.contains("\"outcome\":\"failure\"")) {
          span.setStatus(StatusCode.ERROR);
        }
      }
    }

    span.end();
    log.info("Sent CDEvent {} as OTel span to {}", cdEvent.getType(), otlpEndpoint);
  }

  private OpenTelemetrySdk getOrCreateSdk(String endpoint) {
    return sdkCache.computeIfAbsent(
        endpoint,
        ep -> {
          OtlpGrpcSpanExporter exporter =
              OtlpGrpcSpanExporter.builder()
                  .setEndpoint(ep)
                  .setTimeout(timeoutSeconds, TimeUnit.SECONDS)
                  .build();

          SdkTracerProvider tracerProvider =
              SdkTracerProvider.builder()
                  .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                  .setResource(
                      Resource.builder().put("service.name", "spinnaker-echo-cdevents").build())
                  .build();

          return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
        });
  }
}
