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
import com.google.api.services.monitoring.v3.model.ListMetricDescriptorsResponse;
import com.google.api.services.monitoring.v3.model.LabelDescriptor;
import com.google.api.services.monitoring.v3.model.MetricDescriptor;

import com.google.api.client.http.HttpResponseException;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Manages the custom MetricDescriptors to use with Stackdriver.
 *
 * Maps Spectator Metrics and Measurements into Stackdriver MetricDescriptors.
 */
public class MetricDescriptorCache {
  /**
   * Used to inject minimal labels required by a given custom descriptor.
   *
   * This is a workaround to ensure that certain labels exist within a
   * given metric when it is created on demand where the call sites are
   * inconsistent and may not contain all the labels ultimately needed.
   * Since stackdriver descriptors are immutable, we need to get it right
   * up front, yet we still want to allow hands-free maintainence where
   * possible and just adapt to the runtime code, creating new metrics
   * as they are introduced.
   */
  public static class CustomDescriptorHint {
    /**
     * The name of the hinted metric is the measurement name.
     */
    protected String name;

    /**
     * Set of labels that the metric should be sure to specify when it is
     * created. Typically these are labels that are not always present so
     * cannot be inferred in a small measurement sample.
     */
    protected List<String> labels;

    /**
     * Set of labels that the metric should ommit both when defining and
     * writing.
     */
    protected List<String> redacted;

    /**
     * The name of the Spectator Measurement name that this hint is for.
     */
    public String getName() {
      return name;
    }

    /**
     * The minimal labels that the descriptor should have.
     *
     * In practice this is intended to convey labels that might not
     * always be present if the descriptor is being inferred from a use site.
     */
    public List<String> getLabels() {
      return labels;
    }

    /**
     * Labels that should be ignored.
     *
     * This only ignores the label, not the value.
     */
    public List<String> getRedacted() {
      return redacted;
    }

    /**
     * Constructs a hint, aliases labels list.
     */
    public CustomDescriptorHint(String name,
                                List<String> labels) {
      this(name, labels, null);
    }

    /**
     * Constructs a hint, aliases labels list.
     *
     * @param name
     *   The name of the measurment this is for.
     *
     * @param labels
     *   A minimal list of labels that should be included.
     *   These are typically labels that are not always used
     *   so may not be present in a small sample if the type
     *   labels are inferred.
     *
     * @param redact
     *   A list of labels to ommit if they are found.
     *   This is a workaround for Stackdriver's 10-label constraint.
     */
    public CustomDescriptorHint(String name,
                                List<String> labels,
                                List<String> redact) {
      this.name = name;
      this.labels = labels;
      this.redacted = redact;
    }

    /**
     * Default constructor for spring loader.
     */
    protected CustomDescriptorHint() {
        // empty.
    }
  };


  /**
   * Stackdriver Label identifying the application reporting the values.
   *
   * This is used when not configured for uniqueMetricsPerApplication
   */
  public static final String APPLICATION_LABEL = "MicroserviceSrc";

  /**
   * Stackdriver Label identifying the replica instance reporting the values.
   */
  public static final String INSTANCE_LABEL = "InstanceSrc";


  /**
   * We are going to use this as an internal sentinal to
   * distinguish between not knowing the descriptor for a type
   * vs the type being invalid for having a descriptor.
   * This is used to simplify the interface to an internal helper function.
   */
  private static final MetricDescriptor INVALID_METRIC_DESCRIPTOR
      = new MetricDescriptor();


  /**
   * The client-side stub talking to Stackdriver.
   */
  private final Monitoring service;

  /**
   * The name of the project we give to stackdriver for metric types.
   */
  private final String projectResourceName;

  /**
   * Internal logging.
   */
  private final Logger log = LoggerFactory.getLogger("StackdriverMdCache");

  /**
   * The prefix used when declaring Stackdriver Custom Metric types.
   * This has to look a particular way so we'll capture that here.
   *
   * The postfix will be the Spectator measurement name.
   */
  private String baseStackdriverMetricTypeName;

  /**
   * The prefix used when declaring Stackdriver Custom Metric names.
   * This has to look a particular way so we'll capture that here.
   *
   * The postfix will be the Spectator measurement name.
   */
  private String baseStackdriverMetricName;

  /**
   * HACK: Stackdriver descriptors are immutable descriptors requiring
   *       labels be known (and declared) up front.
   *  Some types we are sharing across services that use different labels.
   *  This is a mechanism allowing external injection of these special cases.
   *  The hints are only used when the need to create a descriptor arises.
   *  Descriptors are only created once in the lifetime of a project (unless
   *  the label is explicitly deleted, erasing all history, at some point).
   *
   *  We could externally create the labels, but doing so dynamically feels
   *  more maintainable.
   *
   *  The key in this table is the desired Stackdriver descriptor name.
   *
   *  Note that this is not thread safe. Presumably we write to the hints
   *  only on startup, and from within one thread so this doesnt matter.
   */
  private final Map<String, Set<String>> labelHints
      = new HashMap<String, Set<String>>();

  /**
   * This is a workaround for Stackdriver limiting custom metrics to
   * having 10 labels (b/31469504). For certain known metrics that
   * have more than 10 labels but some can be ommitted, this map
   * defines which of those labels to redact. It is populated from
   * the hints as well.
   */
  private final Map<String, Set<String>> redactedHints
      = new HashMap<String, Set<String>>();

  /**
   * Depending on our monitoredResource, we may need to add additional
   * labels into our time series data to identify ourselves as the source.
   * If so, this is it.
   */
  protected Map<String, String> extraTimeSeriesLabels
      = new HashMap<String, String>();


  /**
   * These are the custom MetricDescriptor types we know about so that we
   * know whether we need to create a new one or not. Types are created on
   * demand, but remembered across executions, so only created once, forever.
   * Instead of even remembering them, we could probably just assume it exists
   * then respond to an error by creating it and trying again.
   *
   * The key in the map is the Stackdriver descriptor type.
   *
   * protected for testing.
   */
  protected final Map<String, MetricDescriptor> knownDescriptors
      = new HashMap<String, MetricDescriptor>();

  /**
   * If we run into a problem with a type descriptor, we'll blacklist it
   * in order to avoid filling our log file with errors and potentially
   * having side effects preventing good metrics from being logged.
   */
  private final Set<String> badDescriptorTypes = new HashSet<String>();

  /**
   * A set of descriptorTypes that we created but have not yet confirmed.
   *
   * This is to work around the race condition in b/31469322 where the
   * descriptor may not yet be ready to use when we want to actually use it.
   * This list acts as a guard to prevent us from attempting to recreate the
   * descriptor while waiting for it to be fetched into knownDescriptors.
   */
  private final Set<String> unconfirmedDescriptorTypes = new HashSet<String>();

  /**
   * Constructor.
   *
   * @param configParams
   *   Only the stackdriverStub, projectName, and customTypeNamespace
   *   are used.
   */
  public MetricDescriptorCache(ConfigParams configParams) {
    service = configParams.getStackdriverStub();
    projectResourceName = "projects/" + configParams.getProjectName();

    baseStackdriverMetricTypeName
        = String.format("custom.googleapis.com/%s/",
                        configParams.getCustomTypeNamespace());
    if (configParams.isMetricUniquePerApplication()) {
        baseStackdriverMetricTypeName
            += String.format("%s/", configParams.getApplicationName());
    }

    baseStackdriverMetricName
        = String.format("%s/metricDescriptors/%s",
                        projectResourceName, baseStackdriverMetricTypeName);
  }

  /**
   * Convert a Spectator ID into a Stackdriver Custom Descriptor Type name.
   *
   * @param id
   *   Spectator measurement id
   *
   * @return
   *   Fully qualified Stackdriver custom Metric Descriptor type.
   *   This always returns the type, independent of filtering.
   */
  public String idToDescriptorType(Id id) {
    return baseStackdriverMetricTypeName + id.name();
  }

  /**
   * Convert a Spectator ID into a Stackdriver Custom Descriptor instance name.
   *
   * @param id
   *   Spectator measurement id
   *
   * @return
   *   Fully qualified Stackdriver custom Metric Descriptor name.
   *   This always returns the name, independent of filtering.
   */
  public String idToDescriptorName(Id id) {
    return baseStackdriverMetricName + id.name();
  }

  /**
   * Returns a reference to extra labels to include with TimeSeries data.
   */
  public Map<String, String> getExtraTimeSeriesLabels() {
    return extraTimeSeriesLabels;
  }

  /**
   * Specifies a label binding to be added to every custom metric TimeSeries.
   *
   * This also adds the labels to every custom MetricDescriptor created.
   *
   * Therefore this method should only be called before any Stackdriver
   * activity. This is awkward to enfore here because in practice the
   * labels are needed once we determine the Stackdriver Monitored Resource,
   * however that can be deferred until after we construct the cache in
   * the event that Stackdriver was not available when we needed to create
   * a custom MonitoredResource.
   */
  public void addExtraTimeSeriesLabel(String key, String value) {
    extraTimeSeriesLabels.put(key, value);
  }

  /**
   * Inject a hints about one or more types.
   *
   * Hints are only used if and when we need to create a type descriptor for
   * a type. This happens at most once over the lifetime of a system (not just
   * a process invocation). Adding hints is a preemtive workaround for known
   * inconsistent uses for given types where an incomplete descriptor may
   * otherwise be inferred.
   *
   * Note that the name of the hint is just the Spectator name, not the
   * Stackdriver type name. That is, it does not yet contain the
   * baseStackdriverMetricTypeName. This is for [assumed] convienence
   * specifying the hints.
   */
  public void addCustomDescriptorHints(
        List<? extends CustomDescriptorHint> hints) {
    for (CustomDescriptorHint hint : hints) {
      String name = hint.getName();
      String typeName = name.startsWith(baseStackdriverMetricTypeName)
                      ? name : baseStackdriverMetricTypeName + name;

      List<String> added = hint.getLabels();
      if (added != null && !added.isEmpty()) {
          labelHints.computeIfAbsent(typeName, k -> new HashSet<String>())
              .addAll(added);
      }
      List<String> redacted = hint.getRedacted();
      if (redacted != null && !redacted.isEmpty()) {
          redactedHints.computeIfAbsent(typeName, k -> new HashSet<String>())
              .addAll(redacted);
      }
    }
  }

  /**
   * Lookup all the pre-existing custom metrics so that we
   * know if we need to create new metric descriptors or not.
   */
  void initKnownDescriptors()
      throws HttpResponseException, IOException {
    ListMetricDescriptorsResponse response =
      service.projects().metricDescriptors().list(projectResourceName)
      .execute();

    for (MetricDescriptor descriptor : response.getMetricDescriptors()) {
      if (descriptor.getType().startsWith(baseStackdriverMetricTypeName)) {
        knownDescriptors.put(descriptor.getType(), descriptor);
      }
    }
  }

  /**
   * Get the descriptor for a measurement, if there is one.
   *
   * As a side effect, we will ensure that we have a Stackdriver descriptor for
   * the measurement. If for some reason we cannot have a descriptor then
   * we will drop that measurement from those collected, thus ensuring that
   * all the metrics returned can be written into Stackdriver.
   *
   * @param registry
   *   The Spectator Registry is used to determine the meter type if needed.
   *
   * @param meter
   *   The Spectator Meter that the measurement is from.
   *
   * @param measurement
   *   The Spectator Measurement that we want a descriptor for.
   *
   * @return
   *   The available Metric Descriptor or null if none in the cache.
   *   null does not necessarily mean future calls will fail.
   */
  public MetricDescriptor descriptorOrNull(
        Registry registry, Meter meter, Measurement measurement) {
    if (lookupDescriptor(registry, meter, measurement) == null) {
      return null;
    }

    String descriptorType = idToDescriptorType(measurement.id());
    MetricDescriptor descriptor = knownDescriptors.get(descriptorType);
    if (descriptor == null) {
      log.error("*** No stackdriver descriptor for {}", descriptorType);
      return null;
    }
    return descriptor;
  }

  /**
   * Perform a preliminary lookup locally before we hit the server.
   *
   * This is factored out to simplify the call site. There isnt anything
   * special about not wanting to hit the server.
   *
   * @param typeName
   *   The desired custom MetricDescriptor type name.
   *
   * @param meter
   *   The Spectator meter that the type is for.
   *   This is only used to filter out spring-only metrics
   *   which are not of interest to Spinnaker and often have
   *   names that are not friendly to our heuristics.
   *
   * @return
   *   The descriptor if we already have one, null if we dont.
   *   INVALID_METRIC_DESCRIPTOR indicates the descriptor isnt valid
   *   so dont pursue this type further.
   */
  private MetricDescriptor preliminaryLookup(String typeName) {
    if (knownDescriptors.isEmpty()) {
      try {
        initKnownDescriptors();
      } catch (HttpResponseException rex) {
        log.error(
            "Caught HttpResponseException initializing KnownDescriptors", rex);
        return INVALID_METRIC_DESCRIPTOR;
      } catch (IOException ioex) {
        log.error("Caught IOException initializing knownDescriptors.", ioex);
        return INVALID_METRIC_DESCRIPTOR;
      }
    }

    MetricDescriptor found = knownDescriptors.get(typeName);
    if (found != null) {
      return found;
    } else if (badDescriptorTypes.contains(typeName)) {
      return INVALID_METRIC_DESCRIPTOR;
    }

    return null;
  }

  /**
   * Lookup a descriptor, creating one if needed.
   *
   * @param registry
   *   The Spectator Registry is used to determine the meter type if needed.
   *
   * @param meter
   *   The Spectator Meter is used for reference if this is the
   *   first time the Id has even been seen over the lifetime of
   *   the project this is running in.
   *
   * @param measurement
   *   The Spectator Mesurement to get the descriptor for.
   *
   * @return
   *   The descriptor or null if a descriptor is not yet available.
   *   A null result could mean that the descriptor is inflight
   *   with a pending creation request.
   */
  MetricDescriptor lookupDescriptor(
        Registry registry, Meter meter, Measurement measurement) {
    Id id = measurement.id();
    String descriptorType = idToDescriptorType(id);
    MetricDescriptor found = preliminaryLookup(descriptorType);
    if (found != null) {
      // This public function returns null to indicate invalid descriptors
      // because it is easier to consume that way. However internally we
      // go the other way and return null indicating 'dont known'
      // because it is more readable.
      return found == INVALID_METRIC_DESCRIPTOR ? null : found;
    }

    String descriptorName = idToDescriptorName(id);
    try {
      return fetchDescriptorFromService(descriptorName, descriptorType);
    } catch (HttpResponseException rex) {
      if (rex.getStatusCode() == 404) {
        log.debug("It appears that descriptor name='{}' does not yet exist.",
                 descriptorName);
      } else {
        log.error(
            "Caught HttpResponseException fetching descriptor", rex);
      }
    } catch (IOException ioex) {
      log.error(
          "Caught HttpResponseException fetching descriptor", ioex);
    }

    return createDescriptor(registry, meter, measurement);
  }

  /**
   * Helper function to fetch an individual descriptor from Stackdriver.
   *
   * This is used to lookup whether or not a particular type already exists.
   * Normally we would already know this from initKnownDescriptors, however
   * there is a race condition on first usage where the descriptor did not
   * exist on our startup but another process has since created it.
   *
   * Fetching it will correct for that.
   */
  MetricDescriptor fetchDescriptorFromService(String descriptorName,
                                              String descriptorType)
       throws IOException {
    MetricDescriptor descriptor
        = service.projects().metricDescriptors().get(descriptorName).execute();
    knownDescriptors.put(descriptorType, descriptor);
    if (unconfirmedDescriptorTypes.contains(descriptorType)) {
        unconfirmedDescriptorTypes.remove(descriptorType);
    }
    return descriptor;
  }

  /**
   * Creates a new descriptor.
   *
   * @param measurement
   *   The Spectator Measurement is used to determine the name and labels.
   *
   * @param registry
   *   The Spectator Registry is used to determine the meter type if needed.
   *
   * @param meter
   *   The Spectator Meter may be considered for the kind of descriptor and
   *   other metadata.
   *
   * @return
   *   This may return null if the descriptor is not yet available for use.
   */
  private MetricDescriptor createDescriptor(
        Registry registry, Meter meter, Measurement measurement) {
    Id id = measurement.id();
    try {
      createDescriptorInServer(id, registry, meter);
    } catch (HttpResponseException rex) {
      log.error(
          "Caught HttpResponseException creating descriptor", rex);
    } catch (IOException ioex) {
      log.error(
          "Caught IOException creating descriptor", ioex);
    }

    String descriptorName = idToDescriptorName(id);
    String descriptorType = idToDescriptorType(id);
    try {
      return fetchDescriptorFromService(descriptorName, descriptorType);
    } catch (IOException ioex) {
      if (!unconfirmedDescriptorTypes.contains(descriptorType)) {
        log.warn("Descriptor for name='{}', type='{}' is not yet ready:",
                 descriptorName, descriptorType, ioex);
      }
    }
    return null;
  }

  /**
   * Creates a new [globally persistent] descriptor within Stackdriver.
   *
   * Since descriptors are global and persistent this only happens the
   * first time we wish to use it over the lifetime of the project that
   * is managing the Stackdriver data.  It is also possible a extra times
   * in other processes encountering a race condition on the first-time use.
   *
   * There is an additional race condition within Stackdriver itself between
   * creating a new descriptor and using one for the first time.
   * b/31469322 notes that when this race condition occurs, creating time
   * series data may auto-create the metric descriptor for us, which will
   * overwrite the descriptor we attempted to create, losing any subtle
   * details we may have added (especially additional labels from the hints).
   *
   * Therefore, this method is void and requires discovery of the descriptor
   * through the normal fetching even though we are the ones that created it.
   */
  void createDescriptorInServer(Id id, Registry registry, Meter meter)
       throws IOException {
    String descriptorName = idToDescriptorName(id);
    String descriptorType = idToDescriptorType(id);
    if (unconfirmedDescriptorTypes.contains(descriptorType)) {
        // creation request is already in-flight.
        return;
    }

    MetricDescriptor descriptor = new MetricDescriptor();
    descriptor.setName(descriptorName);
    descriptor.setDisplayName(id.name());
    descriptor.setType(descriptorType);
    descriptor.setValueType("DOUBLE");

    String untransformedDescriptorType;
    if (meterIsTimer(registry, meter)) {
      if (id.name().endsWith("__totalTime")) {
        descriptor.setUnit("ns");
      }
      descriptor.setMetricKind("CUMULATIVE");
      int suffixOffset = descriptorType.lastIndexOf("__");
      untransformedDescriptorType = descriptorType.substring(0, suffixOffset);
    } else {
      descriptor.setMetricKind(meterToKind(registry, meter));
      untransformedDescriptorType = descriptorType;
    }

    List<LabelDescriptor> labels = new ArrayList<LabelDescriptor>();
    for (String key : extraTimeSeriesLabels.keySet()) {
        LabelDescriptor labelDescriptor = new LabelDescriptor();
        labelDescriptor.setKey(key);
        labelDescriptor.setValueType("STRING");
        labels.add(labelDescriptor);
    }

    for (Tag tag : id.tags()) {
       LabelDescriptor labelDescriptor = new LabelDescriptor();
       labelDescriptor.setKey(tag.key());
       labelDescriptor.setValueType("STRING");
       labels.add(labelDescriptor);
    }

    maybeAddLabelHints(untransformedDescriptorType, labels);
    if (labels.size() > 10) {
        log.error("{} has too many labels for stackdriver to handle: {}",
                  id.name(), labels);
        log.warn("*** MARKING {} AS A BAD METRIC ***to ignore from now on.",
                 id.name());
        badDescriptorTypes.add(descriptorType);
        return;
     }

    descriptor.setLabels(labels);
    MetricDescriptor response = service.projects().metricDescriptors()
      .create(projectResourceName, descriptor)
      .execute();
    unconfirmedDescriptorTypes.add(descriptorType);

    log.info("Created new MetricDescriptor {}", response.toString());
  }

  /**
   * Convert a Spectator Meter type into a Stackdriver Metric kind.
   */
  public String meterToKind(Registry registry, Meter meter) {
    if (meter instanceof Counter) {
      return "CUMULATIVE";
    }

    if (registry.counters().anyMatch(m -> m.id().equals(meter.id()))) {
      return "CUMULATIVE";
    }
    return "GAUGE";
  }

  /**
   * Given a list of labels, maybe embellish it with additional labels
   * depending on whether there are hints for the given descriptorType.
   */
  private void maybeAddLabelHints(String descriptorType,
                                  List<LabelDescriptor> labels) {
    Set<String> redact = redactedHints.get(descriptorType);
    if (redact != null) {
      for (String name : redact) {
        for (int i = 0; i < labels.size(); ++i) {
            if (labels.get(i).getKey().equals(name)) {
                log.debug("Redacting label {}", name);
                labels.remove(i);
                break;
            }
        }
      }
    }
    Set<String> ensure = labelHints.get(descriptorType);
    if (ensure == null) return;

    log.info("Verifying labels in {}", descriptorType);
    for (String key : ensure) {
      boolean found = false;
      for (LabelDescriptor label : labels) {
        if (label.getKey().equals(key)) {
          log.info("Found hinted label '{}'", key);
          found = true;
          break;
        }
      }
      if (!found) {
        LabelDescriptor labelDescriptor = new LabelDescriptor();
        log.info("Injecting hinted label '{}'.", key);
        labelDescriptor.setKey(key);
        labelDescriptor.setValueType("STRING");
        labels.add(labelDescriptor);
      }
    }
  }

  /**
   * Get the labels to use for a given metric instance.
   *
   * @param descriptor
   *   The Stackdriver MetricDescriptor for the labels.
   *
   * @param tags
   *   The Spectator Measurement tags for the data.
   *
   * @return
   *   A map of label key, value bindings may include fewer or more
   *   tags than those provided depending on the extra tags and configured
   *   custom descriptor hints.
   */
  public Map<String, String> tagsToTimeSeriesLabels(
          MetricDescriptor descriptor, Iterable<Tag> tags) {
    HashMap<String, String> labels
        = new HashMap<String, String>(extraTimeSeriesLabels);

    for (Tag tag :tags) {
      addSanitizedLabel(descriptor, tag, labels);
    }
    return labels;
  }

  /**
   * Helper function providing a hook to fix or omit bad labels.
   */
  private void addSanitizedLabel(
        MetricDescriptor descriptor, Tag tag, Map<String, String> labels) {
    Set<String> redact = redactedHints.get(descriptor.getType());
    if (redact != null && redact.contains(tag.key())) {
      log.debug("Redacting use of label {}", tag.key());
      return;
    }
    labels.put(tag.key(), tag.value());
  }

  /**
   * Remember the analysis of meters to determine if they are timers or not.
   * This is because meterIsTimer is called continuously, not just on creation
   * of a new descriptor.
   */
  private Map<Id, Boolean> idToTimer = new HashMap<Id, Boolean>();

  /**
   * Determine if meter is a Timer or not.
   */
  public boolean meterIsTimer(Registry registry, Meter meter) {
    return idToTimer.computeIfAbsent(meter.id(), k -> {
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
}
