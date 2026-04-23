/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.kork.actuator.observability.newrelic;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.netflix.spinnaker.kork.actuator.observability.model.MetricsConfig;
import com.netflix.spinnaker.kork.actuator.observability.model.MetricsNewRelicConfig;
import com.netflix.spinnaker.kork.actuator.observability.model.ObservabilityConfigurationProperties;
import com.netflix.spinnaker.kork.actuator.observability.service.TagsService;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.newrelic.NewRelicRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class NewRelicRegistrySupplierTest {

  MetricsNewRelicConfig config;

  @Mock TagsService tagsService;
  @Mock HttpUrlConnectionSender sender;

  NewRelicRegistrySupplier sut;

  @Before
  public void before() throws IOException {
    initMocks(this);
    var observabilityConfigurationProperites = new ObservabilityConfigurationProperties();
    var metricsConfig = new MetricsConfig();
    observabilityConfigurationProperites.setMetrics(metricsConfig);
    config = MetricsNewRelicConfig.builder().apiKey("foo").build();
    metricsConfig.setNewrelic(config);
    sut = new NewRelicRegistrySupplier(observabilityConfigurationProperites, tagsService);
    when(sender.post(anyString())).thenCallRealMethod();
    when(sender.newRequest(anyString())).thenCallRealMethod();
    sut.sender = sender;
  }

  @Test
  public void test_that_get_returns_null_if_newrelic_is_not_enabled() {
    config.setEnabled(false);
    assertNull(sut.get());
  }

  @Test
  public void test_that_get_returns_a_newrelic_registry_if_newrelic_is_enabled() {
    config.setEnabled(true);
    var actual = sut.get();
    assertNotNull(actual);
  }

  @Test
  public void verifySendOfMetrics() throws Exception {
    config.setEnabled(true);
    config.setStepInSeconds(1);

    ArgumentCaptor<HttpSender.Request> captor = ArgumentCaptor.forClass(HttpSender.Request.class);
    when(sender.send(captor.capture())).thenReturn(new HttpSender.Response(202, "Success"));
    NewRelicRegistry registry = (NewRelicRegistry) sut.get().getMeterRegistry();
    registry.start(r -> new Thread(r));
    registry.counter("testCounter").increment();
    Thread.sleep(2 * 1000l);
    registry.stop();
    assertTrue(
        "Received " + decompressByteData(captor.getValue().getEntity()),
        decompressByteData(captor.getValue().getEntity()).contains("testCounter"));
  }

  @Test
  public void dpmGauge_hasDefaultTags_whenDefaultTagsNotDisabled() {
    config.setEnabled(true);
    config.getRegistry().setDefaultTagsDisabled(false);

    when(tagsService.getDefaultTags())
        .thenReturn(List.of(Tag.of("env", "test"), Tag.of("svc", "orca")));

    NewRelicRegistry registry = (NewRelicRegistry) sut.get().getMeterRegistry();

    Meter dpm =
        registry.getMeters().stream()
            .filter(m -> m.getId().getName().equals("metrics.dpm"))
            .findFirst()
            .orElse(null);

    assertNotNull("DPM gauge should be registered", dpm);
    assertEquals("test", dpm.getId().getTag("env"));
    assertEquals("orca", dpm.getId().getTag("svc"));
    registry.stop();
  }

  @Test
  public void dpmGauge_hasNoDefaultTags_whenDefaultTagsDisabled() {
    config.setEnabled(true);
    config.getRegistry().setDefaultTagsDisabled(true);

    when(tagsService.getDefaultTags())
        .thenReturn(List.of(Tag.of("env", "test"), Tag.of("svc", "orca")));

    NewRelicRegistry registry = (NewRelicRegistry) sut.get().getMeterRegistry();

    Meter dpm =
        registry.getMeters().stream()
            .filter(m -> m.getId().getName().equals("metrics.dpm"))
            .findFirst()
            .orElse(null);

    assertNotNull("DPM gauge should be registered", dpm);
    assertNull("No env tag expected when default tags are disabled", dpm.getId().getTag("env"));
    assertNull("No svc tag expected when default tags are disabled", dpm.getId().getTag("svc"));
    registry.stop();
  }

  private String decompressByteData(byte[] result) throws IOException {
    GZIPInputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(result));
    String results = new String(inputStream.readAllBytes());
    inputStream.close();
    return results;
  }
}
