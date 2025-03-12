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

import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.LabelDescriptor;
import com.google.api.services.monitoring.v3.model.MetricDescriptor;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the custom MetricDescriptors to use with Stackdriver.
 *
 * <p>This isnt really a cache anymore. It just knows how to map between spectator and custom metric
 * descriptors, and to manipulate custom metric descriptors if needed.
 */
public class MetricDescriptorCache {
  /** Stackdriver Label identifying the replica instance reporting the values. */
  public static final String INSTANCE_LABEL = "InstanceSrc";

  /** The client-side stub talking to Stackdriver. */
  private final Monitoring service;

  /** The name of the project we give to stackdriver for metric types. */
  private final String projectResourceName;

  /** Internal logging. */
  private final Logger log = LoggerFactory.getLogger("StackdriverMdCache");

  /**
   * The prefix used when declaring Stackdriver Custom Metric types. This has to look a particular
   * way so we'll capture that here.
   *
   * <p>The postfix will be the Spectator measurement name.
   */
  private String baseStackdriverMetricTypeName;

  /**
   * Depending on our monitoredResource, we may need to add additional labels into our time series
   * data to identify ourselves as the source. If so, this is it.
   */
  protected Map<String, String> extraTimeSeriesLabels = new HashMap<String, String>();

  /**
   * Constructor.
   *
   * @param configParams Only the stackdriverStub, projectName, and customTypeNamespace are used.
   */
  public MetricDescriptorCache(ConfigParams configParams) {
    service = configParams.getStackdriverStub();
    projectResourceName = "projects/" + configParams.getProjectName();

    baseStackdriverMetricTypeName =
        String.format(
            "custom.googleapis.com/%s/%s/",
            configParams.getCustomTypeNamespace(), configParams.getApplicationName());
  }

  /**
   * Convert a Spectator ID into a Stackdriver Custom Descriptor Type name.
   *
   * @param id Spectator measurement id
   * @return Fully qualified Stackdriver custom Metric Descriptor type. This always returns the
   *     type, independent of filtering.
   */
  public String idToDescriptorType(Id id) {
    return baseStackdriverMetricTypeName + id.name();
  }

  /** Returns a reference to extra labels to include with TimeSeries data. */
  public Map<String, String> getExtraTimeSeriesLabels() {
    return extraTimeSeriesLabels;
  }

  /**
   * Specifies a label binding to be added to every custom metric TimeSeries.
   *
   * <p>This also adds the labels to every custom MetricDescriptor created.
   *
   * <p>Therefore this method should only be called before any Stackdriver activity. This is awkward
   * to enfore here because in practice the labels are needed once we determine the Stackdriver
   * Monitored Resource, however that can be deferred until after we construct the cache in the
   * event that Stackdriver was not available when we needed to create a custom MonitoredResource.
   */
  public void addExtraTimeSeriesLabel(String key, String value) {
    extraTimeSeriesLabels.put(key, value);
  }

  /** Convert a Spectator Meter type into a Stackdriver Metric kind. */
  public String meterToKind(Registry registry, Meter meter) {
    if (meter instanceof Counter) {
      return "CUMULATIVE";
    }

    if (registry.counters().anyMatch(m -> m.id().equals(meter.id()))) {
      return "CUMULATIVE";
    }

    if (meterIsTimer(registry, meter)) {
      return "CUMULATIVE";
    }

    return "GAUGE";
  }

  /**
   * Get the labels to use for a given metric instance.
   *
   * @param descriptorType The Stackdriver custom descriptor ype name for the labels.
   * @param tags The Spectator Measurement tags for the data.
   * @return A map of label key, value bindings may include fewer or more tags than those provided
   *     depending on the extra tags and configured custom descriptor hints.
   */
  public Map<String, String> tagsToTimeSeriesLabels(String descriptorType, Iterable<Tag> tags) {
    HashMap<String, String> labels = new HashMap<String, String>(extraTimeSeriesLabels);

    for (Tag tag : tags) {
      labels.put(tag.key(), tag.value());
    }
    return labels;
  }

  /** Helper function providing a hook to fix or omit bad labels. */
  private void addSanitizedLabel(
      MetricDescriptor descriptor, Tag tag, Map<String, String> labels) {}

  /**
   * Remember the analysis of meters to determine if they are timers or not. This is because
   * meterIsTimer is called continuously, not just on creation of a new descriptor.
   */
  private Map<Id, Boolean> idToTimer = new HashMap<Id, Boolean>();

  /**
   * The Stackdriver metric Kind to use for a given Custom Descriptor type. The kind is derived from
   * the underlying Spectator Metric.
   */
  private Map<String, String> typeToKind = new HashMap<String, String>();

  /** Determine if meter is a Timer or not. */
  public boolean meterIsTimer(Registry registry, Meter meter) {
    return idToTimer.computeIfAbsent(
        meter.id(),
        k -> {
          try {
            return registry.timers().anyMatch(m -> m.id().equals(meter.id()));
          } catch (ArrayIndexOutOfBoundsException aoex) {
            // !!! 20160929
            // !!! I dont know if this is a bug or what
            // !!! but the tests all get an array out of bounds calling stream()
            return meter instanceof Timer;
          }
        });
  }

  /** Determine the Stackdriver Custom Metric Desctiptor Kind to use. */
  public String descriptorTypeToKind(String descriptorType, Registry registry, Meter meter) {
    return typeToKind.computeIfAbsent(
        descriptorType,
        k -> {
          return meterToKind(registry, meter);
        });
  }

  /**
   * Hack another label into an existing Stackdriver Custom Metric Descriptor.
   *
   * <p>This may lose historic time series data. It returns the new descriptor (result from create)
   * only for testing purposes.
   */
  public MetricDescriptor addLabel(String descriptorType, String newLabel) {
    String descriptorName = projectResourceName + "/metricDescriptors/" + descriptorType;
    MetricDescriptor descriptor;

    log.info(
        "Adding label '{}' to stackdriver descriptor '{}'. This may lose existing metric data",
        newLabel,
        descriptorName);
    try {
      descriptor = service.projects().metricDescriptors().get(descriptorName).execute();
    } catch (IOException ioex) {
      log.error("Could not fetch descriptor " + descriptorType + ": " + ioex);
      return null;
    }
    List<LabelDescriptor> labels = descriptor.getLabels();
    if (labels == null) {
      labels = new ArrayList<LabelDescriptor>();
      descriptor.setLabels(labels);
    }

    for (LabelDescriptor labelDescriptor : descriptor.getLabels()) {
      if (labelDescriptor.getKey().equals(newLabel)) {
        log.info("{} already added to descriptor", newLabel);
        return descriptor;
      }
    }

    LabelDescriptor labelDescriptor = new LabelDescriptor();
    labelDescriptor.setKey(newLabel);
    labelDescriptor.setValueType("STRING");
    descriptor.getLabels().add(labelDescriptor);

    /* Catch but ignore errors. Assume there is another process making mods too.
     * We may overwrite theirs, but they'll just have to make them again later.
     */
    try {
      log.info("Deleting existing stackdriver descriptor {}", descriptorName);
      service.projects().metricDescriptors().delete(descriptorName).execute();
    } catch (IOException ioex) {
      log.info("Ignoring error " + ioex);
    }

    try {
      log.info("Adding new descriptor for {}", descriptorName);
      return service
          .projects()
          .metricDescriptors()
          .create(projectResourceName, descriptor)
          .execute();
    } catch (IOException ioex) {
      log.error("Failed to update the descriptor definition: " + ioex);
      return null;
    }
  }
}
