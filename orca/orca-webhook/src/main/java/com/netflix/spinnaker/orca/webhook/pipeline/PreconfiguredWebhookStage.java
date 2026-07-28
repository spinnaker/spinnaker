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

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties.PreconfiguredWebhook;
import com.netflix.spinnaker.orca.webhook.exception.PreconfiguredWebhookNotFoundException;
import com.netflix.spinnaker.orca.webhook.exception.PreconfiguredWebhookUnauthorizedException;
import com.netflix.spinnaker.orca.webhook.service.WebhookService;
import com.netflix.spinnaker.orca.webhook.tasks.MonitorWebhookTask;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.token.SpinnakerTokenClaims;
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier;
import com.netflix.spinnaker.security.token.TokenValidationException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PreconfiguredWebhookStage extends WebhookStage {

  private static final Set<String> IGNORE_FIELDS =
      Set.of("props", "enabled", "label", "description", "type", "parameters", "sensitiveHeaders");
  private static List<Field> ALL_FIELDS =
      Arrays.stream(PreconfiguredWebhook.class.getDeclaredFields())
          .filter(f -> !f.isSynthetic())
          .filter(f -> !IGNORE_FIELDS.contains(f.getName()))
          .collect(Collectors.toList());

  private final WebhookService webhookService;
  private final SpinnakerTokenVerifier tokenVerifier;

  @Autowired
  PreconfiguredWebhookStage(
      WebhookService webhookService,
      Optional<SpinnakerTokenVerifier> tokenVerifier,
      MonitorWebhookTask monitorWebhookTask,
      WebhookProperties webhookProperties) {
    super(monitorWebhookTask, webhookProperties);

    this.webhookService = webhookService;
    this.tokenVerifier = tokenVerifier.orElse(null);
  }

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    var preconfiguredWebhook =
        webhookService
            .findPreconfiguredWebhook(stage.getType())
            .orElseThrow(() -> new PreconfiguredWebhookNotFoundException(stage.getType()));

    var permissions = preconfiguredWebhook.getPermissions();
    if (permissions != null && !permissions.isEmpty()) {
      String user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");
      // Role-only decision sourced from the verified identity token (no remote lookup). When no
      // verified token is available (permissive rollout), authorize rather than fail closed.
      Optional<SpinnakerTokenClaims> claims = resolveVerifiedClaims();
      if (claims.isPresent()) {
        SpinnakerTokenClaims tokenClaims = claims.get();
        Set<String> roleNames = new HashSet<>(tokenClaims.getRoles());
        boolean isAllowed =
            tokenClaims.isAdmin() || preconfiguredWebhook.isAllowed("WRITE", roleNames);
        if (!isAllowed) {
          throw new PreconfiguredWebhookUnauthorizedException(user, stage.getType());
        }
      }
    }

    overrideIfNotSetInContextAndOverrideDefault(stage.getContext(), preconfiguredWebhook);
    super.taskGraph(stage, builder);
  }

  private Optional<SpinnakerTokenClaims> resolveVerifiedClaims() {
    if (tokenVerifier == null) {
      return Optional.empty();
    }
    String token = AuthenticatedRequest.get(Header.IDENTITY_TOKEN).orElse(null);
    if (StringUtils.isBlank(token)) {
      return Optional.empty();
    }
    try {
      return Optional.of(tokenVerifier.verify(token));
    } catch (TokenValidationException e) {
      return Optional.empty();
    }
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
