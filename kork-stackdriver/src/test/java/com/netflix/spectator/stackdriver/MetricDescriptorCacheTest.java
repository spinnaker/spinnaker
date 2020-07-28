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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.*;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class MetricDescriptorCacheTest {
  static class ReturnExecuteDescriptorArg implements Answer {
    private Monitoring.Projects.MetricDescriptors.Create mockCreateMethod;

    public ReturnExecuteDescriptorArg(
        Monitoring.Projects.MetricDescriptors.Create mockCreateMethod) {
      this.mockCreateMethod = mockCreateMethod;
    }

    public Object answer(InvocationOnMock invocation) {
      try {
        MetricDescriptor descArg = (MetricDescriptor) invocation.getArguments()[1];
        when(mockCreateMethod.execute()).thenReturn(descArg);
        return mockCreateMethod;
      } catch (IOException ioex) {
        return null; // Not Reached
      }
    }
  }
  ;

  private long millis = 12345L;
  private Clock clock =
      new Clock() {
        public long wallTime() {
          return millis;
        }

        public long monotonicTime() {
          return millis;
        }
      };
  DefaultRegistry registry = new DefaultRegistry(clock);

  MetricDescriptorCache cache;
  String projectName = "test-project";
  String applicationName = "test-application";

  Id idA = registry.createId("idA");
  Id idB = registry.createId("idB");
  Id idAXY = idA.withTag("tagA", "X").withTag("tagB", "Y");
  Id idAYX = idA.withTag("tagA", "Y").withTag("tagB", "X");
  Id idBXY = idB.withTag("tagA", "X").withTag("tagB", "Y");

  Predicate<Measurement> allowAll =
      new Predicate<Measurement>() {
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

  private MetricDescriptor makeDescriptor(Id id, List<String> tagNames, String kind) {
    MetricDescriptor descriptor = new MetricDescriptor();
    descriptor.setDisplayName(id.name());
    descriptor.setType(cache.idToDescriptorType(id));
    descriptor.setValueType("DOUBLE");
    descriptor.setMetricKind(kind);

    List<LabelDescriptor> labels = new ArrayList<LabelDescriptor>();
    LabelDescriptor labelDescriptor = new LabelDescriptor();
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

    config =
        new ConfigParams.Builder()
            .setDetermineProjectName(name -> name)
            .setStackdriverStub(monitoringApi)
            .setCustomTypeNamespace("TESTNAMESPACE")
            .setProjectName(projectName)
            .setApplicationName(applicationName)
            .setMeasurementFilter(allowAll);

    cache = new MetricDescriptorCache(config.build());
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
  public void testAddLabel() throws IOException {
    List<String> origTags = Arrays.asList("tagA", "tagB");
    MetricDescriptor origDescriptor = makeDescriptor(idA, origTags, "GAUGE");

    String type = origDescriptor.getType();
    String label = "newTag";
    List<String> updatedTags = Arrays.asList("tagA", "tagB", label);
    MetricDescriptor updatedDescriptor = makeDescriptor(idA, updatedTags, "GAUGE");

    Monitoring.Projects.MetricDescriptors.Get mockGetMethod =
        Mockito.mock(Monitoring.Projects.MetricDescriptors.Get.class);
    Monitoring.Projects.MetricDescriptors.Delete mockDeleteMethod =
        Mockito.mock(Monitoring.Projects.MetricDescriptors.Delete.class);
    Monitoring.Projects.MetricDescriptors.Create mockCreateMethod =
        Mockito.mock(Monitoring.Projects.MetricDescriptors.Create.class);

    String descriptorName = "projects/test-project/metricDescriptors/" + type;
    when(descriptorsApi.get(eq(descriptorName))).thenReturn(mockGetMethod);
    when(descriptorsApi.delete(eq(descriptorName))).thenReturn(mockDeleteMethod);
    when(descriptorsApi.create(eq("projects/test-project"), eq(updatedDescriptor)))
        .thenReturn(mockCreateMethod);

    when(mockGetMethod.execute()).thenReturn(origDescriptor);
    when(mockCreateMethod.execute()).thenReturn(updatedDescriptor);

    Assert.assertEquals(updatedDescriptor, cache.addLabel(type, label));
    verify(mockGetMethod, times(1)).execute();
    verify(mockDeleteMethod, times(1)).execute();
    verify(mockCreateMethod, times(1)).execute();
  }

  @Test
  public void testAddLabelWithDeleteFailure() throws IOException {
    List<String> origTags = Arrays.asList("tagA", "tagB");
    MetricDescriptor origDescriptor = makeDescriptor(idA, origTags, "GAUGE");

    String type = origDescriptor.getType();
    String label = "newTag";
    List<String> updatedTags = Arrays.asList("tagA", "tagB", label);
    MetricDescriptor updatedDescriptor = makeDescriptor(idA, updatedTags, "GAUGE");

    Monitoring.Projects.MetricDescriptors.Get mockGetMethod =
        Mockito.mock(Monitoring.Projects.MetricDescriptors.Get.class);
    Monitoring.Projects.MetricDescriptors.Delete mockDeleteMethod =
        Mockito.mock(Monitoring.Projects.MetricDescriptors.Delete.class);
    Monitoring.Projects.MetricDescriptors.Create mockCreateMethod =
        Mockito.mock(Monitoring.Projects.MetricDescriptors.Create.class);

    String descriptorName = "projects/test-project/metricDescriptors/" + type;
    when(descriptorsApi.get(eq(descriptorName))).thenReturn(mockGetMethod);
    when(descriptorsApi.delete(eq(descriptorName))).thenReturn(mockDeleteMethod);
    when(descriptorsApi.create(eq("projects/test-project"), eq(updatedDescriptor)))
        .thenReturn(mockCreateMethod);

    when(mockGetMethod.execute()).thenReturn(origDescriptor);
    when(mockDeleteMethod.execute()).thenThrow(new IOException("Not Found"));
    when(mockCreateMethod.execute()).thenReturn(updatedDescriptor);

    Assert.assertEquals(updatedDescriptor, cache.addLabel(type, label));
    verify(mockGetMethod, times(1)).execute();
    verify(mockDeleteMethod, times(1)).execute();
    verify(mockCreateMethod, times(1)).execute();
  }

  @Test
  public void testAddLabelWithCreateFailure() throws IOException {
    List<String> origTags = Arrays.asList("tagA", "tagB");
    MetricDescriptor origDescriptor = makeDescriptor(idA, origTags, "GAUGE");

    String type = origDescriptor.getType();
    String label = "newTag";
    List<String> updatedTags = Arrays.asList("tagA", "tagB", label);
    MetricDescriptor updatedDescriptor = makeDescriptor(idA, updatedTags, "GAUGE");

    Monitoring.Projects.MetricDescriptors.Get mockGetMethod =
        Mockito.mock(Monitoring.Projects.MetricDescriptors.Get.class);
    Monitoring.Projects.MetricDescriptors.Delete mockDeleteMethod =
        Mockito.mock(Monitoring.Projects.MetricDescriptors.Delete.class);
    Monitoring.Projects.MetricDescriptors.Create mockCreateMethod =
        Mockito.mock(Monitoring.Projects.MetricDescriptors.Create.class);

    String descriptorName = "projects/test-project/metricDescriptors/" + type;
    when(descriptorsApi.get(eq(descriptorName))).thenReturn(mockGetMethod);
    when(descriptorsApi.delete(eq(descriptorName))).thenReturn(mockDeleteMethod);
    when(descriptorsApi.create(eq("projects/test-project"), eq(updatedDescriptor)))
        .thenReturn(mockCreateMethod);

    when(mockGetMethod.execute()).thenReturn(origDescriptor);
    when(mockCreateMethod.execute()).thenThrow(new IOException("Not Found"));

    Assert.assertNull(cache.addLabel(type, label));

    verify(mockGetMethod, times(1)).execute();
    verify(mockDeleteMethod, times(1)).execute();
    verify(mockCreateMethod, times(1)).execute();
  }

  @Test
  public void testAddLabelAlreadyExists() throws IOException {
    String label = "newTag";
    List<String> origTags = Arrays.asList("tagA", "tagB", label);
    MetricDescriptor origDescriptor = makeDescriptor(idA, origTags, "GAUGE");
    String type = origDescriptor.getType();

    Monitoring.Projects.MetricDescriptors.Get mockGetMethod =
        Mockito.mock(Monitoring.Projects.MetricDescriptors.Get.class);
    Monitoring.Projects.MetricDescriptors.Delete mockDeleteMethod =
        Mockito.mock(Monitoring.Projects.MetricDescriptors.Delete.class);
    Monitoring.Projects.MetricDescriptors.Create mockCreateMethod =
        Mockito.mock(Monitoring.Projects.MetricDescriptors.Create.class);

    String descriptorName = "projects/test-project/metricDescriptors/" + type;
    when(descriptorsApi.get(eq(descriptorName))).thenReturn(mockGetMethod);
    when(descriptorsApi.delete(any())).thenReturn(mockDeleteMethod);
    when(descriptorsApi.create(any(), any())).thenReturn(mockCreateMethod);

    when(mockGetMethod.execute()).thenReturn(origDescriptor);
    Assert.assertEquals(origDescriptor, cache.addLabel(type, label));

    verify(mockGetMethod, times(1)).execute();
    verify(mockDeleteMethod, times(0)).execute();
    verify(mockCreateMethod, times(0)).execute();
  }
}
