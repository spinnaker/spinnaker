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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sends CDEvents as OTel spans to an OTLP/gRPC endpoint.
 *
 * <p>Unlike the HTTP transport where the receiving broker (e.g. Knative, Keptn) correlates related
 * CloudEvents using the {@code subject.id} field, OTLP/OpenTelemetry backends (e.g. Jaeger, SigNoz,
 * Grafana Tempo) expect trace correlation to be established by the producer via shared trace/span
 * IDs. This class therefore maintains a mapping of Spinnaker execution IDs to W3C {@code
 * traceparent} strings so that all CDEvents belonging to the same pipeline execution appear as a
 * single trace:
 *
 * <pre>
 *   pipelinerun.started          (root span)
 *   ├── taskrun.started.Build    (child)
 *   ├── taskrun.finished.Build   (child)
 *   ├── taskrun.started.Test     (child)
 *   ├── ...
 *   └── pipelinerun.finished     (child)
 * </pre>
 *
 * <p>Stage names from the original Orca event are appended to taskrun span names and added as the
 * {@code cdevents.stage.name} attribute for easy filtering in trace UIs.
 *
 * <p>Entries from the CDEvent {@code customData} map (populated via Spinnaker notification
 * preferences or event context) are promoted to individual {@code cdevents.custom.<key>} span
 * attributes. This allows CI variables such as commit SHAs, repository names, or Helm chart
 * versions to become first-class searchable attributes in trace UIs.
 *
 * <p>This class is only instantiated when {@code cdevents.transport=otlp}. The HTTP transport path
 * in {@link com.netflix.spinnaker.echo.notification.CDEventsNotificationAgent} is completely
 * unaffected.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cdevents.transport", havingValue = "otlp")
public class CDEventsOTelSender {

  private static final String INSTRUMENTATION_NAME = "spinnaker-cdevents";
  private static final ObjectMapper mapper = new ObjectMapper();
  // Unbounded cache keyed by endpoint URL. In practice, endpoints are static per-pipeline config,
  // so the number of entries is small and bounded by the number of distinct OTLP endpoints.
  private final Map<String, OpenTelemetrySdk> sdkCache = new ConcurrentHashMap<>();
  // Tracks the root pipeline trace context keyed by execution ID so stage spans become children.
  private static final TextMapPropagator PROPAGATOR = W3CTraceContextPropagator.getInstance();
  private static final TextMapGetter<Map<String, String>> MAP_GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
          return carrier.get(key);
        }
      };
  private final Map<String, String> pipelineTraceContext = new ConcurrentHashMap<>();
  private final CDEventsConfigProperties config;
  private final byte[] caCertBytes;
  private final byte[] clientCertBytes;
  private final byte[] clientKeyBytes;

  public CDEventsOTelSender(CDEventsConfigProperties config) {
    this.config = config;
    try {
      this.caCertBytes =
          config.getOtlpCaCertPath() != null
              ? Files.readAllBytes(Path.of(config.getOtlpCaCertPath()))
              : null;
      this.clientCertBytes =
          config.getOtlpClientCertPath() != null
              ? Files.readAllBytes(Path.of(config.getOtlpClientCertPath()))
              : null;
      this.clientKeyBytes =
          config.getOtlpClientKeyPath() != null
              ? Files.readAllBytes(Path.of(config.getOtlpClientKeyPath()))
              : null;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read OTLP TLS certificate files", e);
    }
  }

  /**
   * Sends a CDEvent as an OTel span. Backwards-compatible overload that delegates to {@link
   * #send(CloudEvent, String, Map, com.netflix.spinnaker.echo.api.events.Event)} with no additional
   * context.
   */
  public void send(CloudEvent cdEvent, String otlpEndpoint) {
    send(cdEvent, otlpEndpoint, null, null);
  }

  /**
   * Sends a CDEvent as an OTel span with trace correlation and stage name enrichment.
   *
   * @param cdEvent the CloudEvent to send
   * @param otlpEndpoint the OTLP/gRPC endpoint URL
   * @param config Orca event config containing the event type ("pipeline" or "stage"), may be null
   * @param event the original Orca event, used to extract stage name from {@code
   *     content.context.stageDetails.name}, may be null
   */
  @SuppressWarnings("unchecked")
  public void send(
      CloudEvent cdEvent,
      String otlpEndpoint,
      Map<String, String> config,
      com.netflix.spinnaker.echo.api.events.Event event) {
    Tracer tracer = getOrCreateSdk(otlpEndpoint).getTracer(INSTRUMENTATION_NAME);
    String type = cdEvent.getType();
    String executionId = extractExecutionId(cdEvent);

    // Extract stage name from the original Orca event
    String stageName = null;
    if (event != null && event.content != null) {
      Map<String, Object> context = (Map<String, Object>) event.content.get("context");
      if (context != null) {
        Map<String, Object> stageDetails = (Map<String, Object>) context.get("stageDetails");
        if (stageDetails != null) {
          stageName = (String) stageDetails.get("name");
        }
      }
    }

    // Build span name: append stage name for taskrun events
    String spanName = type;
    if (stageName != null && type != null) {
      spanName = type.replaceFirst("\\.\\d+\\.\\d+\\.\\d+$", "") + "." + stageName;
    }

    Attributes attrs =
        Attributes.of(
            AttributeKey.stringKey("cdevents.type"), type,
            AttributeKey.stringKey("cdevents.source"), cdEvent.getSource().toString(),
            AttributeKey.stringKey("cdevents.id"), cdEvent.getId());

    if (stageName != null) {
      attrs =
          attrs.toBuilder().put(AttributeKey.stringKey("cdevents.stage.name"), stageName).build();
    }
    if (config != null && config.get("type") != null) {
      attrs =
          attrs.toBuilder()
              .put(AttributeKey.stringKey("cdevents.config.type"), config.get("type"))
              .build();
    }

    if (cdEvent.getData() != null) {
      String payload = new String(cdEvent.getData().toBytes(), StandardCharsets.UTF_8);
      attrs = attrs.toBuilder().put(AttributeKey.stringKey("cdevents.data"), payload).build();
      attrs = promoteCustomData(attrs, cdEvent);
    }

    var spanBuilder =
        tracer.spanBuilder(spanName).setSpanKind(SpanKind.PRODUCER).setAllAttributes(attrs);

    // Parent child spans under the pipeline root span
    boolean isPipelineStart =
        type != null && type.contains("pipelinerun") && !type.contains("finished");
    boolean isPipelineEnd =
        type != null && type.contains("pipelinerun") && type.contains("finished");

    if (!isPipelineStart && executionId != null) {
      String traceparent = pipelineTraceContext.get(executionId);
      if (traceparent != null) {
        Context parentCtx =
            PROPAGATOR.extract(Context.root(), Map.of("traceparent", traceparent), MAP_GETTER);
        spanBuilder.setParent(parentCtx);
      }
    }

    Span span = spanBuilder.startSpan();

    // Store root trace context for pipeline start events
    if (isPipelineStart && executionId != null) {
      Map<String, String> carrier = new HashMap<>();
      PROPAGATOR.inject(Context.root().with(span), carrier, Map::put);
      pipelineTraceContext.put(executionId, carrier.get("traceparent"));
    }

    if (type != null && type.contains("finished")) {
      if (cdEvent.getData() != null) {
        String data = new String(cdEvent.getData().toBytes(), StandardCharsets.UTF_8);
        if (data.contains("\"outcome\":\"failure\"")) {
          span.setStatus(StatusCode.ERROR);
        }
      }
      // Clean up stored context when pipeline finishes
      if (isPipelineEnd && executionId != null) {
        pipelineTraceContext.remove(executionId);
      }
    }

    span.end();
    log.info("Sent CDEvent {} as OTel span to {}", type, otlpEndpoint);
  }

  private String extractExecutionId(CloudEvent cdEvent) {
    if (cdEvent.getData() == null) return null;
    try {
      JsonNode root = mapper.readTree(cdEvent.getData().toBytes());
      JsonNode id = root.path("subject").path("id");
      return id.isMissingNode() ? null : id.asText();
    } catch (Exception e) {
      log.debug("Could not extract execution ID from CDEvent data", e);
      return null;
    }
  }

  /**
   * Promotes entries from the CDEvent {@code customData} map to individual span attributes prefixed
   * with {@code cdevents.custom.}. This allows CI variables (commit SHA, repo, helm chart version
   * etc.) configured via Spinnaker notification preferences to become first-class searchable
   * attributes in trace UIs.
   */
  private Attributes promoteCustomData(Attributes attrs, CloudEvent cdEvent) {
    try {
      JsonNode root = mapper.readTree(cdEvent.getData().toBytes());
      JsonNode customData = root.path("customData");
      if (customData.isMissingNode() || !customData.isObject()) return attrs;
      var builder = attrs.toBuilder();
      var fields = customData.fields();
      while (fields.hasNext()) {
        var entry = fields.next();
        builder.put(
            AttributeKey.stringKey("cdevents.custom." + entry.getKey()), entry.getValue().asText());
      }
      return builder.build();
    } catch (Exception e) {
      log.debug("Could not extract customData from CDEvent", e);
      return attrs;
    }
  }

  private OpenTelemetrySdk getOrCreateSdk(String endpoint) {
    return sdkCache.computeIfAbsent(
        endpoint,
        ep -> {
          OtlpGrpcSpanExporterBuilder builder =
              OtlpGrpcSpanExporter.builder()
                  .setEndpoint(ep)
                  .setTimeout(config.getOtlpTimeoutSeconds(), TimeUnit.SECONDS);

          configureTls(builder);

          SdkTracerProvider tracerProvider =
              SdkTracerProvider.builder()
                  .addSpanProcessor(BatchSpanProcessor.builder(builder.build()).build())
                  .setResource(
                      Resource.builder().put("service.name", "spinnaker-echo-cdevents").build())
                  .build();

          return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
        });
  }

  private void configureTls(OtlpGrpcSpanExporterBuilder builder) {
    if (caCertBytes != null) {
      builder.setTrustedCertificates(caCertBytes);
    }
    if (clientCertBytes != null && clientKeyBytes != null) {
      builder.setClientTls(clientKeyBytes, clientCertBytes);
    }
  }
}
