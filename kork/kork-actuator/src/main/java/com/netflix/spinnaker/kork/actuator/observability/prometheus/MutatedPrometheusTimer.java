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

package com.netflix.spinnaker.kork.actuator.observability.prometheus;

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.FixedBoundaryVictoriaMetricsHistogram;
import io.micrometer.core.instrument.distribution.Histogram;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.TimeWindowMax;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;
import io.micrometer.prometheus.HistogramFlavor;
import io.prometheus.client.exemplars.Exemplar;
import io.prometheus.client.exemplars.HistogramExemplarSampler;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class MutatedPrometheusTimer extends AbstractTimer {
  private static final CountAtBucket[] EMPTY_HISTOGRAM = new CountAtBucket[0];
  private final LongAdder count = new LongAdder();
  private final LongAdder totalTime = new LongAdder();
  private final TimeWindowMax max;
  private final HistogramFlavor histogramFlavor;
  @Nullable private final Histogram histogram;
  private boolean exemplarsEnabled = false;

  MutatedPrometheusTimer(
      Id id,
      Clock clock,
      DistributionStatisticConfig distributionStatisticConfig,
      PauseDetector pauseDetector,
      HistogramFlavor histogramFlavor,
      @Nullable HistogramExemplarSampler exemplarSampler) {
    super(
        id,
        clock,
        DistributionStatisticConfig.builder()
            .percentilesHistogram(false)
            .serviceLevelObjectives(new double[0])
            .build()
            .merge(distributionStatisticConfig),
        pauseDetector,
        TimeUnit.SECONDS,
        false);
    this.histogramFlavor = histogramFlavor;
    this.max = new TimeWindowMax(clock, distributionStatisticConfig);
    if (distributionStatisticConfig.isPublishingHistogram()) {
      switch (histogramFlavor) {
        case Prometheus:
          MutatedPrometheusHistogram prometheusHistogram =
              new MutatedPrometheusHistogram(clock, distributionStatisticConfig, exemplarSampler);
          this.histogram = prometheusHistogram;
          this.exemplarsEnabled = prometheusHistogram.isExemplarsEnabled();
          break;
        case VictoriaMetrics:
          this.histogram = new FixedBoundaryVictoriaMetricsHistogram();
          break;
        default:
          this.histogram = null;
      }
    } else {
      this.histogram = null;
    }
  }

  protected void recordNonNegative(long amount, TimeUnit unit) {
    this.count.increment();
    long nanoAmount = TimeUnit.NANOSECONDS.convert(amount, unit);
    this.totalTime.add(nanoAmount);
    this.max.record((double) nanoAmount, TimeUnit.NANOSECONDS);
    if (this.histogram != null) {
      this.histogram.recordLong(nanoAmount);
    }
  }

  @Nullable
  Exemplar[] exemplars() {
    return this.exemplarsEnabled ? ((MutatedPrometheusHistogram) this.histogram).exemplars() : null;
  }

  public long count() {
    return this.count.longValue();
  }

  public double totalTime(TimeUnit unit) {
    return TimeUtils.nanosToUnit(this.totalTime.doubleValue(), unit);
  }

  public double max(TimeUnit unit) {
    return this.max.poll(unit);
  }

  public HistogramFlavor histogramFlavor() {
    return this.histogramFlavor;
  }

  public CountAtBucket[] histogramCounts() {
    return this.histogram == null
        ? EMPTY_HISTOGRAM
        : this.histogram.takeSnapshot(0L, 0.0, 0.0).histogramCounts();
  }

  public HistogramSnapshot takeSnapshot() {
    HistogramSnapshot snapshot = super.takeSnapshot();
    if (this.histogram == null) {
      return snapshot;
    } else {
      long var10002 = snapshot.count();
      double var10003 = snapshot.total();
      double var10004 = snapshot.max();
      ValueAtPercentile[] var10005 = snapshot.percentileValues();
      CountAtBucket[] var10006 = this.histogramCounts();
      Objects.requireNonNull(snapshot);
      return new HistogramSnapshot(
          var10002, var10003, var10004, var10005, var10006, snapshot::outputSummary);
    }
  }
}
