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
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
 * IDs.
 *
 * <p>Trace and span IDs are derived deterministically from the Spinnaker execution ID using SHA-256
 * hashing. This means any Echo pod can produce the correct parent-child relationship without shared
 * state:
 *
 * <pre>
 *   pipelinerun.started          (root span, traceId = hash(executionId))
 *   ├── taskrun.started [Build]  (child)
 *   ├── taskrun.finished [Build] (child)
 *   └── pipelinerun.finished     (child)
 * </pre>
 *
 * <p>Stage names from the original Orca event are added as the {@code cdevents.stage.name}
 * attribute for filtering in trace UIs.
 *
 * <p>Entries from the CDEvent {@code customData} map are promoted to individual {@code
 * cdevents.custom.<key>} span attributes, making CI variables searchable in trace UIs.
 *
 * <p>This class is only instantiated when {@code cdevents.transport=otlp}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cdevents.transport", havingValue = "otlp")
public class CDEventsOTelSender {

  private static final String INSTRUMENTATION_NAME = "spinnaker-cdevents";

  private final Map<String, OpenTelemetrySdk> sdkCache = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;
  private final CDEventsConfigProperties config;
  private final byte[] caCertBytes;
  private final byte[] clientCertBytes;
  private final byte[] clientKeyBytes;

  public CDEventsOTelSender(CDEventsConfigProperties config, ObjectMapper objectMapper) {
    this.config = config;
    this.objectMapper = objectMapper;
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
   * @param event the original Orca event, used to extract stage name, may be null
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
    String stageName = extractStageName(event);

    // Build span name: use short type (e.g. "pipelinerun.started") with stage name in brackets
    String shortType = shortenType(type);
    String spanName = stageName != null ? shortType + " [" + stageName + "]" : shortType;

    Attributes attrs =
        Attributes.of(
            AttributeKey.stringKey("cdevents.type"), shortType,
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

    // All spans with an execution ID get the deterministic trace ID via parent context.
    // Pipeline start spans become children of the synthetic root (which is never emitted),
    // so trace backends display them as the effective root of the trace.
    if (executionId != null) {
      SpanContext parentCtx = deriveRootSpanContext(executionId);
      spanBuilder.setParent(Context.root().with(Span.wrap(parentCtx)));
    }

    Span span = spanBuilder.startSpan();

    // Check for failure outcome using proper JSON parsing
    if (type != null && type.contains("finished") && cdEvent.getData() != null) {
      if (hasFailureOutcome(cdEvent)) {
        span.setStatus(StatusCode.ERROR);
      }
    }

    span.end();
    log.debug("Sent CDEvent {} as OTel span to {}", shortType, otlpEndpoint);
  }

  /**
   * Derives a deterministic trace ID from the execution ID using SHA-256. Every Echo pod produces
   * the same trace ID for the same pipeline execution — no shared state required.
   */
  static String traceIdFromExecutionId(String executionId) {
    byte[] hash = sha256(executionId);
    return HexFormat.of().formatHex(hash, 0, 16);
  }

  /**
   * Derives a deterministic root span ID from the execution ID. Uses bytes 16-24 of the SHA-256
   * hash to avoid collision with the trace ID.
   */
  static String rootSpanIdFromExecutionId(String executionId) {
    byte[] hash = sha256(executionId);
    return HexFormat.of().formatHex(hash, 16, 24);
  }

  private SpanContext deriveRootSpanContext(String executionId) {
    return SpanContext.createFromRemoteParent(
        traceIdFromExecutionId(executionId),
        rootSpanIdFromExecutionId(executionId),
        TraceFlags.getSampled(),
        TraceState.getDefault());
  }

  private static byte[] sha256(String input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /** Strips the version suffix (e.g. ".0.1.1") from a CDEvents type string. */
  private static String shortenType(String type) {
    if (type == null) return "unknown";
    return type.replaceFirst("\\.\\d+\\.\\d+\\.\\d+$", "");
  }

  @SuppressWarnings("unchecked")
  private static String extractStageName(com.netflix.spinnaker.echo.api.events.Event event) {
    if (event == null || event.content == null) return null;
    Map<String, Object> context = (Map<String, Object>) event.content.get("context");
    if (context == null) return null;
    Map<String, Object> stageDetails = (Map<String, Object>) context.get("stageDetails");
    if (stageDetails == null) return null;
    return (String) stageDetails.get("name");
  }

  private String extractExecutionId(CloudEvent cdEvent) {
    if (cdEvent.getData() == null) return null;
    try {
      JsonNode root = objectMapper.readTree(cdEvent.getData().toBytes());
      JsonNode id = root.path("subject").path("id");
      return id.isMissingNode() ? null : id.asText();
    } catch (Exception e) {
      log.debug("Could not extract execution ID from CDEvent data", e);
      return null;
    }
  }

  /** Checks for failure outcome by parsing the JSON properly rather than string matching. */
  private boolean hasFailureOutcome(CloudEvent cdEvent) {
    try {
      JsonNode root = objectMapper.readTree(cdEvent.getData().toBytes());
      JsonNode outcome = root.path("outcome");
      return !outcome.isMissingNode() && "failure".equals(outcome.asText());
    } catch (Exception e) {
      log.debug("Could not parse CDEvent data for outcome check", e);
      return false;
    }
  }

  /**
   * Promotes entries from the CDEvent {@code customData} map to individual span attributes prefixed
   * with {@code cdevents.custom.}.
   */
  private Attributes promoteCustomData(Attributes attrs, CloudEvent cdEvent) {
    try {
      JsonNode root = objectMapper.readTree(cdEvent.getData().toBytes());
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
