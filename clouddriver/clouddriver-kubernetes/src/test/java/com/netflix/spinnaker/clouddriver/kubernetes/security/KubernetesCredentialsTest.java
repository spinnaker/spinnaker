/*
 * Copyright 2020 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonSyntaxException;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.kubernetes.config.CustomKubernetesResource;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.AccountResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.GlobalResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest;
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesManifestNamer;
import com.netflix.spinnaker.clouddriver.kubernetes.names.KubernetesNamerRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.CustomResourceStatusEvaluator;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesUnregisteredCustomResourceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.op.job.KubectlJobExecutor.KubectlException;
import com.netflix.spinnaker.clouddriver.kubernetes.op.job.KubectlJobExecutor.KubectlNotFoundException;
import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;
import io.kubernetes.client.util.Yaml;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

final class KubernetesCredentialsTest {
  private static final String ACCOUNT_NAME = "my-account";
  private static final String DEPLOYMENT_NAME = "my-deployment";
  private static final String NAMESPACE = "my-namespace";
  private final String OP_NAME = "KubernetesCredentialsTest";
  private final Task task = new DefaultTask("task-id");

  private KubernetesCredentials getCredentials(Registry registry, KubectlJobExecutor jobExecutor) {
    ManagedAccount managedAccount = new ManagedAccount();
    managedAccount.setName("my-account");
    return getCredentials(
        registry,
        jobExecutor,
        managedAccount,
        CustomResourceStatusEvaluator.disabled(),
        new GlobalResourcePropertyRegistry(
            ImmutableList.of(),
            new KubernetesUnregisteredCustomResourceHandler(
                CustomResourceStatusEvaluator.disabled())));
  }

  private KubernetesCredentials getCredentials(
      Registry registry,
      KubectlJobExecutor jobExecutor,
      ManagedAccount managedAccount,
      CustomResourceStatusEvaluator statusEvaluator,
      GlobalResourcePropertyRegistry globalResourcePropertyRegistry) {
    KubernetesCredentials.Factory factory =
        new KubernetesCredentials.Factory(
            registry,
            new KubernetesNamerRegistry(ImmutableList.of(new KubernetesManifestNamer())),
            jobExecutor,
            new ConfigFileService(new CloudConfigResourceService()),
            new AccountResourcePropertyRegistry.Factory(
                new GlobalResourcePropertyRegistry(
                    ImmutableList.of(),
                    new KubernetesUnregisteredCustomResourceHandler(
                        CustomResourceStatusEvaluator.disabled()))),
            new KubernetesKindRegistry.Factory(
                new GlobalKubernetesKindRegistry(ImmutableList.of())),
            new KubernetesSpinnakerKindMap(ImmutableList.of()),
            globalResourcePropertyRegistry,
            statusEvaluator);
    return factory.build(managedAccount);
  }

  private static final KubernetesKind WIDGET = KubernetesKind.fromString("Widget.example.com");

  private static CustomResourceStatusEvaluator enabledStatusEvaluator() {
    KubernetesConfigurationProperties properties = new KubernetesConfigurationProperties();
    properties.getCustomResourceStatus().setEnabled(true);
    return new CustomResourceStatusEvaluator(properties);
  }

  private static KubernetesManifest yamlManifest(String yaml) {
    return new ObjectMapper()
        .convertValue(Yaml.getSnakeYaml(null).load(yaml), KubernetesManifest.class);
  }

  private static KubernetesManifest stalledWidget() {
    return yamlManifest(
        "apiVersion: example.com/v1\n"
            + "kind: Widget\n"
            + "metadata: {name: my-widget, namespace: default}\n"
            + "status:\n"
            + "  conditions: [{type: Stalled, status: \"True\", message: invalid spec}]\n");
  }

  @Test
  void configuredCustomResourcesUseTheStatusEvaluator() {
    CustomKubernetesResource widget = new CustomKubernetesResource();
    widget.setKubernetesKind(WIDGET.toString());
    ManagedAccount managedAccount = new ManagedAccount();
    managedAccount.setName(ACCOUNT_NAME);
    managedAccount.setCustomResources(ImmutableList.of(widget));

    KubernetesCredentials credentials =
        getCredentials(
            new NoopRegistry(),
            mock(KubectlJobExecutor.class),
            managedAccount,
            enabledStatusEvaluator(),
            new GlobalResourcePropertyRegistry(
                ImmutableList.of(),
                new KubernetesUnregisteredCustomResourceHandler(
                    CustomResourceStatusEvaluator.disabled())));

    Manifest.Status status =
        credentials.getResourcePropertyRegistry().get(WIDGET).getHandler().status(stalledWidget());

    assertThat(status.getFailed().isState()).isTrue();
    assertThat(status.getFailed().getMessage()).isEqualTo("invalid spec");
  }

  @Test
  void discoveredCustomResourcesUseTheStatusEvaluator() {
    ManagedAccount managedAccount = new ManagedAccount();
    managedAccount.setName(ACCOUNT_NAME);
    // listing kinds explicitly makes them valid without probing the cluster for permissions
    managedAccount.setKinds(
        ImmutableList.of(KubernetesKind.CUSTOM_RESOURCE_DEFINITION.toString(), WIDGET.toString()));
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);
    GlobalResourcePropertyRegistry globalResourcePropertyRegistry =
        new GlobalResourcePropertyRegistry(
            ImmutableList.of(),
            new KubernetesUnregisteredCustomResourceHandler(
                CustomResourceStatusEvaluator.disabled()));
    KubernetesCredentials credentials =
        getCredentials(
            new NoopRegistry(),
            jobExecutor,
            managedAccount,
            enabledStatusEvaluator(),
            globalResourcePropertyRegistry);
    when(jobExecutor.list(eq(credentials), any(), any(), any()))
        .thenReturn(
            ImmutableList.of(
                yamlManifest(
                    "apiVersion: apiextensions.k8s.io/v1\n"
                        + "kind: CustomResourceDefinition\n"
                        + "metadata: {name: widgets.example.com}\n"
                        + "spec:\n"
                        + "  group: example.com\n"
                        + "  scope: Namespaced\n"
                        + "  names: {kind: Widget, plural: widgets, singular: widget}\n"
                        + "  versions: [{name: v1, served: true, storage: true}]\n")));

    assertThat(credentials.getCrds()).containsExactly(WIDGET);

    Manifest.Status status =
        globalResourcePropertyRegistry.get(WIDGET).getHandler().status(stalledWidget());

    assertThat(status.getFailed().isState()).isTrue();
    assertThat(status.getFailed().getMessage()).isEqualTo("invalid spec");
  }

  private KubernetesManifest getManifest() {
    KubernetesManifest manifest = new KubernetesManifest();
    manifest.put("metadata", new HashMap<>());
    manifest.setName(DEPLOYMENT_NAME);
    manifest.setNamespace(NAMESPACE);
    manifest.setKind(KubernetesKind.DEPLOYMENT);
    return manifest;
  }

  @Test
  void metricTagsForSuccessfulDeploy() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);
    Registry registry = new DefaultRegistry();
    KubernetesCredentials credentials = getCredentials(registry, jobExecutor);
    credentials.deploy(getManifest(), task, OP_NAME, new KubernetesSelectorList());

    ImmutableList<Timer> timers = registry.timers().collect(toImmutableList());
    assertThat(timers).hasSize(1);

    Timer timer = timers.get(0);
    assertThat(timer.id().name()).isEqualTo("kubernetes.api");
    assertThat(timer.id().tags())
        .containsExactlyInAnyOrder(
            Tag.of("account", ACCOUNT_NAME),
            Tag.of("action", "deploy"),
            Tag.of("kinds", KubernetesKind.DEPLOYMENT.toString()),
            Tag.of("namespace", NAMESPACE),
            Tag.of("success", "true"));
  }

  @Test
  void metricTagsForSuccessfulList() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);
    Registry registry = new DefaultRegistry();
    KubernetesCredentials credentials = getCredentials(registry, jobExecutor);
    credentials.list(
        ImmutableList.of(KubernetesKind.DEPLOYMENT, KubernetesKind.REPLICA_SET), NAMESPACE);
    ImmutableList<Timer> timers = registry.timers().collect(toImmutableList());
    assertThat(timers).hasSize(1);

    Timer timer = timers.get(0);
    assertThat(timer.id().name()).isEqualTo("kubernetes.api");

    assertThat(timer.id().tags())
        .containsExactlyInAnyOrder(
            Tag.of("account", ACCOUNT_NAME),
            Tag.of("action", "list"),
            Tag.of("kinds", "deployment,replicaSet"),
            Tag.of("namespace", NAMESPACE),
            Tag.of("success", "true"));
  }

  @Test
  void metricTagsForSuccessfulListNoNamespace() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);
    Registry registry = new DefaultRegistry();
    KubernetesCredentials credentials = getCredentials(registry, jobExecutor);
    credentials.list(ImmutableList.of(KubernetesKind.DEPLOYMENT, KubernetesKind.REPLICA_SET), null);
    ImmutableList<Timer> timers = registry.timers().collect(toImmutableList());
    assertThat(timers).hasSize(1);

    Timer timer = timers.get(0);
    assertThat(timer.id().name()).isEqualTo("kubernetes.api");

    assertThat(timer.id().tags()).contains(Tag.of("namespace", "none"));
  }

  @Test
  void metricTagsForSuccessfulListEmptyNamespace() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);
    Registry registry = new DefaultRegistry();
    KubernetesCredentials credentials = getCredentials(registry, jobExecutor);
    credentials.list(ImmutableList.of(KubernetesKind.DEPLOYMENT, KubernetesKind.REPLICA_SET), "");
    ImmutableList<Timer> timers = registry.timers().collect(toImmutableList());
    assertThat(timers).hasSize(1);

    Timer timer = timers.get(0);
    assertThat(timer.id().name()).isEqualTo("kubernetes.api");

    assertThat(timer.id().tags()).contains(Tag.of("namespace", "none"));
  }

  @Test
  void returnValueForSuccessfulList() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);
    Registry registry = new DefaultRegistry();
    KubernetesCredentials credentials = getCredentials(registry, jobExecutor);

    KubernetesManifest manifest = getManifest();
    when(jobExecutor.list(eq(credentials), any(), any(), any()))
        .thenReturn(ImmutableList.of(manifest));
    ImmutableList<KubernetesManifest> result =
        credentials.list(
            ImmutableList.of(KubernetesKind.DEPLOYMENT, KubernetesKind.REPLICA_SET), NAMESPACE);
    assertThat(result).containsExactly(manifest);
  }

  @Test
  void timeRecordedForSuccessfulList() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);

    ManualClock clock = new ManualClock();
    Registry registry = new DefaultRegistry(clock);
    KubernetesCredentials credentials = getCredentials(registry, jobExecutor);

    clock.setMonotonicTime(1000);
    when(jobExecutor.list(eq(credentials), any(), any(), any()))
        .then(
            call -> {
              clock.setMonotonicTime(1500);
              return ImmutableList.of();
            });
    credentials.list(
        ImmutableList.of(KubernetesKind.DEPLOYMENT, KubernetesKind.REPLICA_SET), NAMESPACE);

    ImmutableList<Timer> timers = registry.timers().collect(toImmutableList());
    assertThat(timers).hasSize(1);

    Timer timer = timers.get(0);
    assertThat(timer.id().name()).isEqualTo("kubernetes.api");
    assertThat(timer.totalTime()).isEqualTo(500);
  }

  @Test
  void metricTagsForListThrowingKubectlException() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);
    Registry registry = new DefaultRegistry();
    KubernetesCredentials credentials = getCredentials(registry, jobExecutor);

    when(jobExecutor.list(eq(credentials), any(), any(), any()))
        .thenThrow(
            new KubectlException(
                "Failed to parse kubectl output: failure", new JsonSyntaxException("failure")));

    assertThatThrownBy(
            () ->
                credentials.list(
                    ImmutableList.of(KubernetesKind.DEPLOYMENT, KubernetesKind.REPLICA_SET),
                    NAMESPACE))
        .isInstanceOf(KubectlException.class);
    ImmutableList<Timer> timers = registry.timers().collect(toImmutableList());
    assertThat(timers).hasSize(1);

    Timer timer = timers.get(0);
    assertThat(timer.id().name()).isEqualTo("kubernetes.api");

    assertThat(timer.id().tags())
        .containsExactlyInAnyOrder(
            Tag.of("account", ACCOUNT_NAME),
            Tag.of("action", "list"),
            Tag.of("kinds", "deployment,replicaSet"),
            Tag.of("namespace", NAMESPACE),
            Tag.of("success", "false"),
            Tag.of("reason", "KubectlException"));
  }

  @Test
  void propagatedExceptionForListThrowingKubectlException() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);
    Registry registry = new DefaultRegistry();
    KubernetesCredentials credentials = getCredentials(registry, jobExecutor);

    KubectlException exception =
        new KubectlException(
            "Failed to parse kubectl output: failure", new JsonSyntaxException("failure"));
    when(jobExecutor.list(eq(credentials), any(), any(), any())).thenThrow(exception);

    // Assert that a KubectlException is passed through without modification
    assertThatThrownBy(
            () ->
                credentials.list(
                    ImmutableList.of(KubernetesKind.DEPLOYMENT, KubernetesKind.REPLICA_SET),
                    NAMESPACE))
        .isEqualTo(exception);
  }

  @Test
  void timeRecordedForListThrowingKubectlException() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);

    ManualClock clock = new ManualClock();
    Registry registry = new DefaultRegistry(clock);
    KubernetesCredentials credentials = getCredentials(registry, jobExecutor);

    clock.setMonotonicTime(1000);
    when(jobExecutor.list(eq(credentials), any(), any(), any()))
        .then(
            call -> {
              clock.setMonotonicTime(1500);
              throw new KubectlException(
                  "Failed to parse kubectl output: failure", new JsonSyntaxException("failure"));
            });
    assertThatThrownBy(
            () ->
                credentials.list(
                    ImmutableList.of(KubernetesKind.DEPLOYMENT, KubernetesKind.REPLICA_SET),
                    NAMESPACE))
        .isInstanceOf(KubectlException.class);

    ImmutableList<Timer> timers = registry.timers().collect(toImmutableList());
    assertThat(timers).hasSize(1);

    Timer timer = timers.get(0);
    assertThat(timer.id().name()).isEqualTo("kubernetes.api");
    assertThat(timer.totalTime()).isEqualTo(500);
  }

  @Test
  void metricTagsForListThrowingOtherException() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);
    Registry registry = new DefaultRegistry();
    KubernetesCredentials credentials = getCredentials(registry, jobExecutor);

    when(jobExecutor.list(eq(credentials), any(), any(), any()))
        .thenThrow(new CustomException("Kubernetes error"));

    assertThatThrownBy(
            () ->
                credentials.list(
                    ImmutableList.of(KubernetesKind.DEPLOYMENT, KubernetesKind.REPLICA_SET),
                    NAMESPACE))
        .isInstanceOf(CustomException.class);
    ImmutableList<Timer> timers = registry.timers().collect(toImmutableList());
    assertThat(timers).hasSize(1);

    Timer timer = timers.get(0);
    assertThat(timer.id().name()).isEqualTo("kubernetes.api");

    assertThat(timer.id().tags())
        .containsExactlyInAnyOrder(
            Tag.of("account", ACCOUNT_NAME),
            Tag.of("action", "list"),
            Tag.of("kinds", "deployment,replicaSet"),
            Tag.of("namespace", NAMESPACE),
            Tag.of("success", "false"),
            Tag.of("reason", "CustomException"));
  }

  @Test
  void timeRecordedForListThrowingOtherException() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);

    ManualClock clock = new ManualClock();
    Registry registry = new DefaultRegistry(clock);
    KubernetesCredentials credentials = getCredentials(registry, jobExecutor);

    clock.setMonotonicTime(1000);
    when(jobExecutor.list(eq(credentials), any(), any(), any()))
        .then(
            call -> {
              clock.setMonotonicTime(1500);
              throw new CustomException("Kubernetes errror");
            });
    assertThatThrownBy(
            () ->
                credentials.list(
                    ImmutableList.of(KubernetesKind.DEPLOYMENT, KubernetesKind.REPLICA_SET),
                    NAMESPACE))
        .isInstanceOf(CustomException.class);

    ImmutableList<Timer> timers = registry.timers().collect(toImmutableList());
    assertThat(timers).hasSize(1);

    Timer timer = timers.get(0);
    assertThat(timer.id().name()).isEqualTo("kubernetes.api");
    assertThat(timer.totalTime()).isEqualTo(500);
  }

  @Test
  void propagatedExceptionForListThrowingOtherException() {
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);
    Registry registry = new DefaultRegistry();
    KubernetesCredentials credentials = getCredentials(registry, jobExecutor);

    Exception cause = new CustomException("Kubernetes error");
    when(jobExecutor.list(eq(credentials), any(), any(), any())).thenThrow(cause);

    // Assert that the source exception is wrapped in a KubectlException with details about the call
    // that failed
    assertThatThrownBy(
            () ->
                credentials.list(
                    ImmutableList.of(KubernetesKind.DEPLOYMENT, KubernetesKind.REPLICA_SET),
                    NAMESPACE))
        .isEqualTo(cause);
  }

  @Test
  void replaceWhenResourceExists() {
    KubernetesManifest manifest = getManifest();
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);
    KubernetesCredentials credentials = getCredentials(new NoopRegistry(), jobExecutor);
    KubernetesSelectorList selectorList = new KubernetesSelectorList();
    when(jobExecutor.create(credentials, manifest, task, OP_NAME, selectorList))
        .thenThrow(new KubectlException("Create failed: Error from server (AlreadyExists)"));
    when(jobExecutor.replace(credentials, manifest, task, OP_NAME)).thenReturn(manifest);

    KubernetesManifest result = credentials.createOrReplace(getManifest(), task, OP_NAME);
    assertThat(result).isEqualTo(manifest);
  }

  @Test
  void replaceWhenResourceDoesNotExist() {
    KubernetesManifest manifest = getManifest();
    KubectlJobExecutor jobExecutor = mock(KubectlJobExecutor.class);
    KubernetesCredentials credentials = getCredentials(new NoopRegistry(), jobExecutor);
    KubernetesSelectorList selectorList = new KubernetesSelectorList();
    when(jobExecutor.replace(credentials, manifest, task, OP_NAME))
        .thenThrow(new KubectlNotFoundException("Not found"));
    when(jobExecutor.create(credentials, manifest, task, OP_NAME, selectorList))
        .thenReturn(manifest);

    KubernetesManifest result = credentials.createOrReplace(getManifest(), task, OP_NAME);
    assertThat(result).isEqualTo(manifest);
  }

  // This is an error type that will only ever be thrown by stubs in this test; that way we can
  // assert that it is thrown and be sure that we aren't accidentally passing due to an unrelated
  // exception.
  private static class CustomException extends RuntimeException {
    CustomException(String message) {
      super(message);
    }
  }
}
