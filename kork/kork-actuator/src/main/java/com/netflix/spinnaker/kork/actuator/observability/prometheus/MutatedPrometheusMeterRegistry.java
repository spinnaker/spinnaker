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

/*
 * This file uses the source code from https://github.com/micrometer-metrics/micrometer/pull/2653
 * imported in the 1.3.5 micrometer-core lib licensed under the Apache 2.0 license.
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.actuator.observability.prometheus;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.lang.Nullable;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusNamingConvention;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exemplars.ExemplarSampler;
import io.prometheus.client.exporter.common.TextFormat;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

/** @author Jon Schneider */
public class MutatedPrometheusMeterRegistry extends MeterRegistry {
  private final CollectorRegistry registry;
  private final ConcurrentMap<String, MutatedMicrometerCollector> collectorMap =
      new ConcurrentHashMap<>();
  private final PrometheusConfig prometheusConfig;

  @Nullable private final ExemplarSampler exemplarSampler;

  public MutatedPrometheusMeterRegistry(PrometheusConfig config) {
    this(config, new CollectorRegistry(), Clock.SYSTEM, null);
  }

  public MutatedPrometheusMeterRegistry(
      PrometheusConfig config,
      CollectorRegistry registry,
      Clock clock,
      @Nullable ExemplarSampler exemplarSampler) {
    super(clock);
    this.registry = registry;
    this.exemplarSampler = exemplarSampler;
    config().namingConvention(new PrometheusNamingConvention());
    config().onMeterRemoved(this::onMeterRemoved);
    this.prometheusConfig = config;
  }

  private static List<String> tagValues(Meter.Id id) {
    return stream(id.getTagsAsIterable().spliterator(), false).map(Tag::getValue).collect(toList());
  }

  /**
   * @return Content that should be included in the response body for an endpoint designated for
   *     Prometheus to scrape from.
   */
  public String scrape() {
    Writer writer = new StringWriter();
    try {
      scrape(writer);
    } catch (IOException e) {
      // This actually never happens since StringWriter::write() doesn't throw any IOException
      throw new RuntimeException(e);
    }
    return writer.toString();
  }

  /**
   * Scrape to the specified writer.
   *
   * @param writer Target that serves the content to be scraped by Prometheus.
   * @throws IOException if writing fails
   */
  public void scrape(Writer writer) throws IOException {
    TextFormat.write004(writer, registry.metricFamilySamples());
  }

  @Override
  public Counter newCounter(Meter.Id id) {
    // MutatedMicrometerCollector collector = collectorByName(id);
    MutatedPrometheusCounter counter = new MutatedPrometheusCounter(id);
    applyToCollector(
        id,
        (collector) -> {
          collector.add(
              id.getTags(),
              (conventionName, tags) ->
                  Stream.of(
                      new MutatedMicrometerCollector.Family(
                          Collector.Type.COUNTER,
                          conventionName,
                          new Collector.MetricFamilySamples.Sample(
                              conventionName, tags.keys, tags.values, counter.count()))));
        });
    return counter;
  }

  @Override
  public DistributionSummary newDistributionSummary(
      Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
    // MutatedMicrometerCollector collector = collectorByName(id);
    MutatedPrometheusDistributionSummary summary =
        new MutatedPrometheusDistributionSummary(
            id,
            clock,
            distributionStatisticConfig,
            scale,
            prometheusConfig.histogramFlavor(),
            exemplarSampler);
    List<String> tagValues = tagValues(id);
    applyToCollector(
        id,
        (collector) -> {
          collector.add(
              id.getTags(),
              (conventionName, tags) -> {
                Stream.Builder<Collector.MetricFamilySamples.Sample> samples = Stream.builder();

                final ValueAtPercentile[] percentileValues =
                    summary.takeSnapshot().percentileValues();
                final CountAtBucket[] histogramCounts = summary.histogramCounts();
                double count = summary.count();

                if (percentileValues.length > 0) {
                  List<String> quantileKeys = new LinkedList<>(tags.getKeys());
                  quantileKeys.add("quantile");

                  // satisfies https://prometheus.io/docs/concepts/metric_types/#summary
                  for (ValueAtPercentile v : percentileValues) {
                    List<String> quantileValues = new LinkedList<>(tags.getValues());
                    quantileValues.add(Collector.doubleToGoString(v.percentile()));
                    samples.add(
                        new Collector.MetricFamilySamples.Sample(
                            conventionName, quantileKeys, quantileValues, v.value()));
                  }
                }

                Collector.Type type = Collector.Type.SUMMARY;
                if (histogramCounts.length > 0) {
                  // Prometheus doesn't balk at a metric being BOTH a histogram and a summary
                  type = Collector.Type.HISTOGRAM;

                  List<String> histogramKeys = new LinkedList<>(tags.getKeys());
                  histogramKeys.add("le");

                  // satisfies https://prometheus.io/docs/concepts/metric_types/#histogram
                  for (CountAtBucket c : histogramCounts) {
                    final List<String> histogramValues = new LinkedList<>(tags.getValues());
                    histogramValues.add(Collector.doubleToGoString(c.bucket()));
                    samples.add(
                        new Collector.MetricFamilySamples.Sample(
                            conventionName + "_bucket", histogramKeys, histogramValues, c.count()));
                  }

                  // the +Inf bucket should always equal `count`
                  final List<String> histogramValues = new LinkedList<>(tags.getValues());
                  histogramValues.add("+Inf");
                  samples.add(
                      new Collector.MetricFamilySamples.Sample(
                          conventionName + "_bucket", histogramKeys, histogramValues, count));
                }

                samples.add(
                    new Collector.MetricFamilySamples.Sample(
                        conventionName + "_count", tags.getKeys(), tags.getValues(), count));

                samples.add(
                    new Collector.MetricFamilySamples.Sample(
                        conventionName + "_sum",
                        tags.getKeys(),
                        tags.getValues(),
                        summary.totalAmount()));

                return Stream.of(
                    new MutatedMicrometerCollector.Family(type, conventionName, samples.build()),
                    new MutatedMicrometerCollector.Family(
                        Collector.Type.GAUGE,
                        conventionName + "_max",
                        new Collector.MetricFamilySamples.Sample(
                            conventionName + "_max",
                            tags.getKeys(),
                            tags.getValues(),
                            summary.max())));
              });
        });

    return summary;
  }

  @Override
  protected Timer newTimer(
      Meter.Id id,
      DistributionStatisticConfig distributionStatisticConfig,
      PauseDetector pauseDetector) {
    // MutatedMicrometerCollector collector = collectorByName(id);
    MutatedPrometheusTimer timer =
        new MutatedPrometheusTimer(
            id,
            clock,
            distributionStatisticConfig,
            pauseDetector,
            prometheusConfig.histogramFlavor(),
            exemplarSampler);
    List<String> tagValues = tagValues(id);
    applyToCollector(
        id,
        (collector) -> {
          collector.add(
              id.getTags(),
              (conventionName, tags) -> {
                Stream.Builder<Collector.MetricFamilySamples.Sample> samples = Stream.builder();

                final ValueAtPercentile[] percentileValues =
                    timer.takeSnapshot().percentileValues();
                final CountAtBucket[] histogramCounts = timer.histogramCounts();
                double count = timer.count();

                if (percentileValues.length > 0) {
                  List<String> quantileKeys = new LinkedList<>(tags.getKeys());
                  quantileKeys.add("quantile");

                  // satisfies https://prometheus.io/docs/concepts/metric_types/#summary
                  for (ValueAtPercentile v : percentileValues) {
                    List<String> quantileValues = new LinkedList<>(tags.getValues());
                    quantileValues.add(Collector.doubleToGoString(v.percentile()));
                    samples.add(
                        new Collector.MetricFamilySamples.Sample(
                            conventionName,
                            quantileKeys,
                            quantileValues,
                            v.value(TimeUnit.SECONDS)));
                  }
                }

                Collector.Type type =
                    distributionStatisticConfig.isPublishingHistogram()
                        ? Collector.Type.HISTOGRAM
                        : Collector.Type.SUMMARY;
                if (histogramCounts.length > 0) {
                  // Prometheus doesn't balk at a metric being BOTH a histogram and a summary
                  type = Collector.Type.HISTOGRAM;

                  List<String> histogramKeys = new LinkedList<>(tags.getKeys());
                  histogramKeys.add("le");

                  // satisfies https://prometheus.io/docs/concepts/metric_types/#histogram
                  for (CountAtBucket c : histogramCounts) {
                    final List<String> histogramValues = new LinkedList<>(tags.getValues());
                    histogramValues.add(Collector.doubleToGoString(c.bucket(TimeUnit.SECONDS)));
                    samples.add(
                        new Collector.MetricFamilySamples.Sample(
                            conventionName + "_bucket", histogramKeys, histogramValues, c.count()));
                  }

                  // the +Inf bucket should always equal `count`
                  final List<String> histogramValues = new LinkedList<>(tags.getValues());
                  histogramValues.add("+Inf");
                  samples.add(
                      new Collector.MetricFamilySamples.Sample(
                          conventionName + "_bucket", histogramKeys, histogramValues, count));
                }

                samples.add(
                    new Collector.MetricFamilySamples.Sample(
                        conventionName + "_count", tags.getKeys(), tags.getValues(), count));

                samples.add(
                    new Collector.MetricFamilySamples.Sample(
                        conventionName + "_sum",
                        tags.getKeys(),
                        tags.getValues(),
                        timer.totalTime(TimeUnit.SECONDS)));

                return Stream.of(
                    new MutatedMicrometerCollector.Family(type, conventionName, samples.build()),
                    new MutatedMicrometerCollector.Family(
                        Collector.Type.GAUGE,
                        conventionName + "_max",
                        Stream.of(
                            new Collector.MetricFamilySamples.Sample(
                                conventionName + "_max",
                                tags.getKeys(),
                                tags.getValues(),
                                timer.max(getBaseTimeUnit())))));
              });
        });
    return timer;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
    // MutatedMicrometerCollector collector = collectorByName(id);
    Gauge gauge = new DefaultGauge(id, obj, valueFunction);
    List<String> tagValues = tagValues(id);
    applyToCollector(
        id,
        (collector) -> {
          collector.add(
              id.getTags(),
              (conventionName, tags) ->
                  Stream.of(
                      new MutatedMicrometerCollector.Family(
                          Collector.Type.GAUGE,
                          conventionName,
                          new Collector.MetricFamilySamples.Sample(
                              conventionName, tags.getKeys(), tags.getValues(), gauge.value()))));
        });
    return gauge;
  }

  @Override
  protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
    // MutatedMicrometerCollector collector = collectorByName(id);
    LongTaskTimer ltt = new DefaultLongTaskTimer(id, clock);
    List<String> tagValues = tagValues(id);
    applyToCollector(
        id,
        (collector) -> {
          collector.add(
              id.getTags(),
              (conventionName, tags) ->
                  Stream.of(
                      new MutatedMicrometerCollector.Family(
                          Collector.Type.UNKNOWN,
                          conventionName,
                          new Collector.MetricFamilySamples.Sample(
                              conventionName + "_active_count",
                              tags.getKeys(),
                              tags.getValues(),
                              ltt.activeTasks()),
                          new Collector.MetricFamilySamples.Sample(
                              conventionName + "_duration_sum",
                              tags.getKeys(),
                              tags.getValues(),
                              ltt.duration(TimeUnit.SECONDS)))));
        });
    return ltt;
  }

  @Override
  protected <T> FunctionTimer newFunctionTimer(
      Meter.Id id,
      T obj,
      ToLongFunction<T> countFunction,
      ToDoubleFunction<T> totalTimeFunction,
      TimeUnit totalTimeFunctionUnit) {
    // MutatedMicrometerCollector collector = collectorByName(id);
    FunctionTimer ft =
        new CumulativeFunctionTimer<>(
            id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
    List<String> tagValues = tagValues(id);
    applyToCollector(
        id,
        (collector) -> {
          collector.add(
              id.getTags(),
              (conventionName, tags) ->
                  Stream.of(
                      new MutatedMicrometerCollector.Family(
                          Collector.Type.SUMMARY,
                          conventionName,
                          new Collector.MetricFamilySamples.Sample(
                              conventionName + "_count",
                              tags.getKeys(),
                              tags.getValues(),
                              ft.count()),
                          new Collector.MetricFamilySamples.Sample(
                              conventionName + "_sum",
                              tags.getKeys(),
                              tags.getValues(),
                              ft.totalTime(TimeUnit.SECONDS)))));
        });
    return ft;
  }

  @Override
  protected <T> FunctionCounter newFunctionCounter(
      Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
    // MutatedMicrometerCollector collector = collectorByName(id);
    FunctionCounter fc = new CumulativeFunctionCounter<>(id, obj, countFunction);
    List<String> tagValues = tagValues(id);
    applyToCollector(
        id,
        (collector) -> {
          collector.add(
              id.getTags(),
              (conventionName, tags) ->
                  Stream.of(
                      new MutatedMicrometerCollector.Family(
                          Collector.Type.COUNTER,
                          conventionName,
                          new Collector.MetricFamilySamples.Sample(
                              conventionName, tags.getKeys(), tags.getValues(), fc.count()))));
        });
    return fc;
  }

  @Override
  protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
    Collector.Type promType = Collector.Type.UNKNOWN;
    switch (type) {
      case COUNTER:
        promType = Collector.Type.COUNTER;
        break;
      case GAUGE:
        promType = Collector.Type.GAUGE;
        break;
      case DISTRIBUTION_SUMMARY:
      case TIMER:
        promType = Collector.Type.SUMMARY;
        break;
    }

    // MutatedMicrometerCollector collector = collectorByName(id);
    List<String> tagValues = tagValues(id);

    final Collector.Type finalPromType = promType;
    applyToCollector(
        id,
        (collector) -> {
          collector.add(
              id.getTags(),
              (conventionName, tags) -> {
                List<String> statKeys = new LinkedList<>(tags.getKeys());
                statKeys.add("statistic");

                return Stream.of(
                    new MutatedMicrometerCollector.Family(
                        finalPromType,
                        conventionName,
                        stream(measurements.spliterator(), false)
                            .map(
                                m -> {
                                  List<String> statValues = new LinkedList<>(tags.getValues());
                                  statValues.add(m.getStatistic().toString());

                                  String name = conventionName;
                                  switch (m.getStatistic()) {
                                    case TOTAL:
                                    case TOTAL_TIME:
                                      name += "_sum";
                                      break;
                                    case MAX:
                                      name += "_max";
                                      break;
                                    case ACTIVE_TASKS:
                                      name += "_active_count";
                                      break;
                                    case DURATION:
                                      name += "_duration_sum";
                                      break;
                                  }

                                  return new Collector.MetricFamilySamples.Sample(
                                      name, statKeys, statValues, m.getValue());
                                })));
              });
        });
    return new DefaultMeter(id, type, measurements);
  }

  @Override
  protected TimeUnit getBaseTimeUnit() {
    return TimeUnit.SECONDS;
  }

  /** @return The underlying Prometheus {@link CollectorRegistry}. */
  public CollectorRegistry getPrometheusRegistry() {
    return registry;
  }

  private void onMeterRemoved(Meter meter) {
    MutatedMicrometerCollector collector = collectorMap.get(getConventionName(meter.getId()));
    if (collector != null) {
      collector.remove(meter.getId().getTags());
      if (collector.isEmpty()) {
        collectorMap.remove(getConventionName(meter.getId()));
        getPrometheusRegistry().unregister(collector);
      }
    }
  }

  private void applyToCollector(Meter.Id id, Consumer<MutatedMicrometerCollector> consumer) {
    collectorMap.compute(
        getConventionName(id),
        (name, existingCollector) -> {
          MutatedMicrometerCollector collector = existingCollector;
          if (collector == null) {
            collector =
                new MutatedMicrometerCollector(id, config().namingConvention(), prometheusConfig);
            collector.register(registry);
          }

          consumer.accept(collector);
          return collector;
        });
  }

  @Override
  protected DistributionStatisticConfig defaultHistogramConfig() {
    return DistributionStatisticConfig.builder()
        .expiry(prometheusConfig.step())
        .build()
        .merge(DistributionStatisticConfig.DEFAULT);
  }
}
