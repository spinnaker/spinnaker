/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.AbstractOCIRegistryEvent;
import com.netflix.spinnaker.echo.model.trigger.DockerEvent;
import com.netflix.spinnaker.echo.model.trigger.HelmOciEvent;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AbstractOCIRegistryEventHandlerTest {

  private Registry registry;
  private ObjectMapper objectMapper;
  private FiatPermissionEvaluator fiatPermissionEvaluator;

  private TestDockerEventHandler dockerEventHandler;
  private TestHelmOciEventHandler helmOciEventHandler;

  @BeforeEach
  public void setUp() {
    registry = new NoopRegistry();
    objectMapper = new ObjectMapper();
    fiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
    when(fiatPermissionEvaluator.hasPermission(anyString(), any(), any(), any())).thenReturn(true);

    dockerEventHandler =
        new TestDockerEventHandler(registry, objectMapper, fiatPermissionEvaluator);
    helmOciEventHandler =
        new TestHelmOciEventHandler(registry, objectMapper, fiatPermissionEvaluator);
  }

  @Test
  public void testSupportedTriggerTypes() {
    assertEquals(Collections.singletonList("docker"), dockerEventHandler.supportedTriggerTypes());
    assertEquals(
        Collections.singletonList("helm/oci"), helmOciEventHandler.supportedTriggerTypes());
  }

  @Test
  public void testIsSuccessfulTriggerEvent() {
    // Create Docker event with tag
    DockerEvent dockerEvent = new DockerEvent();
    AbstractOCIRegistryEvent.Content dockerContent = new AbstractOCIRegistryEvent.Content();
    dockerContent.setTag("v1.0.0");
    dockerEvent.setContent(dockerContent);

    // Create Helm OCI event with tag
    HelmOciEvent helmOciEvent = new HelmOciEvent();
    AbstractOCIRegistryEvent.Content helmOciContent = new AbstractOCIRegistryEvent.Content();
    helmOciContent.setTag("v1.0.0");
    helmOciEvent.setContent(helmOciContent);

    // Create events with no tag
    DockerEvent emptyDockerEvent = new DockerEvent();
    AbstractOCIRegistryEvent.Content emptyDockerContent = new AbstractOCIRegistryEvent.Content();
    emptyDockerContent.setTag("");
    emptyDockerEvent.setContent(emptyDockerContent);

    HelmOciEvent emptyHelmOciEvent = new HelmOciEvent();
    AbstractOCIRegistryEvent.Content emptyHelmOciContent = new AbstractOCIRegistryEvent.Content();
    emptyHelmOciContent.setTag("");
    emptyHelmOciEvent.setContent(emptyHelmOciContent);

    // Test successful events
    assertTrue(dockerEventHandler.isSuccessfulTriggerEvent(dockerEvent));
    assertTrue(helmOciEventHandler.isSuccessfulTriggerEvent(helmOciEvent));

    // Test unsuccessful events
    assertFalse(dockerEventHandler.isSuccessfulTriggerEvent(emptyDockerEvent));
    assertFalse(helmOciEventHandler.isSuccessfulTriggerEvent(emptyHelmOciEvent));
  }

  @Test
  public void testGetArtifactsFromEvent() {
    // Create Docker event
    DockerEvent dockerEvent = new DockerEvent();
    AbstractOCIRegistryEvent.Content dockerContent = new AbstractOCIRegistryEvent.Content();
    dockerContent.setRegistry("docker.io");
    dockerContent.setRepository("library/nginx");
    dockerContent.setTag("1.19");
    dockerEvent.setContent(dockerContent);

    // Create Helm OCI event
    HelmOciEvent helmOciEvent = new HelmOciEvent();
    AbstractOCIRegistryEvent.Content helmOciContent = new AbstractOCIRegistryEvent.Content();
    helmOciContent.setRegistry("registry.example.com");
    helmOciContent.setRepository("charts/nginx");
    helmOciContent.setTag("1.0.0");
    helmOciEvent.setContent(helmOciContent);

    // Create triggers
    Trigger dockerTrigger = Trigger.builder().build();
    Trigger helmOciTrigger = Trigger.builder().build();

    // Get artifacts
    List<Artifact> dockerArtifacts =
        dockerEventHandler.getArtifactsFromEvent(dockerEvent, dockerTrigger);
    List<Artifact> helmOciArtifacts =
        helmOciEventHandler.getArtifactsFromEvent(helmOciEvent, helmOciTrigger);

    // Verify Docker artifact
    assertNotNull(dockerArtifacts);
    assertEquals(1, dockerArtifacts.size());
    Artifact dockerArtifact = dockerArtifacts.get(0);
    assertEquals("docker/image", dockerArtifact.getType());
    assertEquals("docker.io/library/nginx", dockerArtifact.getName());
    assertEquals("1.19", dockerArtifact.getVersion());
    assertEquals("docker.io/library/nginx:1.19", dockerArtifact.getReference());

    // Verify Helm OCI artifact
    assertNotNull(helmOciArtifacts);
    assertEquals(1, helmOciArtifacts.size());
    Artifact helmOciArtifact = helmOciArtifacts.get(0);
    assertEquals("helm/image", helmOciArtifact.getType());
    assertEquals("registry.example.com/charts/nginx", helmOciArtifact.getName());
    assertEquals("1.0.0", helmOciArtifact.getVersion());
    assertEquals("registry.example.com/charts/nginx:1.0.0", helmOciArtifact.getReference());
  }

  @Test
  public void testIsValidTrigger() {
    // Create valid triggers
    Trigger validDockerTrigger =
        Trigger.builder()
            .enabled(true)
            .type("docker")
            .account("account")
            .repository("repository")
            .build();

    Trigger validHelmOciTrigger =
        Trigger.builder()
            .enabled(true)
            .type("helm/oci")
            .account("account")
            .repository("repository")
            .build();

    // Create invalid triggers
    Trigger disabledTrigger =
        Trigger.builder()
            .enabled(false)
            .type("docker")
            .account("account")
            .repository("repository")
            .build();

    Trigger wrongTypeTrigger =
        Trigger.builder()
            .enabled(true)
            .type("wrong-type")
            .account("account")
            .repository("repository")
            .build();

    Trigger noAccountTrigger =
        Trigger.builder().enabled(true).type("docker").repository("repository").build();

    Trigger noRepositoryTrigger =
        Trigger.builder().enabled(true).type("docker").account("account").build();

    // Test valid triggers
    assertTrue(dockerEventHandler.isValidTrigger(validDockerTrigger));
    assertTrue(helmOciEventHandler.isValidTrigger(validHelmOciTrigger));

    // Test invalid triggers
    assertFalse(dockerEventHandler.isValidTrigger(disabledTrigger));
    assertFalse(dockerEventHandler.isValidTrigger(wrongTypeTrigger));
    assertFalse(dockerEventHandler.isValidTrigger(noAccountTrigger));
    assertFalse(dockerEventHandler.isValidTrigger(noRepositoryTrigger));

    assertFalse(helmOciEventHandler.isValidTrigger(disabledTrigger));
    assertFalse(helmOciEventHandler.isValidTrigger(wrongTypeTrigger));
    assertFalse(helmOciEventHandler.isValidTrigger(noAccountTrigger));
    assertFalse(helmOciEventHandler.isValidTrigger(noRepositoryTrigger));
  }

  /** Test implementation of AbstractOCIRegistryEventHandler for Docker events. */
  private static class TestDockerEventHandler extends AbstractOCIRegistryEventHandler<DockerEvent> {
    private static final List<String> SUPPORTED_TYPES = Collections.singletonList("docker");

    public TestDockerEventHandler(
        Registry registry,
        ObjectMapper objectMapper,
        FiatPermissionEvaluator fiatPermissionEvaluator) {
      super(registry, objectMapper, fiatPermissionEvaluator);
    }

    @Override
    public boolean handleEventType(String eventType) {
      return eventType.equalsIgnoreCase(DockerEvent.TYPE);
    }

    @Override
    public Class<DockerEvent> getEventType() {
      return DockerEvent.class;
    }

    @Override
    protected String getTriggerType() {
      return "docker";
    }

    @Override
    protected String getArtifactType() {
      return "docker/image";
    }

    @Override
    public List<String> supportedTriggerTypes() {
      return SUPPORTED_TYPES;
    }
  }

  /** Test implementation of AbstractOCIRegistryEventHandler for Helm OCI events. */
  private static class TestHelmOciEventHandler
      extends AbstractOCIRegistryEventHandler<HelmOciEvent> {
    private static final List<String> SUPPORTED_TYPES = Collections.singletonList("helm/oci");

    public TestHelmOciEventHandler(
        Registry registry,
        ObjectMapper objectMapper,
        FiatPermissionEvaluator fiatPermissionEvaluator) {
      super(registry, objectMapper, fiatPermissionEvaluator);
    }

    @Override
    public boolean handleEventType(String eventType) {
      return eventType.equalsIgnoreCase(HelmOciEvent.TYPE);
    }

    @Override
    public Class<HelmOciEvent> getEventType() {
      return HelmOciEvent.class;
    }

    @Override
    protected String getTriggerType() {
      return "helm/oci";
    }

    @Override
    protected String getArtifactType() {
      return "helm/image";
    }

    @Override
    public List<String> supportedTriggerTypes() {
      return SUPPORTED_TYPES;
    }
  }
}
