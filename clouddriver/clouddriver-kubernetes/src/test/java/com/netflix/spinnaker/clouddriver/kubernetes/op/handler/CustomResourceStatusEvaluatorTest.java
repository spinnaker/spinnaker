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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest.Status;
import io.kubernetes.client.util.Yaml;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

final class CustomResourceStatusEvaluatorTest {
  private static final String OLD_GENERATION = UnstableReason.OLD_GENERATION.getMessage();

  private static CustomResourceStatusEvaluator enabled() {
    return enabled(1024);
  }

  private static CustomResourceStatusEvaluator enabled(int maxMessageLength) {
    KubernetesConfigurationProperties properties = new KubernetesConfigurationProperties();
    properties.getCustomResourceStatus().setEnabled(true);
    properties.getCustomResourceStatus().setMaxMessageLength(maxMessageLength);
    return new CustomResourceStatusEvaluator(properties);
  }

  /** A custom resource at generation 3 with the given {@code status} block (YAML, indented). */
  private static KubernetesManifest withStatus(String status) {
    return manifest(
        "apiVersion: example.com/v1\n"
            + "kind: Widget\n"
            + "metadata:\n"
            + "  name: my-widget\n"
            + "  namespace: default\n"
            + "  generation: 3\n"
            + (status.isEmpty() ? "" : "status:\n" + status));
  }

  private static KubernetesManifest manifest(String yaml) {
    return new ObjectMapper()
        .convertValue(Yaml.getSnakeYaml(null).load(yaml), KubernetesManifest.class);
  }

  private static Status stable() {
    return Status.defaultStatus();
  }

  private static Status unstable(String message) {
    return Status.defaultStatus().unstable(message);
  }

  private static Status failed(String message) {
    return Status.defaultStatus().unstable(message).failed(message);
  }

  private static Status paused(String message) {
    return Status.defaultStatus().paused(message);
  }

  static Stream<Arguments> rules() {
    return Stream.of(
        // 1. no status block
        Arguments.of("no status block", "", stable()),
        Arguments.of("empty status block", "  {}\n", stable()),
        // 2. generation
        Arguments.of(
            "older observedGeneration", "  observedGeneration: 2\n", unstable(OLD_GENERATION)),
        Arguments.of("matching observedGeneration", "  observedGeneration: 3\n", stable()),
        Arguments.of(
            "numeric string observedGeneration that differs",
            "  observedGeneration: \"2\"\n",
            unstable(OLD_GENERATION)),
        Arguments.of(
            "non-numeric observedGeneration is ignored",
            "  observedGeneration: 7d9f5c8b6\n",
            stable()),
        // 3. Stalled
        Arguments.of(
            "Stalled=True",
            "  conditions: [{type: Stalled, status: \"True\", message: invalid spec},"
                + " {type: Ready, status: \"True\"}]\n",
            failed("invalid spec")),
        Arguments.of(
            "Stalled=False is ignored",
            "  conditions: [{type: Stalled, status: \"False\"}, {type: Ready, status: \"True\"}]\n",
            stable()),
        // 4. Reconciling
        Arguments.of(
            "Reconciling=True",
            "  conditions: [{type: Reconciling, status: \"True\", message: applying changes},"
                + " {type: Ready, status: \"True\"}]\n",
            unstable("applying changes")),
        // 5. Ready
        Arguments.of(
            "Ready=True",
            "  conditions:\n    - {type: Ready, status: \"True\", message: all good}\n",
            stable()),
        Arguments.of(
            "Ready=False",
            "  conditions:\n    - {type: Ready, status: \"False\", message: waiting on dependency}\n",
            unstable("waiting on dependency")),
        Arguments.of(
            "Ready=Unknown",
            "  conditions:\n    - {type: Ready, status: Unknown, reason: Pending}\n",
            unstable("Pending")),
        Arguments.of(
            "condition type and status are case-insensitive",
            "  conditions:\n    - {type: ready, status: \"false\", message: not yet}\n",
            unstable("not yet")),
        Arguments.of(
            "Ready=True from an older generation",
            "  conditions:\n    - {type: Ready, status: \"True\", observedGeneration: 2}\n",
            unstable(OLD_GENERATION)),
        Arguments.of(
            "Ready=True from the current generation",
            "  conditions:\n    - {type: Ready, status: \"True\", observedGeneration: 3}\n",
            stable()),
        Arguments.of(
            "Ready takes precedence over phase",
            "  phase: Failed\n  conditions:\n    - {type: Ready, status: \"True\"}\n",
            stable()),
        // 6. phase
        Arguments.of("phase Failed", "  phase: Failed\n", failed("Phase is Failed")),
        Arguments.of(
            "phase Degraded uses status.message",
            "  phase: Degraded\n  message: out of capacity\n",
            failed("out of capacity")),
        Arguments.of("phase Pending", "  phase: Pending\n", unstable("Phase is Pending")),
        Arguments.of(
            "phase Progressing", "  phase: progressing\n", unstable("Phase is progressing")),
        Arguments.of("phase Paused", "  phase: Paused\n", paused("Phase is Paused")),
        Arguments.of("unrecognised phase", "  phase: Healthy\n", stable()),
        // 7. otherwise
        Arguments.of(
            "unrelated conditions only",
            "  conditions:\n    - {type: Synced, status: \"True\"}\n",
            stable()),
        Arguments.of("malformed conditions are ignored", "  conditions: not-a-list\n", stable()),
        Arguments.of(
            "malformed condition entries are ignored",
            "  conditions:\n    - just-a-string\n    - {type: Ready, status: \"False\", message: x}\n",
            unstable("x")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rules")
  void appliesRulesInOrder(String description, String status, Status expected) {
    assertThat(enabled().status(withStatus(status))).isEqualTo(expected);
  }

  static Stream<Arguments> propertyKeys() {
    return Stream.of(
        Arguments.of(
            "kubernetes.customResourceStatus.enabled",
            "kubernetes.customResourceStatus.maxMessageLength"),
        Arguments.of(
            "kubernetes.custom-resource-status.enabled",
            "kubernetes.custom-resource-status.max-message-length"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("propertyKeys")
  void bindsFromConfiguration(String enabledKey, String maxLengthKey) {
    KubernetesConfigurationProperties properties =
        new Binder(
                new MapConfigurationPropertySource(Map.of(enabledKey, "true", maxLengthKey, "5")))
            .bind("kubernetes", KubernetesConfigurationProperties.class)
            .get();
    KubernetesManifest stalled =
        withStatus("  conditions: [{type: Stalled, status: \"True\", message: invalid spec}]\n");

    assertThat(new CustomResourceStatusEvaluator(properties).status(stalled))
        .isEqualTo(failed("inva…"));
  }

  @Test
  void reportsStableWhenDisabled() {
    KubernetesManifest stalled =
        withStatus("  conditions:\n    - {type: Stalled, status: \"True\", message: broken}\n");

    assertThat(CustomResourceStatusEvaluator.disabled().status(stalled)).isEqualTo(stable());
    assertThat(
            new CustomResourceStatusEvaluator(new KubernetesConfigurationProperties())
                .status(stalled))
        .isEqualTo(stable());
  }

  @Test
  void messageFallsBackToReasonThenDescription() {
    assertThat(
            enabled()
                .status(
                    withStatus(
                        "  conditions:\n    - {type: Ready, status: \"False\", reason: Creating}\n")))
        .isEqualTo(unstable("Creating"));
    assertThat(
            enabled().status(withStatus("  conditions:\n    - {type: Ready, status: \"False\"}\n")))
        .isEqualTo(unstable("Ready is False"));
  }

  @Test
  void messagesHaveControlAndBidiCharactersReplaced() {
    assertThat(CustomResourceStatusEvaluator.clean("line one\nline\ttwo\u0007", 1024))
        .isEqualTo("line one line two");
    String rightToLeftOverride = Character.toString(0x202E);
    String leftToRightIsolate = Character.toString(0x2066);
    assertThat(
            CustomResourceStatusEvaluator.clean(
                "abc" + rightToLeftOverride + "def" + leftToRightIsolate + "ghi", 1024))
        .isEqualTo("abc def ghi");
  }

  @Test
  void messagesAreTruncated() {
    assertThat(CustomResourceStatusEvaluator.clean("abcdefghij", 5)).isEqualTo("abcd…");
    assertThat(CustomResourceStatusEvaluator.clean("abcde", 5)).isEqualTo("abcde");
    assertThat(CustomResourceStatusEvaluator.clean("abcdefghij", 0)).isEqualTo("abcdefghij");
    // counts code points, so a surrogate pair isn't split
    assertThat(CustomResourceStatusEvaluator.clean("😀😀😀", 2)).isEqualTo("😀…");

    KubernetesManifest longMessage =
        withStatus(
            "  conditions:\n    - {type: Ready, status: \"False\", message: "
                + "x".repeat(50)
                + "}\n");
    assertThat(enabled(10).status(longMessage)).isEqualTo(unstable("xxxxxxxxx…"));
  }

  static Stream<Arguments> fixtures() {
    return Stream.of(
        Arguments.of("no-status.yml", stable()),
        Arguments.of(
            "certificate-issuing.yml", unstable("Issuing certificate as Secret does not exist")),
        Arguments.of("certificate-ready.yml", stable()),
        Arguments.of("crossplane-claim-creating.yml", unstable("Creating")),
        Arguments.of("argo-rollout-healthy.yml", stable()),
        Arguments.of(
            "argo-rollout-degraded.yml",
            failed(
                "ProgressDeadlineExceeded: ReplicaSet \"my-rollout-7d9f5c8b6\" has timed out"
                    + " progressing.")),
        Arguments.of("argo-rollout-paused.yml", paused("CanaryPauseStep")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void evaluatesRealCustomResources(String fixture, Status expected) {
    KubernetesManifest manifest = ManifestFetcher.getManifest("customresource/" + fixture);

    assertThat(enabled().status(manifest)).isEqualTo(expected);
  }
}
