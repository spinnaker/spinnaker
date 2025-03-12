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
package com.netflix.spinnaker.clouddriver.orchestration.sagas;

import static java.lang.String.format;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.event.CompositeSpinnakerEvent;
import com.netflix.spinnaker.clouddriver.event.EventMetadata;
import com.netflix.spinnaker.clouddriver.event.SpinnakerEvent;
import com.netflix.spinnaker.clouddriver.saga.ManyCommands;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaIntegrationException;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Loads an Application from Front50, then calls the next SagaCommand.
 *
 * <p>This SagaAction can be reused across operations and even cloud providers.
 *
 * <pre>{@code
 * SagaFlow()
 *   .then(MyPredecessorAction.class)
 *   .then(LoadFront50App.class)
 *   .then(MyNextAction.class)
 *
 * class MyPredecessorAction : SagaAction<SagaCommand> {
 *   Result apply(SagaCommand command, Saga saga) {
 *     return Result(
 *       new LoadFront50AppCommand(
 *         "clouddriver",
 *         new MyNextActionCommand()
 *       )
 *     );
 *   }
 * }
 *
 * class MyNextAction : SagaAction<MyNextActionCommand> {
 *
 *   // MyNextAction implements an interface to mark it knows about this action
 *   static class MyNextActionCommand extends SagaCommand implements Front50AppAware {
 *
 *   }
 * }
 * }</pre>
 */
@Component
public class LoadFront50App implements SagaAction<LoadFront50App.LoadFront50AppCommand> {

  private static final Logger log = LoggerFactory.getLogger(LoadFront50App.class);

  private final Front50Service front50Service;
  private final ObjectMapper objectMapper;

  @Autowired
  public LoadFront50App(Front50Service front50Service, ObjectMapper objectMapper) {
    this.front50Service = front50Service;
    this.objectMapper = objectMapper;
  }

  /** Recursively applies the loaded front50 model to any {@code Front50AppAware} command. */
  private static SagaCommand applyFront50App(SagaCommand command, Front50App loadedApp) {
    if (ManyCommands.class.isAssignableFrom(command.getClass())) {
      for (SagaCommand c : ((ManyCommands) command).getCommands()) {
        applyFront50App(c, loadedApp);
      }
    }
    if (command instanceof Front50AppAware) {
      ((Front50AppAware) command).setFront50App(loadedApp);
    }
    return command;
  }

  @Nonnull
  @Override
  public Result apply(@Nonnull LoadFront50AppCommand command, @Nonnull Saga saga) {
    try {
      Map response = Retrofit2SyncCall.execute(front50Service.getApplication(command.getAppName()));
      try {
        return new Result(
            Optional.ofNullable(response)
                .map(it -> objectMapper.convertValue(it, Front50App.class))
                .map(f -> applyFront50App(command.nextCommand, f))
                .orElse(null),
            Collections.emptyList());
      } catch (IllegalArgumentException e) {
        log.error("Failed to convert front50 application to internal model", e);
        throw new SagaIntegrationException(
            "Failed to convert front50 application to internal model", e);
      }
    } catch (Exception e) {
      if (command.isAllowMissing()) {
        // It's ok to not load the front50 application
        return new Result(command.nextCommand, Collections.emptyList());
      }
      log.error("Failed to load front50 application attributes for {}", command.getAppName(), e);
      throw new SystemException(
          format("Failed to load front50 application: %s", command.getAppName()), e);
    }
  }

  /** Marks a SagaCommand as being aware of the result of the LoadFront50App SagaAction. */
  public interface Front50AppAware {
    void setFront50App(Front50App app);
  }

  @Builder(builderClassName = "LoadFront50AppCommandBuilder", toBuilder = true)
  @JsonDeserialize(builder = LoadFront50AppCommand.LoadFront50AppCommandBuilder.class)
  @JsonTypeName("loadFront50AppCommand")
  @Value
  public static class LoadFront50AppCommand implements SagaCommand, CompositeSpinnakerEvent {
    @Nonnull private String appName;
    @Nonnull private SagaCommand nextCommand;
    private boolean allowMissing;
    @NonFinal private EventMetadata metadata;

    @Override
    public void setMetadata(EventMetadata metadata) {
      this.metadata = metadata;
    }

    @Nonnull
    @Override
    public List<SpinnakerEvent> getComposedEvents() {
      return Collections.singletonList(nextCommand);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class LoadFront50AppCommandBuilder {}
  }

  @Value
  @AllArgsConstructor
  @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
  public static class Front50App {
    private String email;
    private boolean platformHealthOnly;
  }
}
