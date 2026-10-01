/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.handler;

import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties.CustomResourceStatus;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest.Status;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Reports the health of resources that have no dedicated handler (custom resources, and any other
 * kind Spinnaker doesn't model), following the conventions of {@code sigs.k8s.io/cli-utils}
 * kstatus. The first rule that applies wins:
 *
 * <ol>
 *   <li>no {@code status} block: stable (resources without a controller, and config-only kinds);
 *   <li>{@code metadata.generation} differs from a numeric {@code status.observedGeneration}:
 *       unstable;
 *   <li>{@code Stalled=True}: failed;
 *   <li>{@code Reconciling=True}: unstable;
 *   <li>{@code Ready}: {@code True} is stable (unless the condition's own observedGeneration is
 *       older than the object's), {@code False} or {@code Unknown} is unstable;
 *   <li>{@code status.phase}: {@code Failed}/{@code Degraded} is failed, {@code Pending}/{@code
 *       Progressing} is unstable, {@code Paused} is paused, with {@code status.message} as the
 *       message when there is one;
 *   <li>otherwise stable.
 * </ol>
 *
 * <p>Evaluation is controlled by {@code kubernetes.customResourceStatus.enabled}; when it's off
 * every resource is reported as stable, as before.
 */
@Component
public class CustomResourceStatusEvaluator {
  // Control characters, plus the bidirectional overrides and isolates that can reorder how text
  // is displayed.
  private static final Pattern UNSAFE_CHARACTERS =
      Pattern.compile("[\\p{Cc}\\u202A-\\u202E\\u2066-\\u2069]+");
  private static final Pattern INTEGER = Pattern.compile("-?\\d+");

  private final KubernetesConfigurationProperties configurationProperties;

  public CustomResourceStatusEvaluator(KubernetesConfigurationProperties configurationProperties) {
    this.configurationProperties = configurationProperties;
  }

  /** An evaluator that reports every resource as stable. */
  public static CustomResourceStatusEvaluator disabled() {
    return new CustomResourceStatusEvaluator(new KubernetesConfigurationProperties());
  }

  public Status status(KubernetesManifest manifest) {
    CustomResourceStatus config = configurationProperties.getCustomResourceStatus();
    if (config == null || !config.isEnabled()) {
      return Status.defaultStatus();
    }
    return evaluate(manifest, config.getMaxMessageLength());
  }

  private static Status evaluate(KubernetesManifest manifest, int maxMessageLength) {
    if (!(manifest.getStatus() instanceof Map) || ((Map<?, ?>) manifest.getStatus()).isEmpty()) {
      return Status.defaultStatus();
    }
    Map<?, ?> status = (Map<?, ?>) manifest.getStatus();

    Optional<Long> generation = generation(manifest);
    Optional<Long> observedGeneration = asLong(status.get("observedGeneration"));
    if (generation.isPresent()
        && observedGeneration.isPresent()
        && !generation.get().equals(observedGeneration.get())) {
      return Status.defaultStatus().unstable(UnstableReason.OLD_GENERATION.getMessage());
    }

    List<Map<?, ?>> conditions = conditions(status);

    Optional<Map<?, ?>> stalled = condition(conditions, "Stalled");
    if (stalled.isPresent() && isTrue(stalled.get())) {
      String message = message(stalled.get(), "Stalled", maxMessageLength);
      return Status.defaultStatus().unstable(message).failed(message);
    }

    Optional<Map<?, ?>> reconciling = condition(conditions, "Reconciling");
    if (reconciling.isPresent() && isTrue(reconciling.get())) {
      return Status.defaultStatus()
          .unstable(message(reconciling.get(), "Reconciling", maxMessageLength));
    }

    Optional<Map<?, ?>> ready = condition(conditions, "Ready");
    if (ready.isPresent()) {
      if (!isTrue(ready.get())) {
        return Status.defaultStatus().unstable(message(ready.get(), "Ready", maxMessageLength));
      }
      Optional<Long> readyGeneration = asLong(ready.get().get("observedGeneration"));
      if (generation.isPresent()
          && readyGeneration.isPresent()
          && readyGeneration.get() < generation.get()) {
        return Status.defaultStatus().unstable(UnstableReason.OLD_GENERATION.getMessage());
      }
      return Status.defaultStatus();
    }

    if (status.get("phase") instanceof String) {
      String phase = (String) status.get("phase");
      Object statusMessage = status.get("message");
      String message =
          statusMessage instanceof String && !((String) statusMessage).isBlank()
              ? clean((String) statusMessage, maxMessageLength)
              : clean("Phase is " + phase, maxMessageLength);
      switch (phase.toLowerCase()) {
        case "failed":
        case "degraded":
          return Status.defaultStatus().unstable(message).failed(message);
        case "pending":
        case "progressing":
          return Status.defaultStatus().unstable(message);
        case "paused":
          return Status.defaultStatus().paused(message);
        default:
          break;
      }
    }

    return Status.defaultStatus();
  }

  private static Optional<Long> generation(KubernetesManifest manifest) {
    Object metadata = manifest.get("metadata");
    return metadata instanceof Map
        ? asLong(((Map<?, ?>) metadata).get("generation"))
        : Optional.empty();
  }

  /**
   * Generations are integers, but some CRDs declare {@code observedGeneration} as a string (Argo
   * Rollouts, for example). Anything that isn't an integer is ignored rather than treated as a
   * mismatch, which would leave the resource unstable forever.
   */
  private static Optional<Long> asLong(Object value) {
    if (value instanceof Integer || value instanceof Long) {
      return Optional.of(((Number) value).longValue());
    }
    if (value instanceof String && INTEGER.matcher((String) value).matches()) {
      try {
        return Optional.of(Long.parseLong((String) value));
      } catch (NumberFormatException e) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private static List<Map<?, ?>> conditions(Map<?, ?> status) {
    Object conditions = status.get("conditions");
    if (!(conditions instanceof List)) {
      return List.of();
    }
    return ((List<?>) conditions)
        .stream()
            .filter(Map.class::isInstance)
            .map(c -> (Map<?, ?>) c)
            .collect(Collectors.toList());
  }

  private static Optional<Map<?, ?>> condition(List<Map<?, ?>> conditions, String type) {
    return conditions.stream()
        .filter(c -> c.get("type") instanceof String)
        .filter(c -> type.equalsIgnoreCase((String) c.get("type")))
        .findFirst();
  }

  private static boolean isTrue(Map<?, ?> condition) {
    return "true".equalsIgnoreCase(String.valueOf(condition.get("status")));
  }

  /** The condition's message, falling back to its reason and then a description of it. */
  private static String message(Map<?, ?> condition, String type, int maxMessageLength) {
    for (String key : List.of("message", "reason")) {
      Object value = condition.get(key);
      if (value instanceof String && !((String) value).isBlank()) {
        return clean((String) value, maxMessageLength);
      }
    }
    return clean(type + " is " + condition.get("status"), maxMessageLength);
  }

  /**
   * Status text can be written by any controller, so drop characters that could disrupt how it's
   * displayed and cap its length.
   */
  static String clean(String text, int maxMessageLength) {
    String cleaned = UNSAFE_CHARACTERS.matcher(text).replaceAll(" ").strip();
    if (maxMessageLength <= 0 || cleaned.codePointCount(0, cleaned.length()) <= maxMessageLength) {
      return cleaned;
    }
    if (maxMessageLength == 1) {
      return "…";
    }
    return cleaned.substring(0, cleaned.offsetByCodePoints(0, maxMessageLength - 1)) + "…";
  }
}
