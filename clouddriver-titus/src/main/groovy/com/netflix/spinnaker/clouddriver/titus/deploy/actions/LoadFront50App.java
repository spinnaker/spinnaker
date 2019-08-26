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
package com.netflix.spinnaker.clouddriver.titus.deploy.actions;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.saga.ManyCommands;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaIntegrationException;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
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

  @NotNull
  @Override
  public Result apply(@NotNull LoadFront50AppCommand command, @NotNull Saga saga) {
    try {
      Map response = front50Service.getApplication(command.getAppName());
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
      log.error("Failed to load front50 application attributes for {}", command.getAppName(), e);
      if (command.isAllowMissing()) {
        // It's ok to not load the front50 application
        return new Result(command.nextCommand, Collections.emptyList());
      }
      throw new SystemException(
          format("Failed to load front50 application: %s", command.getAppName()), e);
    }
  }

  @EqualsAndHashCode(callSuper = true)
  @Getter
  public static class LoadFront50AppCommand extends SagaCommand {
    @Nonnull private final String appName;
    @Nonnull private final SagaCommand nextCommand;
    @Nonnull private final boolean allowMissing;

    public LoadFront50AppCommand(
        @Nonnull String appName, @Nonnull SagaCommand nextCommand, boolean allowMissing) {
      super();
      this.appName = appName;
      this.nextCommand = nextCommand;
      this.allowMissing = allowMissing;
    }
  }

  /** Marks a SagaCommand as being aware of the result of the LoadFront50App SagaAction. */
  interface Front50AppAware {
    void setFront50App(Front50App app);
  }

  @Value
  @AllArgsConstructor
  @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
  public static class Front50App {
    private String email;
    private boolean platformHealthOnly;
  }
}
