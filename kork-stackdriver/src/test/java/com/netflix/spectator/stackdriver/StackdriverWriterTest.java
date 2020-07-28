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
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.*;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

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
  }
  ;

  static final long START_TIME_MILLIS =
      TimeUnit.MILLISECONDS.convert(1472394000L, TimeUnit.SECONDS);
  private long millis = START_TIME_MILLIS + 12345L; // doesnt matter
  private Clock clock =
      new Clock() {
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

  Id idInternalTimerCount = registry.createId(StackdriverWriter.WRITE_TIMER_NAME + "__count");
  Id idInternalTimerTotal = registry.createId(StackdriverWriter.WRITE_TIMER_NAME + "__totalTime");

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
  @Mock Monitoring.Projects.TimeSeries timeseriesApi;

  ConfigParams.Builder writerConfig;
  TestableStackdriverWriter writer;
  MetricDescriptorCache descriptorRegistrySpy;

  MetricDescriptor descriptorA;
  MetricDescriptor descriptorB;
  MetricDescriptor timerCountDescriptor; // for timer within StackdriverWriter
  MetricDescriptor timerTimeDescriptor; // for timer within StackdriverWriter

  private MetricDescriptor makeDescriptor(Id id, List<String> tagNames) {
    MetricDescriptor descriptor = new MetricDescriptor();
    descriptor.setDisplayName(id.name());
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

    writerConfig =
        new ConfigParams.Builder()
            .setCounterStartTime(START_TIME_MILLIS)
            .setDetermineProjectName(name -> name)
            .setStackdriverStub(monitoringApi)
            .setCustomTypeNamespace("TESTNAMESPACE")
            .setProjectName(projectName)
            .setApplicationName(applicationName)
            .setInstanceId(INSTANCE_ID)
            .setMeasurementFilter(allowAll);

    descriptorRegistrySpy = spy(new MetricDescriptorCache(writerConfig.build()));
    writerConfig.setDescriptorCache(descriptorRegistrySpy);

    writer = new TestableStackdriverWriter(writerConfig.build());
    List<String> testTags = Arrays.asList("tagA", "tagB");

    descriptorA = makeDescriptor(idA, testTags);
    descriptorB = makeDescriptor(idB, testTags);
    timerCountDescriptor = makeDescriptor(idInternalTimerCount, new ArrayList<String>());
    timerTimeDescriptor = makeDescriptor(idInternalTimerTotal, new ArrayList<String>());
  }

  @Test
  public void testConfigParamsDefaultInstanceId() {
    ConfigParams config =
        new ConfigParams.Builder()
            .setCounterStartTime(START_TIME_MILLIS)
            .setStackdriverStub(monitoringApi)
            .setCustomTypeNamespace("TESTNAMESPACE")
            .setProjectName(projectName)
            .setApplicationName(applicationName)
            .setMeasurementFilter(allowAll)
            .build();
    Assert.assertTrue(!config.getInstanceId().isEmpty());
  }

  TimeSeries makeTimeSeries(MetricDescriptor descriptor, Id id, double value, String time) {
    TypedValue tv = new TypedValue();
    tv.setDoubleValue(value);
    TimeInterval timeInterval = new TimeInterval();
    timeInterval.setStartTime("2016-08-28T14:20:00.000000000Z");
    timeInterval.setEndTime(time);

    Point point = new Point();
    point.setValue(tv);
    point.setInterval(timeInterval);

    HashMap<String, String> labels = new HashMap<String, String>();
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
    ts.setMetricKind("CUMULATIVE");
    ts.setValueType("DOUBLE");

    return ts;
  }

  @Test
  public void testMeasurementsToTimeSeries() throws IOException {
    Measurement measureAXY = new Measurement(idAXY, clock.monotonicTime(), 1);
    Measurement measureBXY = new Measurement(idBXY, clock.monotonicTime(), 2);

    DefaultRegistry testRegistry = new DefaultRegistry(clock);
    testRegistry.counter(idAXY).increment();
    testRegistry.counter(idBXY).increment(2);

    // Note this writer is still using the mock Monitoring client stub.
    TestableStackdriverWriter spy = spy(new TestableStackdriverWriter(writerConfig.build()));

    Meter counterA = testRegistry.counter(idAXY);
    Meter counterB = testRegistry.counter(idBXY);

    doReturn(new TimeSeries())
        .when(spy)
        .measurementToTimeSeries(
            eq(descriptorA.getType()), eq(testRegistry), eq(counterA), eq(measureAXY));
    doReturn(new TimeSeries())
        .when(spy)
        .measurementToTimeSeries(
            eq(descriptorB.getType()), eq(testRegistry), eq(counterB), eq(measureBXY));

    // Just testing the call flow produces descriptors since
    // we return empty TimeSeries values.
    spy.registryToTimeSeries(testRegistry);
  }

  @Test
  public void testAddMeasurementsToTimeSeries() {
    DefaultRegistry testRegistry = new DefaultRegistry(clock);

    long millisA = TimeUnit.MILLISECONDS.convert(1472394975L, TimeUnit.SECONDS);
    long millisB = millisA + 987;
    String timeA = "2016-08-28T14:36:15.000000000Z";
    String timeB = "2016-08-28T14:36:15.987000000Z";
    Meter timerA = testRegistry.timer(idAXY);
    Meter timerB = testRegistry.timer(idBXY);
    Measurement measureAXY = new Measurement(idAXY, millisA, 1);
    Measurement measureBXY = new Measurement(idBXY, millisB, 20.1);

    descriptorRegistrySpy.addExtraTimeSeriesLabel(
        MetricDescriptorCache.INSTANCE_LABEL, INSTANCE_ID);

    Assert.assertEquals(
        makeTimeSeries(descriptorA, idAXY, 1, timeA),
        writer.measurementToTimeSeries(descriptorA.getType(), testRegistry, timerA, measureAXY));
    Assert.assertEquals(
        makeTimeSeries(descriptorB, idBXY, 20.1, timeB),
        writer.measurementToTimeSeries(descriptorB.getType(), testRegistry, timerB, measureBXY));
  }

  @Test
  public void writeRegistryWithSmallRegistry() throws IOException {
    TestableStackdriverWriter spy = spy(new TestableStackdriverWriter(writerConfig.build()));
    Monitoring.Projects.TimeSeries.Create mockCreateMethod =
        Mockito.mock(Monitoring.Projects.TimeSeries.Create.class);

    DefaultRegistry registry = new DefaultRegistry(clock);
    Counter counterA = registry.counter(idAXY);
    Counter counterB = registry.counter(idBXY);
    counterA.increment(4);
    counterB.increment(10);

    when(timeseriesApi.create(eq("projects/test-project"), any(CreateTimeSeriesRequest.class)))
        .thenReturn(mockCreateMethod);
    when(mockCreateMethod.execute()).thenReturn(null);

    spy.writeRegistry(registry);
    verify(mockCreateMethod, times(1)).execute();

    ArgumentCaptor<CreateTimeSeriesRequest> captor =
        ArgumentCaptor.forClass(CreateTimeSeriesRequest.class);
    verify(timeseriesApi, times(1)).create(eq("projects/test-project"), captor.capture());
    // A, B, timer count and totalTime.
    Assert.assertEquals(4, captor.getValue().getTimeSeries().size());
  }

  @Test
  public void writeRegistryWithLargeRegistry() throws IOException {
    TestableStackdriverWriter spy = spy(new TestableStackdriverWriter(writerConfig.build()));
    Monitoring.Projects.TimeSeries.Create mockCreateMethod =
        Mockito.mock(Monitoring.Projects.TimeSeries.Create.class);

    DefaultRegistry registry = new DefaultRegistry(clock);

    // The contents of this timeseries doesnt matter.
    // It is technically invalid to have null entries in the list,
    // but since we're mocking out the access the values does not matter.
    // What is important is the size of the list, so we can verify chunking.
    List<TimeSeries> tsList = new ArrayList<TimeSeries>();
    for (int i = 0; i < 200; ++i) {
      tsList.add(null);
    }
    tsList.add(new TimeSeries()); // make last one different to test chunking

    doReturn(tsList).when(spy).registryToTimeSeries(registry);

    // The Mockito ArgumentCaptor to verify the calls wont work here
    // because each call is referencing the same instance but with
    // different TimeSeries list. Since the values arent copied, all
    // the calls point to the same instance with the final mutated value.
    class MatchN implements ArgumentMatcher<CreateTimeSeriesRequest> {
      public int found = 0;
      private int n;

      public MatchN(int n) {
        super();
        this.n = n;
      }

      @Override
      public String toString() {
        return "Match n=" + n;
      }

      @Override
      public boolean matches(CreateTimeSeriesRequest obj) {
        boolean eq = ((CreateTimeSeriesRequest) obj).getTimeSeries().size() == n;
        found += eq ? 1 : 0;
        return eq;
      }
    }
    ;

    MatchN match200 = new MatchN(200);
    MatchN match1 = new MatchN(1);
    when(timeseriesApi.create(eq("projects/test-project"), argThat(match200)))
        .thenReturn(mockCreateMethod);
    when(timeseriesApi.create(eq("projects/test-project"), argThat(match1)))
        .thenReturn(mockCreateMethod);
    when(mockCreateMethod.execute()).thenReturn(null);

    spy.writeRegistry(registry);

    verify(mockCreateMethod, times(2)).execute();
    Assert.assertEquals(1, match200.found);
    Assert.assertEquals(1, match1.found);
  }

  @Test
  public void writeRegistryWithTimer() throws IOException {
    DefaultRegistry testRegistry = new DefaultRegistry(clock);
    Timer timer = testRegistry.timer(idAXY);
    timer.record(123, TimeUnit.MILLISECONDS);

    // If we get the expected result then we matched the expected descriptors,
    // which means the transforms occurred as expected.
    List<TimeSeries> tsList = writer.registryToTimeSeries(testRegistry);
    Assert.assertEquals(2, tsList.size());
  }
}
