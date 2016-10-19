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
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;

import com.google.api.services.monitoring.v3.Monitoring;

import java.io.IOException;

import java.util.function.Predicate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
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
public class StackdriverWriterTest {
  static class TestableStackdriverWriter extends StackdriverWriter {
      public TestableStackdriverWriter(ConfigParams params) {
        super(params);
        monitoredResource = new MonitoredResource();

        Map<String, String> labels = new HashMap<String, String>();
        labels.put("project_id", params.getProjectName());
        monitoredResource.setType("global");
        monitoredResource.setLabels(labels);
      }

      public MonitoredResource peekMonitoredResource() {
          return monitoredResource;
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

  static final String INSTANCE_ID = "TestUID";

  DefaultRegistry registry = new DefaultRegistry(clock);

  String projectName = "test-project";
  String applicationName = "test-application";

  Id idInternalTimerCount
      = registry.createId(StackdriverWriter.WRITE_TIMER_NAME + "__count");
  Id idInternalTimerTotal
      = registry.createId(StackdriverWriter.WRITE_TIMER_NAME + "__totalTime");

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
  @Mock Monitoring.Projects.TimeSeries timeseriesApi;

  ConfigParams.Builder writerConfig;
  TestableStackdriverWriter writer;
  MetricDescriptorCacheTest.TestableMetricDescriptorCache
      descriptorRegistrySpy;

  MetricDescriptor descriptorA;
  MetricDescriptor descriptorB;
  MetricDescriptor timerCountDescriptor;  // for timer within StackdriverWriter
  MetricDescriptor timerTimeDescriptor;  // for timer within StackdriverWriter

  private MetricDescriptor makeDescriptor(Id id, List<String> tagNames) {
      MetricDescriptor descriptor = new MetricDescriptor();
      descriptor.setDisplayName(id.name());
      descriptor.setName(descriptorRegistrySpy.idToDescriptorName(id));
      descriptor.setType(descriptorRegistrySpy.idToDescriptorType(id));
      descriptor.setValueType("DOUBLE");
      descriptor.setMetricKind("GAUGE");

      List<LabelDescriptor> labels = new ArrayList<LabelDescriptor>();
      for (String extra : descriptorRegistrySpy.getExtraTimeSeriesLabels().keySet()) {

          LabelDescriptor labelDescriptor = new LabelDescriptor();
          labelDescriptor.setKey(extra);
          labelDescriptor.setValueType("STRING");
          labels.add(labelDescriptor);
      }

      for (String key : tagNames) {
          LabelDescriptor labelDescriptor = new LabelDescriptor();
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
      when(projectsApi.timeSeries()).thenReturn(timeseriesApi);

      writerConfig = new ConfigParams.Builder()
              .setDetermineProjectName(name -> name)
              .setStackdriverStub(monitoringApi)
              .setCustomTypeNamespace("TESTNAMESPACE")
              .setProjectName(projectName)
              .setApplicationName(applicationName)
              .setInstanceId(INSTANCE_ID)
              .setMeasurementFilter(allowAll);

      descriptorRegistrySpy = spy(
          new MetricDescriptorCacheTest.TestableMetricDescriptorCache(
                  writerConfig.build()));
      writerConfig.setDescriptorCache(descriptorRegistrySpy);

      writer = new TestableStackdriverWriter(writerConfig.build());
      List<String> testTags = Arrays.asList("tagA", "tagB");

      descriptorA = makeDescriptor(idA, testTags);
      descriptorB = makeDescriptor(idB, testTags);
      timerCountDescriptor
          = makeDescriptor(idInternalTimerCount, new ArrayList<String>());
      timerTimeDescriptor
          = makeDescriptor(idInternalTimerTotal, new ArrayList<String>());
  }

  @Test
  public void testConfigParamsDefaultInstanceId() {
    ConfigParams config = new ConfigParams.Builder()
            .setStackdriverStub(monitoringApi)
            .setCustomTypeNamespace("TESTNAMESPACE")
            .setProjectName(projectName)
            .setApplicationName(applicationName)
            .setMeasurementFilter(allowAll)
            .build();
    Assert.assertTrue(!config.getInstanceId().isEmpty());
  }

  @Test
  public void testFindBadTimeSeriesInError() {
      TimeSeries tsA = new TimeSeries();
      TimeSeries tsB = new TimeSeries();
      TimeSeries tsC = new TimeSeries();
      TimeSeries tsD = new TimeSeries();
      tsA.setValueType("A");  // these are bogus values to distinguish them
      tsB.setValueType("B");
      tsC.setValueType("C");
      tsD.setValueType("D");

      List<TimeSeries> tsList = Arrays.asList(tsA, tsB, tsC, tsD);
      String prefix = "Bogus text\n  thing 1\n  more babble ";

      Assert.assertEquals(
          tsList.get(1),
          StackdriverWriter.findProblematicTimeSeriesElement(
                  prefix + " timeSeries[1].metric.labels[2] one", tsList));
      Assert.assertEquals(
          tsList.get(2),
          StackdriverWriter.findProblematicTimeSeriesElement(
                  prefix + " timeSeries[2].metric.labels[1] one", tsList));
      Assert.assertEquals(
          null,
          StackdriverWriter.findProblematicTimeSeriesElement(prefix, tsList));
  }

  TimeSeries makeTimeSeries(MetricDescriptor descriptor,
                            Id id, double value, String time) {
    TypedValue tv = new TypedValue();
    tv.setDoubleValue(value);
    TimeInterval timeInterval = new TimeInterval();
    timeInterval.setEndTime(time);

    Point point = new Point();
    point.setValue(tv);
    point.setInterval(timeInterval);

    HashMap<String, String> labels = new HashMap<String, String>();
    labels.put(MetricDescriptorCache.APPLICATION_LABEL, applicationName);
    labels.put(MetricDescriptorCache.INSTANCE_LABEL, INSTANCE_ID);
    for (Tag tag : id.tags()) {
      labels.put(tag.key(), tag.value());
    }

    Metric metric = new Metric();
    metric.setType(descriptor.getType());
    metric.setLabels(labels);

    TimeSeries ts = new TimeSeries();
    ts.setResource(writer.peekMonitoredResource());
    ts.setMetric(metric);
    ts.setPoints(Arrays.asList(point));
    ts.setMetricKind("GAUGE");
    ts.setValueType("DOUBLE");

    return ts;
  }

  @Test
  public void testMeasurementsToTimeSeries() throws IOException {
      Measurement measureAXY
          = new Measurement(idAXY, clock.monotonicTime(), 1);
      Measurement measureBXY
          = new Measurement(idBXY, clock.monotonicTime(), 2);

    DefaultRegistry testRegistry = new DefaultRegistry(clock);
    testRegistry.counter(idAXY).increment();
    testRegistry.counter(idBXY).increment(2);

    // Note this writer is still using the mock Monitoring client stub.
    TestableStackdriverWriter spy
        = spy(new TestableStackdriverWriter(writerConfig.build()));

    doNothing().when(descriptorRegistrySpy)
        .initKnownDescriptors();
    doThrow(new IOException()).when(descriptorRegistrySpy)
        .fetchDescriptorFromService(any(), any());
    doAnswer(new Answer<Void>() {
             public Void answer(InvocationOnMock invocation) {
                descriptorRegistrySpy
                    .injectDescriptor(descriptorA.getType(), descriptorA);
                return null;
             }}).when(descriptorRegistrySpy)
                .createDescriptorInServer(eq(idAXY), eq(testRegistry), any());
    doAnswer(new Answer<Void>() {
             public Void answer(InvocationOnMock invocation) {
                descriptorRegistrySpy
                    .injectDescriptor(descriptorB.getType(), descriptorB);
                return null;
             }}).when(descriptorRegistrySpy)
                .createDescriptorInServer(eq(idBXY), eq(testRegistry), any());

    Meter counterA = testRegistry.counter(idAXY);
    Meter counterB = testRegistry.counter(idBXY);

    descriptorRegistrySpy.descriptorOrNull(
        testRegistry, counterA, counterA.measure().iterator().next());
    descriptorRegistrySpy.descriptorOrNull(
        testRegistry, counterB, counterB.measure().iterator().next());

    Assert.assertTrue(descriptorA
                      == descriptorRegistrySpy.peekDescriptor(idAXY));
    Assert.assertTrue(descriptorB
                      == descriptorRegistrySpy.peekDescriptor(idBXY));

    doReturn(new TimeSeries()).when(spy).measurementToTimeSeries(
        eq(descriptorA), eq(measureAXY));
    doReturn(new TimeSeries()).when(spy).measurementToTimeSeries(
        eq(descriptorB), eq(measureBXY));

    // Just testing the call flow produces descriptors since
    // we return empty TimeSeries values.
    spy.registryToTimeSeries(testRegistry);

  }

  @Test
  public void testAddMeasurementsToTimeSeries() {
    long millisA = TimeUnit.MILLISECONDS.convert(1472394975L, TimeUnit.SECONDS);
    long millisB = millisA + 987;
    String timeA = "2016-08-28T14:36:15.000000000Z";
    String timeB = "2016-08-28T14:36:15.987000000Z";
    Measurement measureAXY = new Measurement(idAXY, millisA, 1);
    Measurement measureBXY = new Measurement(idBXY, millisB, 20.1);

    Assert.assertEquals(
        makeTimeSeries(descriptorA, idAXY, 1, timeA),
        writer.measurementToTimeSeries(descriptorA, measureAXY));
    Assert.assertEquals(
        makeTimeSeries(descriptorB, idBXY, 20.1, timeB),
        writer.measurementToTimeSeries(descriptorB, measureBXY));
  }

  @Test
  public void writeRegistryWithSmallRegistry() throws IOException {
    TestableStackdriverWriter spy
        = spy(new TestableStackdriverWriter(writerConfig.build()));
    Monitoring.Projects.TimeSeries.Create mockCreateMethod
        = Mockito.mock(Monitoring.Projects.TimeSeries.Create.class);

    DefaultRegistry registry = new DefaultRegistry(clock);
    Counter counterA = registry.counter(idAXY);
    Counter counterB = registry.counter(idBXY);
    counterA.increment(4);
    counterB.increment(10);

    doNothing().when(descriptorRegistrySpy).initKnownDescriptors();
    doNothing().when(descriptorRegistrySpy)
        .createDescriptorInServer(eq(idAXY), eq(registry), any());
    doNothing().when(descriptorRegistrySpy)
        .createDescriptorInServer(eq(idBXY), eq(registry), any());
    doNothing().when(descriptorRegistrySpy)
        .createDescriptorInServer(
               eq(idInternalTimerCount), eq(registry), any());
    doNothing().when(descriptorRegistrySpy)
        .createDescriptorInServer(
               eq(idInternalTimerTotal), eq(registry), any());

    // First fetch fails (forcing the create) second fetch succeeds.
    doThrow(new IOException())
        .doAnswer(new Answer<MetricDescriptor>() {
            public MetricDescriptor answer(InvocationOnMock invocation) {
                descriptorRegistrySpy
                    .injectDescriptor(descriptorA.getType(), descriptorA);
                return descriptorA;
            }})
        .when(descriptorRegistrySpy)
        .fetchDescriptorFromService(descriptorA.getName(), descriptorA.getType());
    doThrow(new IOException())
        .doAnswer(new Answer<MetricDescriptor>() {
            public MetricDescriptor answer(InvocationOnMock invocation) {
                descriptorRegistrySpy
                    .injectDescriptor(descriptorB.getType(), descriptorB);
                return descriptorA;
            }})
        .when(descriptorRegistrySpy)
        .fetchDescriptorFromService(descriptorB.getName(), descriptorB.getType());
    doThrow(new IOException())
        .doAnswer(new Answer<MetricDescriptor>() {
            public MetricDescriptor answer(InvocationOnMock invocation) {
                descriptorRegistrySpy
                    .injectDescriptor(timerCountDescriptor.getType(),
                                      timerCountDescriptor);
                return timerCountDescriptor;
            }})
        .when(descriptorRegistrySpy)
        .fetchDescriptorFromService(
            timerCountDescriptor.getName(), timerCountDescriptor.getType());

    doThrow(new IOException())
        .doAnswer(new Answer<MetricDescriptor>() {
            public MetricDescriptor answer(InvocationOnMock invocation) {
                descriptorRegistrySpy
                    .injectDescriptor(timerTimeDescriptor.getType(),
                                      timerTimeDescriptor);
                return timerTimeDescriptor;
            }})
        .when(descriptorRegistrySpy)
        .fetchDescriptorFromService(
            timerTimeDescriptor.getName(), timerTimeDescriptor.getType());

    when(timeseriesApi.create(eq("projects/test-project"),
                              any(CreateTimeSeriesRequest.class)))
        .thenReturn(mockCreateMethod);
    when(mockCreateMethod.execute())
        .thenReturn(null);

    spy.writeRegistry(registry);
    verify(mockCreateMethod, times(1)).execute();

    ArgumentCaptor<CreateTimeSeriesRequest> captor
          = ArgumentCaptor.forClass(CreateTimeSeriesRequest.class);
    verify(timeseriesApi, times(1)).create(eq("projects/test-project"),
                                           captor.capture());
      // A, B, timer count and totalTime.
    Assert.assertEquals(4, captor.getValue().getTimeSeries().size());
  }

  @Test
  public void writeRegistryWithLargeRegistry() throws IOException {
    TestableStackdriverWriter spy
        = spy(new TestableStackdriverWriter(writerConfig.build()));
    Monitoring.Projects.TimeSeries.Create mockCreateMethod
        = Mockito.mock(Monitoring.Projects.TimeSeries.Create.class);

    DefaultRegistry registry = new DefaultRegistry(clock);

    // The contents of this timeseries doesnt matter.
    // It is technically invalid to have null entries in the list,
    // but since we're mocking out the access the values does not matter.
    // What is important is the size of the list, so we can verify chunking.
    List<TimeSeries> tsList = new ArrayList<TimeSeries>();
    for (int i = 0; i < 200; ++i) {
        tsList.add(null);
    }
    tsList.add(new TimeSeries());  // make last one different to test chunking

    doNothing().when(descriptorRegistrySpy).initKnownDescriptors();
    doReturn(tsList).when(spy).registryToTimeSeries(registry);

    // We are bypassing the registry here and never actually created any
    // meters so there is nothing in the registry. However the writeRegistry
    // call itself adds some additional metrics. Here we'll throw exceptions
    // trying to get descriptors for them so they will not be included in the
    // results. The small registry test already validated their use.
    doThrow(new IOException()).when(descriptorRegistrySpy)
        .fetchDescriptorFromService(any(), any());
    doThrow(new IOException()).when(descriptorRegistrySpy)
        .createDescriptorInServer(any(), any(), any());

    // The Mockito ArgumentCaptor to verify the calls wont work here
    // because each call is referencing the same instance but with
    // different TimeSeries list. Since the values arent copied, all
    // the calls point to the same instance with the final mutated value.
    class MatchN implements ArgumentMatcher<CreateTimeSeriesRequest> {
      public int found = 0;
      private int n;
      public MatchN(int n) { super(); this.n = n; }
      @Override public String toString() { return "Match n=" + n; }
      @Override public boolean matches(CreateTimeSeriesRequest obj) {
          boolean eq = ((CreateTimeSeriesRequest) obj)
              .getTimeSeries().size() == n;
          found += eq ? 1 : 0;
          return eq;
      }
    };

    MatchN match200 = new MatchN(200);
    MatchN match1 = new MatchN(1);
    when(timeseriesApi.create(eq("projects/test-project"), argThat(match200)))
        .thenReturn(mockCreateMethod);
    when(timeseriesApi.create(eq("projects/test-project"), argThat(match1)))
        .thenReturn(mockCreateMethod);
    when(mockCreateMethod.execute())
        .thenReturn(null);

    spy.writeRegistry(registry);

    verify(mockCreateMethod, times(2)).execute();
    Assert.assertEquals(1, match200.found);
    Assert.assertEquals(1, match1.found);
  }

  @Test
  public void writeRegistryWithTimer() throws IOException {
    descriptorRegistrySpy.addCustomDescriptorHints(
        Arrays.asList(
            new MetricDescriptorCache.CustomDescriptorHint(
                    idAXY.name(), Arrays.asList("anotherTag"))));

    DefaultRegistry testRegistry = new DefaultRegistry(clock);   
    Timer timer = testRegistry.timer(idAXY);
    timer.record(123, TimeUnit.MILLISECONDS);

    Id countId = testRegistry.createId("idA__count");
    Id countIdXY = countId.withTag("tagA", "X").withTag("tagB", "Y");
    String countName = descriptorRegistrySpy.idToDescriptorName(countId);
    String countType = descriptorRegistrySpy.idToDescriptorType(countId);

    Id timeId = testRegistry.createId("idA__totalTime");
    Id timeIdXY = timeId.withTag("tagA", "X").withTag("tagB", "Y");
    String timeName = descriptorRegistrySpy.idToDescriptorName(timeId);
    String timeType = descriptorRegistrySpy.idToDescriptorType(timeId);

    List<String> testTags = Arrays.asList("tagA", "tagB", "anotherTag");
    MetricDescriptor descriptorCount = makeDescriptor(countId, testTags);
    MetricDescriptor descriptorTime = makeDescriptor(timeId, testTags);
    descriptorCount.setMetricKind("CUMULATIVE");
    descriptorTime.setMetricKind("CUMULATIVE");
    descriptorTime.setUnit("ns");

    doNothing().when(descriptorRegistrySpy)
        .initKnownDescriptors();

    doThrow(new IOException())
        .doReturn(descriptorCount)
        .when(descriptorRegistrySpy)
        .fetchDescriptorFromService(countName, countType);

    Monitoring.Projects.MetricDescriptors.Create countMockCreateMethod
          = Mockito.mock(Monitoring.Projects.MetricDescriptors.Create.class);
    when(descriptorsApi.create(eq("projects/test-project"),
                               eq(descriptorCount)))
        .thenReturn(countMockCreateMethod);
    doAnswer(new Answer<MetricDescriptor>() {
            public MetricDescriptor answer(InvocationOnMock invocation) {
                descriptorRegistrySpy
                    .injectDescriptor(descriptorCount.getType(), descriptorCount);
                return descriptorCount;
            }}).when(countMockCreateMethod).execute();

    doThrow(new IOException())
        .doReturn(descriptorTime)
        .when(descriptorRegistrySpy)
        .fetchDescriptorFromService(timeName, timeType);

    Monitoring.Projects.MetricDescriptors.Create timeMockCreateMethod
          = Mockito.mock(Monitoring.Projects.MetricDescriptors.Create.class);
    when(descriptorsApi.create(eq("projects/test-project"),
                               eq(descriptorTime)))
        .thenReturn(timeMockCreateMethod);
    doAnswer(new Answer<MetricDescriptor>() {
            public MetricDescriptor answer(InvocationOnMock invocation) {
                descriptorRegistrySpy
                    .injectDescriptor(descriptorTime.getType(), descriptorTime);
                return descriptorTime;
            }}).when(timeMockCreateMethod).execute();

    // If we get the expected result then we matched the expected descriptors,
    // which means the transforms occurred as expected.
    List<TimeSeries> tsList = writer.registryToTimeSeries(testRegistry);
    Assert.assertEquals(2, tsList.size());
  }
}
