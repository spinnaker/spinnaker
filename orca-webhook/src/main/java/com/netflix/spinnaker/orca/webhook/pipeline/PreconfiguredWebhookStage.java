/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.pipeline;

import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties.PreconfiguredWebhook;
import com.netflix.spinnaker.orca.webhook.exception.PreconfiguredWebhookNotFoundException;
import com.netflix.spinnaker.orca.webhook.exception.PreconfiguredWebhookUnauthorizedException;
import com.netflix.spinnaker.orca.webhook.service.WebhookService;
import com.netflix.spinnaker.orca.webhook.tasks.MonitorWebhookTask;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PreconfiguredWebhookStage extends WebhookStage {

  private static final Set<String> IGNORE_FIELDS =
      Set.of("props", "enabled", "label", "description", "type", "parameters");
  private static List<Field> ALL_FIELDS =
      Arrays.stream(PreconfiguredWebhook.class.getDeclaredFields())
          .filter(f -> !f.isSynthetic())
          .filter(f -> !IGNORE_FIELDS.contains(f.getName()))
          .collect(Collectors.toList());

  private final FiatService fiatService;
  private final WebhookService webhookService;

  @Autowired
  PreconfiguredWebhookStage(
      WebhookService webhookService,
      FiatService fiatService,
      MonitorWebhookTask monitorWebhookTask) {
    super(monitorWebhookTask);

    this.webhookService = webhookService;
    this.fiatService = fiatService;
  }

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    var preconfiguredWebhook =
        webhookService.getPreconfiguredWebhooks().stream()
            .filter(webhook -> Objects.equals(stage.getType(), webhook.getType()))
            .findFirst()
            .orElseThrow(() -> new PreconfiguredWebhookNotFoundException(stage.getType()));

    var permissions = preconfiguredWebhook.getPermissions();
    if (permissions != null && !permissions.isEmpty()) {
      String user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");
      var userPermission = fiatService.getUserPermission(user);

      boolean isAllowed = preconfiguredWebhook.isAllowed("WRITE", userPermission.getRoles());
      if (!isAllowed) {
        throw new PreconfiguredWebhookUnauthorizedException(user, stage.getType());
      }
    }

    overrideIfNotSetInContextAndOverrideDefault(stage.getContext(), preconfiguredWebhook);
    super.taskGraph(stage, builder);
  }

  /** Mutates the context map. */
  private static void overrideIfNotSetInContextAndOverrideDefault(
      Map<String, Object> context, PreconfiguredWebhook preconfiguredWebhook) {
    ALL_FIELDS.forEach(
        it -> {
          try {
            if (context.get(it.getName()) == null || it.get(preconfiguredWebhook) != null) {
              context.put(it.getName(), it.get(preconfiguredWebhook));
            }
          } catch (IllegalAccessException e) {
            throw new SystemException(
                String.format("unexpected reflection issue for field '%s'", it.getName()), e);
          }
        });
  }
}
