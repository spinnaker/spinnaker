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

package com.netflix.spinnaker.echo.model.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class AbstractOCIRegistryEventTest {

  @Test
  public void testDockerEventType() {
    DockerEvent event = new DockerEvent();
    assertEquals(DockerEvent.TYPE, event.getEventType());
    assertEquals("DOCKER", event.getEventType());
  }

  @Test
  public void testHelmOciEventType() {
    HelmOciEvent event = new HelmOciEvent();
    assertEquals(HelmOciEvent.TYPE, event.getEventType());
    assertEquals("HELM/OCI", event.getEventType());
  }

  @Test
  public void testDockerEventContent() {
    DockerEvent event = new DockerEvent();

    // Create and set content
    AbstractOCIRegistryEvent.Content content = new AbstractOCIRegistryEvent.Content();
    content.setAccount("testAccount");
    content.setRegistry("testRegistry");
    content.setRepository("testRepository");
    content.setTag("testTag");
    content.setDigest("testDigest");

    event.setContent(content);

    // Verify content is accessible and correct
    assertNotNull(event.getContent());
    assertEquals("testAccount", event.getContent().getAccount());
    assertEquals("testRegistry", event.getContent().getRegistry());
    assertEquals("testRepository", event.getContent().getRepository());
    assertEquals("testTag", event.getContent().getTag());
    assertEquals("testDigest", event.getContent().getDigest());
  }

  @Test
  public void testHelmOciEventContent() {
    HelmOciEvent event = new HelmOciEvent();

    // Create and set content
    AbstractOCIRegistryEvent.Content content = new AbstractOCIRegistryEvent.Content();
    content.setAccount("testAccount");
    content.setRegistry("testRegistry");
    content.setRepository("testRepository");
    content.setTag("testTag");
    content.setDigest("testDigest");

    event.setContent(content);

    // Verify content is accessible and correct
    assertNotNull(event.getContent());
    assertEquals("testAccount", event.getContent().getAccount());
    assertEquals("testRegistry", event.getContent().getRegistry());
    assertEquals("testRepository", event.getContent().getRepository());
    assertEquals("testTag", event.getContent().getTag());
    assertEquals("testDigest", event.getContent().getDigest());
  }
}
