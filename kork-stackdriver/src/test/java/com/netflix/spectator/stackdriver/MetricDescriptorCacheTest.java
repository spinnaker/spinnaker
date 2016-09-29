/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spectator.stackdriver;

import com.google.api.services.monitoring.v3.model.*;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Measurement;

import com.google.api.services.monitoring.v3.Monitoring;

import java.io.IOException;

import java.util.function.Predicate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MetricDescriptorCacheTest {
  static class TestableMetricDescriptorCache extends MetricDescriptorCache {
    public TestableMetricDescriptorCache(ConfigParams params) {
      super(params);
      addExtraTimeSeriesLabel(APPLICATION_LABEL, params.getApplicationName());
      addExtraTimeSeriesLabel(INSTANCE_LABEL, params.getInstanceId());
    }

    public void injectDescriptor(Id id, MetricDescriptor value) {
        injectDescriptor(idToDescriptorType(id), value);
    }

    public void injectDescriptor(String key, MetricDescriptor value) {
        knownDescriptors.put(key, value);
    }

    public MetricDescriptor peekDescriptor(Id id) {
      return knownDescriptors.get(idToDescriptorType(id));
    }
  };

  static class ReturnExecuteDescriptorArg implements Answer {
    private Monitoring.Projects.MetricDescriptors.Create mockCreateMethod;

    public ReturnExecuteDescriptorArg(
            Monitoring.Projects.MetricDescriptors.Create mockCreateMethod) {
      this.mockCreateMethod = mockCreateMethod;
    }

    public Object answer(InvocationOnMock invocation) {
      try {
        MetricDescriptor descArg
                = (MetricDescriptor) invocation.getArguments()[1];
        when(mockCreateMethod.execute()).thenReturn(descArg);
        return mockCreateMethod;
      } catch (IOException ioex) {
        return null;  // Not Reached
      }
    }
  };

  private long millis = 12345L;
  private Clock clock = new Clock() {
      public long wallTime() {
          return millis;
      }

      public long monotonicTime() {
          return millis;
      }
  };
  DefaultRegistry registry = new DefaultRegistry(clock);

  TestableMetricDescriptorCache cache;
  String projectName = "test-project";
  String applicationName = "test-application";

  Id idA = registry.createId("idA");
  Id idB = registry.createId("idB");
  Id idAXY = idA.withTag("tagA", "X").withTag("tagB", "Y");
  Id idAYX = idA.withTag("tagA", "Y").withTag("tagB", "X");
  Id idBXY = idB.withTag("tagA", "X").withTag("tagB", "Y");

  Predicate<Measurement> allowAll = new Predicate<Measurement>() {
    public boolean test(Measurement measurement) {
      return true;
    }
  };

  @Mock Monitoring monitoringApi;
  @Mock Monitoring.Projects projectsApi;
  @Mock Monitoring.Projects.MetricDescriptors descriptorsApi;

  ConfigParams.Builder config;

  MetricDescriptor descriptorA;
  MetricDescriptor descriptorB;

  Measurement meterMeasurement(Meter meter) {
    return meter.measure().iterator().next();
  }

  private MetricDescriptor makeDescriptor(
        Id id, List<String> tagNames, String kind) {
    MetricDescriptor descriptor = new MetricDescriptor();
    descriptor.setName(cache.idToDescriptorName(id));
    descriptor.setDisplayName(id.name());
    descriptor.setType(cache.idToDescriptorType(id));
    descriptor.setValueType("DOUBLE");
    descriptor.setMetricKind(kind);

    List<LabelDescriptor> labels = new ArrayList<LabelDescriptor>();
    LabelDescriptor labelDescriptor = new LabelDescriptor();
    labelDescriptor.setKey(MetricDescriptorCache.APPLICATION_LABEL);
    labelDescriptor.setValueType("STRING");
    labelDescriptor.setKey(MetricDescriptorCache.INSTANCE_LABEL);
    labelDescriptor.setValueType("STRING");
    labels.add(labelDescriptor);
    for (String key : tagNames) {
      labelDescriptor = new LabelDescriptor();
      labelDescriptor.setKey(key);
      labelDescriptor.setValueType("STRING");
      labels.add(labelDescriptor);
    }
    descriptor.setLabels(labels);
    return descriptor;
  }

  Set<String> getLabelKeys(Iterable<LabelDescriptor> labels) {
    Set<String> result = new HashSet<String>();
    for (LabelDescriptor label : labels) {
      result.add(label.getKey());
    }
    return result;
  }

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(monitoringApi.projects()).thenReturn(projectsApi);
    when(projectsApi.metricDescriptors()).thenReturn(descriptorsApi);

    config = new ConfigParams.Builder()
        .setStackdriverStub(monitoringApi)
        .setCustomTypeNamespace("TESTNAMESPACE")
        .setProjectName(projectName)
        .setApplicationName(applicationName)
        .setMeasurementFilter(allowAll);

    cache = new TestableMetricDescriptorCache(config.build());
    List<String> testTags = Arrays.asList("tagA", "tagB");
    descriptorA = makeDescriptor(idA, testTags, "GAUGE");
    descriptorB = makeDescriptor(idB, testTags, "CUMULATIVE");
  }

  @Test
  public void descriptorTypeAreCompliant() {
    Assert.assertEquals(
        "custom.googleapis.com/TESTNAMESPACE/" + applicationName + "/idA",
        cache.idToDescriptorType(idA));
  }

  @Test
  public void descriptorNamesAreCompliant() {
    // https://cloud.google.com/monitoring/api/ref_v3/rest/v3/projects.metricDescriptors
    Assert.assertEquals(
        "projects/test-project/metricDescriptors/" + cache.idToDescriptorType(idA),
        cache.idToDescriptorName(idA));
  }

  @Test
  public void testCreateDescriptorHelperCreateProperDescriptors()
          throws IOException {
    Meter counterA = registry.counter(idAXY);

    Monitoring.Projects.MetricDescriptors.Create mockCreateMethod
        = Mockito.mock(Monitoring.Projects.MetricDescriptors.Create.class);

    when(descriptorsApi.create(
            eq("projects/test-project"), any(MetricDescriptor.class)))
            .thenAnswer(new ReturnExecuteDescriptorArg(mockCreateMethod));
    cache.createDescriptorInServer(idAXY, registry, counterA);

    verify(mockCreateMethod, times(1)).execute();

    ArgumentCaptor<MetricDescriptor> captor
        = ArgumentCaptor.forClass(MetricDescriptor.class);
    verify(descriptorsApi, times(1)).create(eq("projects/test-project"),
                                               captor.capture());
    MetricDescriptor descriptor = captor.getValue();

    Assert.assertEquals(cache.idToDescriptorName(idA), descriptor.getName());
    Assert.assertEquals(cache.idToDescriptorType(idA), descriptor.getType());
    Assert.assertEquals("DOUBLE", descriptor.getValueType());
    Assert.assertEquals("CUMULATIVE", descriptor.getMetricKind());
    Assert.assertEquals(
        getLabelKeys(descriptor.getLabels()),
        new HashSet(Arrays.asList(
               MetricDescriptorCache.APPLICATION_LABEL,
               MetricDescriptorCache.INSTANCE_LABEL,
               "tagA", "tagB")));

    for (LabelDescriptor label : descriptor.getLabels()) {
        Assert.assertEquals("STRING", label.getValueType());
    }
  }

  @Test
  public void testCreateDescriptorHelperRespectsHints() throws IOException {
    cache.addCustomDescriptorHints(
        Arrays.asList(
            new MetricDescriptorCache.CustomDescriptorHint(
                idAXY.name(), Arrays.asList("tagA", "tagU"))));

    Meter counterA = registry.counter(idAXY);
    Meter counterB = registry.counter(idBXY);

    Monitoring.Projects.MetricDescriptors.Create mockCreateMethod
          = Mockito.mock(Monitoring.Projects.MetricDescriptors.Create.class);

    when(descriptorsApi.create(
            eq("projects/test-project"), any(MetricDescriptor.class)))
            .thenAnswer(new ReturnExecuteDescriptorArg(mockCreateMethod));
    cache.createDescriptorInServer(idAXY, registry, counterA);

    ArgumentCaptor<MetricDescriptor> captor
        = ArgumentCaptor.forClass(MetricDescriptor.class);
    verify(descriptorsApi, times(1)).create(eq("projects/test-project"),
                                               captor.capture());
    verify(mockCreateMethod, times(1)).execute();

    MetricDescriptor descriptor = captor.getValue();
    Assert.assertEquals(
        getLabelKeys(descriptor.getLabels()),
        new HashSet<String>(
                Arrays.asList(
                        MetricDescriptorCache.APPLICATION_LABEL,
                        MetricDescriptorCache.INSTANCE_LABEL,
                        "tagA", "tagB", "tagU")));
    reset(descriptorsApi);
    reset(mockCreateMethod);

    when(descriptorsApi.create(
            eq("projects/test-project"), any(MetricDescriptor.class)))
            .thenAnswer(new ReturnExecuteDescriptorArg(mockCreateMethod));
    cache.createDescriptorInServer(idBXY, registry, counterB);
    verify(mockCreateMethod, times(1)).execute();

    captor = ArgumentCaptor.forClass(MetricDescriptor.class);
    verify(descriptorsApi, times(1)).create(eq("projects/test-project"),
                                               captor.capture());
    descriptor = captor.getValue();

    Assert.assertEquals(
        getLabelKeys(descriptor.getLabels()),
        new HashSet<String>(
            Arrays.asList(MetricDescriptorCache.APPLICATION_LABEL,
                          MetricDescriptorCache.INSTANCE_LABEL,
                          "tagA", "tagB")));
  }

  @Test
  public void testCreateDescriptorHelperRedacts() throws IOException {
    cache.addCustomDescriptorHints(
        Arrays.asList(
            new MetricDescriptorCache.CustomDescriptorHint(
                    idAXY.name(), null, Arrays.asList("tagA", "tagU"))));

    Meter counterA = registry.counter(idAXY);
    Meter counterB = registry.counter(idBXY);

    Monitoring.Projects.MetricDescriptors.Create mockCreateMethod
          = Mockito.mock(Monitoring.Projects.MetricDescriptors.Create.class);

    when(descriptorsApi.create(
            eq("projects/test-project"), any(MetricDescriptor.class)))
            .thenAnswer(new ReturnExecuteDescriptorArg(mockCreateMethod));
    cache.createDescriptorInServer(idAXY, registry, counterA);

    ArgumentCaptor<MetricDescriptor> captor
        = ArgumentCaptor.forClass(MetricDescriptor.class);
    verify(descriptorsApi, times(1)).create(eq("projects/test-project"),
                                               captor.capture());
    verify(mockCreateMethod, times(1)).execute();

    MetricDescriptor descriptor = captor.getValue();
    Assert.assertEquals(
        getLabelKeys(descriptor.getLabels()),
        new HashSet<String>(
                Arrays.asList(
                        MetricDescriptorCache.APPLICATION_LABEL,
                        MetricDescriptorCache.INSTANCE_LABEL,
                        "tagB")));
  }


  @Test
  public void testEnsureDescriptorDescriptorIsAlreadyLoaded() {
    MetricDescriptor descriptor = new MetricDescriptor();
    Meter counterA = registry.counter(idAXY);
    cache.injectDescriptor(idA, descriptor);
    Assert.assertEquals(
        descriptor,
        cache.descriptorOrNull(
            registry, counterA, meterMeasurement(counterA)));
  }

  @Test
  public void testEnsureDescriptorWhenDescriptorIsNotAlreadyLoaded()
          throws IOException {
    Meter counterA = registry.counter(idAXY);

    Monitoring.Projects.MetricDescriptors.Create mockCreateMethod
          = Mockito.mock(Monitoring.Projects.MetricDescriptors.Create.class);

    cache.injectDescriptor(idB, new MetricDescriptor());
    when(descriptorsApi.create(
            eq("projects/test-project"), any(MetricDescriptor.class)))
            .thenAnswer(new ReturnExecuteDescriptorArg(mockCreateMethod));

    cache.createDescriptorInServer(idA, registry, counterA);
    verify(mockCreateMethod, times(1)).execute();

    ArgumentCaptor<MetricDescriptor> captor
          = ArgumentCaptor.forClass(MetricDescriptor.class);
    verify(descriptorsApi, times(1)).create(eq("projects/test-project"),
                                               captor.capture());
    MetricDescriptor descriptor = captor.getValue();
    Assert.assertTrue(descriptor != null);

    // Since we should have a pending request, future creates wont do anything
    reset(descriptorsApi);
    reset(mockCreateMethod);
    cache.createDescriptorInServer(idA, registry, counterA);
    verify(descriptorsApi, times(0)).create(
        any(String.class), any(MetricDescriptor.class));
  }

  @Test
  public void testEnsureDescriptorDescriptorIsAlreadyRegistered()
        throws IOException {
    Monitoring.Projects.MetricDescriptors.List mockListMethod
        = Mockito.mock(Monitoring.Projects.MetricDescriptors.List.class);
    Meter counterA = registry.counter(idAXY);
    ListMetricDescriptorsResponse response
        = new ListMetricDescriptorsResponse();
    response.setMetricDescriptors(Arrays.asList(descriptorA));

    when(descriptorsApi.list("projects/test-project"))
         .thenReturn(mockListMethod);
    when(mockListMethod.execute()).thenReturn(response);
    MetricDescriptor found = cache.descriptorOrNull(
        registry, counterA, meterMeasurement(counterA));
    verify(mockListMethod, times(1)).execute();
    Assert.assertEquals(found, descriptorA);

    // Returns same response from cache.
    reset(mockListMethod);
    found = cache.descriptorOrNull(
        registry, counterA, meterMeasurement(counterA));
    Assert.assertTrue(found == descriptorA);
    verify(mockListMethod, times(0)).execute();
  }

  @Test
  public void testEnsureDescriptorFailsIfnitFails()
      throws IOException {
    Monitoring.Projects.MetricDescriptors.List mockListMethod
        = Mockito.mock(Monitoring.Projects.MetricDescriptors.List.class);
    Meter counterA = registry.counter(idAXY);

    when(descriptorsApi.list("projects/test-project"))
        .thenThrow(new IOException());
    Assert.assertTrue(
        null == cache.descriptorOrNull(
                    registry, counterA, meterMeasurement(counterA)));
    verify(descriptorsApi, times(1)).list(any(String.class));

    reset(descriptorsApi);

    ListMetricDescriptorsResponse response
        = new ListMetricDescriptorsResponse();
    response.setMetricDescriptors(Arrays.asList(descriptorA));

    when(descriptorsApi.list("projects/test-project"))
        .thenReturn(mockListMethod);
    when(mockListMethod.execute())
        .thenReturn(response);
    Assert.assertTrue(
         cache.descriptorOrNull(registry, counterA, meterMeasurement(counterA))
         == descriptorA);
  }
}
