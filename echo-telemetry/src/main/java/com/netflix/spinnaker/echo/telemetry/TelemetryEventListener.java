/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.echo.telemetry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.util.JsonFormat;
import com.netflix.spinnaker.echo.config.TelemetryConfig;
import com.netflix.spinnaker.echo.events.EchoEventListener;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.kork.proto.stats.Application;
import com.netflix.spinnaker.kork.proto.stats.CloudProvider;
import com.netflix.spinnaker.kork.proto.stats.CloudProvider.ID;
import com.netflix.spinnaker.kork.proto.stats.Execution;
import com.netflix.spinnaker.kork.proto.stats.SpinnakerInstance;
import com.netflix.spinnaker.kork.proto.stats.Stage;
import com.netflix.spinnaker.kork.proto.stats.Status;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import retrofit.mime.TypedString;

@Slf4j
@Component
@ConditionalOnProperty("telemetry.enabled")
public class TelemetryEventListener implements EchoEventListener {

  protected static final String TELEMETRY_REGISTRY_NAME = "telemetry";

  private static final Set<String> LOGGABLE_DETAIL_TYPES =
      ImmutableSet.of(
          "orca:orchestration:complete",
          "orca:orchestration:failed",
          "orca:pipeline:complete",
          "orca:pipeline:failed");

  private static final JsonFormat.Printer JSON_PRINTER =
      JsonFormat.printer().includingDefaultValueFields();

  private static final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final TelemetryService telemetryService;

  private final TelemetryConfig.TelemetryConfigProps telemetryConfigProps;

  private final CircuitBreakerRegistry registry;

  @Autowired
  public TelemetryEventListener(
      TelemetryService telemetryService,
      TelemetryConfig.TelemetryConfigProps telemetryConfigProps,
      CircuitBreakerRegistry registry) {
    this.telemetryService = telemetryService;
    this.telemetryConfigProps = telemetryConfigProps;
    this.registry = registry;
  }

  @Override
  public void processEvent(Event event) {
    if (event.getDetails() == null || event.getContent() == null) {
      log.debug("Telemetry not sent: Details or content not found in event");
      return;
    }

    String eventType = event.getDetails().getType();
    if (!LOGGABLE_DETAIL_TYPES.contains(eventType)) {
      log.debug("Telemetry not sent: type '{}' not whitelisted ", eventType);
      return;
    }

    String applicationId = event.getDetails().getApplication();
    if (applicationId == null || applicationId.isEmpty()) {
      log.debug("Application ID must be non-null and not empty");
      return;
    }

    Holder.Content content = objectMapper.convertValue(event.getContent(), Holder.Content.class);
    Holder.Execution execution = content.getExecution();

    // TODO(ttomsu, louisjimenez): Add MPTv1 and v2 execution type detection.
    Execution.Type executionType =
        Execution.Type.valueOf(
            parseEnum(Execution.Type.getDescriptor(), execution.getType().toUpperCase()));

    Status executionStatus =
        Status.valueOf(parseEnum(Status.getDescriptor(), execution.getStatus().toUpperCase()));

    Execution.Trigger.Type triggerType =
        Execution.Trigger.Type.valueOf(
            parseEnum(
                Execution.Trigger.Type.getDescriptor(),
                execution.getTrigger().getType().toUpperCase()));

    List<Stage> protoStages =
        execution.getStages().stream()
            .map(this::toStages)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    Execution.Builder executionBuilder =
        Execution.newBuilder()
            .setType(executionType)
            .setStatus(executionStatus)
            .setTrigger(Execution.Trigger.newBuilder().setType(triggerType))
            .addAllStages(protoStages);
    String executionId = execution.getId();
    if (!executionId.isEmpty()) {
      executionBuilder.setId(hash(executionId));
    }
    Execution executionProto = executionBuilder.build();

    // We want to ensure it's really hard to guess the application name. Using the instance ID (a
    // ULID) provides a good level of randomness as a salt, and is not easily guessable.
    String instanceId = telemetryConfigProps.getInstanceId();
    String hashedInstanceId = hash(instanceId);
    String hashedApplicationId = hash(applicationId, instanceId);

    Application application = Application.newBuilder().setId(hashedApplicationId).build();

    SpinnakerInstance spinnakerInstance =
        SpinnakerInstance.newBuilder()
            .setId(hashedInstanceId)
            .setVersion(telemetryConfigProps.getSpinnakerVersion())
            .build();

    com.netflix.spinnaker.kork.proto.stats.Event loggedEvent =
        com.netflix.spinnaker.kork.proto.stats.Event.newBuilder()
            .setSpinnakerInstance(spinnakerInstance)
            .setApplication(application)
            .setExecution(executionProto)
            .build();

    try {
      String jsonContent = JSON_PRINTER.print(loggedEvent);
      log.debug("Sending telemetry event:\n{}", jsonContent);

      registry
          .circuitBreaker(TELEMETRY_REGISTRY_NAME)
          .executeCallable(() -> telemetryService.log(new TypedJsonString(jsonContent)));
      log.debug("Telemetry sent!");
    } catch (CallNotPermittedException cnpe) {
      log.debug(
          "Telemetry not set: {} circuit breaker tripped - {}",
          TELEMETRY_REGISTRY_NAME,
          cnpe.getMessage());
    } catch (Exception e) {
      log.debug("Could not send Telemetry event {}", event, e);
    }
  }

  private Optional<List<Stage>> toStages(Holder.Stage stage) {
    // Only interested in user-configured stages.
    if (stage.isSyntheticStage()) {
      log.debug("Discarding synthetic stage");
      return Optional.empty();
    }

    Status stageStatus =
        Status.valueOf(parseEnum(Status.getDescriptor(), stage.getStatus().toUpperCase()));
    Stage.Builder stageBuilder = Stage.newBuilder().setType(stage.getType()).setStatus(stageStatus);

    List<Stage> returnList = new ArrayList<>();
    String cloudProvider = stage.getContext().getCloudProvider();
    if (StringUtils.isNotEmpty(cloudProvider)) {
      stageBuilder.setCloudProvider(toCloudProvider(cloudProvider));
      returnList.add(stageBuilder.build());
    } else if (StringUtils.isNotEmpty(stage.getContext().getNewState().getCloudProviders())) {
      // Create and Update Application operations can specify multiple cloud providers in 1
      // operation.
      String[] cloudProviders = stage.getContext().getNewState().getCloudProviders().split(",");
      for (String cp : cloudProviders) {
        returnList.add(stageBuilder.clone().setCloudProvider(toCloudProvider(cp)).build());
      }
    } else {
      returnList.add(stageBuilder.build());
    }

    return Optional.of(returnList);
  }

  private CloudProvider toCloudProvider(String cloudProvider) {
    CloudProvider.ID cloudProviderId =
        CloudProvider.ID.valueOf(parseEnum(ID.getDescriptor(), cloudProvider.toUpperCase()));
    // TODO(ttomsu): Figure out how to detect Kubernetes "flavor" - i.e. GKE, EKS, vanilla, etc.
    return CloudProvider.newBuilder().setId(cloudProviderId).build();
  }

  private String hash(String clearText) {
    return hash(clearText, "");
  }

  private String hash(String clearText, String salt) {
    return Hashing.sha256().hashString(clearText + salt, StandardCharsets.UTF_8).toString();
  }

  private static EnumValueDescriptor parseEnum(EnumDescriptor ed, String value) {
    EnumValueDescriptor evd = ed.findValueByName(value);
    if (evd == null) {
      return ed.getValues().get(0); // Default to first if unrecognized, which should be UNKNOWN.
    }
    return evd;
  }

  static class TypedJsonString extends TypedString {
    TypedJsonString(String body) {
      super(body);
    }

    @Override
    public String mimeType() {
      return "application/json";
    }

    @Override
    public String toString() {
      return new String(getBytes(), StandardCharsets.UTF_8);
    }
  }

  // Arbitrary container for TelemetryEventListener structured data, so there aren't huge
  // fully-qualified names for the equivalent classes in the kork.proto package all over the code.
  public static class Holder {

    @Getter
    @Builder
    @JsonDeserialize(builder = Content.ContentBuilder.class)
    public static class Content {
      @Builder.Default private final Execution execution = Execution.builder().build();

      @JsonPOJOBuilder(withPrefix = "")
      public static class ContentBuilder {}
    }

    @Getter
    @Builder
    @JsonDeserialize(builder = Execution.ExecutionBuilder.class)
    public static class Execution {
      @Builder.Default private final String id = "";
      @Builder.Default private final String type = "UNKNOWN";
      @Builder.Default private final String status = "UNKNOWN";
      @Builder.Default private final Trigger trigger = Trigger.builder().build();
      @Builder.Default private final List<Stage> stages = new ArrayList<>();

      @JsonPOJOBuilder(withPrefix = "")
      public static class ExecutionBuilder {}
    }

    @Getter
    @Builder
    @JsonDeserialize(builder = Trigger.TriggerBuilder.class)
    public static class Trigger {

      @Builder.Default private final String type = "UNKNOWN";

      @JsonPOJOBuilder(withPrefix = "")
      public static class TriggerBuilder {}
    }

    @Getter
    @Builder
    @JsonDeserialize(builder = Stage.StageBuilder.class)
    public static class Stage {
      @Builder.Default private final String status = "UNKNOWN";
      @Builder.Default private final String type = "UNKNOWN";
      @Builder.Default private final Context context = Context.builder().build();
      private final String syntheticStageOwner;

      public boolean isSyntheticStage() {
        return StringUtils.isNotEmpty(syntheticStageOwner);
      }

      @JsonPOJOBuilder(withPrefix = "")
      public static class StageBuilder {}
    }

    @Getter
    @Builder
    @JsonDeserialize(builder = Context.ContextBuilder.class)
    public static class Context {
      private final String cloudProvider;
      // Only used during upsert of application
      @Builder.Default private final NewState newState = NewState.builder().build();

      @JsonPOJOBuilder(withPrefix = "")
      public static class ContextBuilder {}
    }

    @Getter
    @Builder
    @JsonDeserialize(builder = NewState.NewStateBuilder.class)
    public static class NewState {
      private final String cloudProviders;

      @JsonPOJOBuilder(withPrefix = "")
      public static class NewStateBuilder {}
    }
  }
}
